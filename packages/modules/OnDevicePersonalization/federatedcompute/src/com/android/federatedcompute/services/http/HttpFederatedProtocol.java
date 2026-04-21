/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.federatedcompute.services.http;

import static com.android.federatedcompute.services.common.Constants.TRACE_HTTP_ISSUE_CHECKIN;
import static com.android.federatedcompute.services.common.Constants.TRACE_HTTP_REPORT_RESULT;
import static com.android.federatedcompute.services.common.FederatedComputeExecutors.getBackgroundExecutor;
import static com.android.federatedcompute.services.common.FederatedComputeExecutors.getLightweightExecutor;
import static com.android.federatedcompute.services.common.FileUtils.createTempFile;
import static com.android.federatedcompute.services.common.FileUtils.readFileAsByteArray;
import static com.android.federatedcompute.services.common.FileUtils.writeToFile;
import static com.android.federatedcompute.services.http.HttpClientUtil.ACCEPT_ENCODING_HDR;
import static com.android.federatedcompute.services.http.HttpClientUtil.GZIP_ENCODING_HDR;
import static com.android.federatedcompute.services.http.HttpClientUtil.HTTP_OK_STATUS;
import static com.android.federatedcompute.services.http.HttpClientUtil.ODP_IDEMPOTENCY_KEY;
import static com.android.federatedcompute.services.http.HttpClientUtil.compressWithGzip;
import static com.android.federatedcompute.services.http.HttpClientUtil.uncompressWithGzip;

import android.os.Trace;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.http.HttpClientUtil.HttpMethod;
import com.android.federatedcompute.services.training.util.ComputationResult;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.internal.federatedcompute.v1.ClientVersion;
import com.google.internal.federatedcompute.v1.RejectionInfo;
import com.google.internal.federatedcompute.v1.Resource;
import com.google.internal.federatedcompute.v1.ResourceCapabilities;
import com.google.internal.federatedcompute.v1.ResourceCompressionFormat;
import com.google.ondevicepersonalization.federatedcompute.proto.CreateTaskAssignmentRequest;
import com.google.ondevicepersonalization.federatedcompute.proto.CreateTaskAssignmentResponse;
import com.google.ondevicepersonalization.federatedcompute.proto.ReportResultRequest;
import com.google.ondevicepersonalization.federatedcompute.proto.ReportResultRequest.Result;
import com.google.ondevicepersonalization.federatedcompute.proto.ReportResultResponse;
import com.google.ondevicepersonalization.federatedcompute.proto.TaskAssignment;
import com.google.ondevicepersonalization.federatedcompute.proto.UploadInstruction;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.Callable;

/** Implements a single session of HTTP-based federated compute protocol. */
public final class HttpFederatedProtocol {
    public static final String TAG = HttpFederatedProtocol.class.getSimpleName();
    private final String mClientVersion;
    private final String mPopulationName;
    private final HttpClient mHttpClient;
    private String mTaskId;
    private String mAggregationId;
    private String mAssignmentId;
    private final ProtocolRequestCreator mTaskAssignmentRequestCreator;

    @VisibleForTesting
    HttpFederatedProtocol(
            String entryUri, String clientVersion, String populationName, HttpClient httpClient) {
        this.mClientVersion = clientVersion;
        this.mPopulationName = populationName;
        this.mHttpClient = httpClient;
        this.mTaskAssignmentRequestCreator = new ProtocolRequestCreator(entryUri, new HashMap<>());
    }

    /** Creates a HttpFederatedProtocol object. */
    public static HttpFederatedProtocol create(
            String entryUri, String clientVersion, String populationName) {
        return new HttpFederatedProtocol(entryUri, clientVersion, populationName, new HttpClient());
    }

    /** Helper function to perform check in and download federated task from remote servers. */
    public ListenableFuture<CheckinResult> issueCheckin() {
        Trace.beginAsyncSection(TRACE_HTTP_ISSUE_CHECKIN, 0);
        return FluentFuture.from(createTaskAssignment())
                .transformAsync(
                        federatedComputeHttpResponse -> {
                            validateHttpResponseStatus(
                                    "Start task assignment", federatedComputeHttpResponse);
                            CreateTaskAssignmentResponse taskAssignmentResponse;
                            try {
                                taskAssignmentResponse =
                                        CreateTaskAssignmentResponse.parseFrom(
                                                federatedComputeHttpResponse.getPayload());
                            } catch (InvalidProtocolBufferException e) {
                                throw new IllegalStateException(
                                        "Could not parse StartTaskAssignmentResponse proto", e);
                            }
                            if (taskAssignmentResponse.hasRejectionInfo()) {
                                return Futures.immediateFuture(
                                        new CheckinResult(
                                                taskAssignmentResponse.getRejectionInfo()));
                            }
                            TaskAssignment taskAssignment =
                                    getTaskAssignment(taskAssignmentResponse);
                            ListenableFuture<FederatedComputeHttpResponse> planDataResponseFuture =
                                    fetchTaskResource(taskAssignment.getPlan());
                            ListenableFuture<FederatedComputeHttpResponse>
                                    checkpointDataResponseFuture =
                                            fetchTaskResource(taskAssignment.getInitCheckpoint());
                            return Futures.whenAllSucceed(
                                            planDataResponseFuture, checkpointDataResponseFuture)
                                    .call(
                                            new Callable<CheckinResult>() {
                                                @Override
                                                public CheckinResult call() throws Exception {
                                                    return getCheckinResult(
                                                            planDataResponseFuture,
                                                            checkpointDataResponseFuture,
                                                            taskAssignment);
                                                }
                                            },
                                            getBackgroundExecutor());
                        },
                        getBackgroundExecutor());
    }

    /** Helper functions to reporting result and upload result. */
    public FluentFuture<RejectionInfo> reportResult(ComputationResult computationResult) {
        Trace.beginAsyncSection(TRACE_HTTP_REPORT_RESULT, 0);
        if (computationResult != null && computationResult.isResultSuccess()) {
            return FluentFuture.from(performReportResult(computationResult))
                    .transformAsync(
                            reportResp -> {
                                ReportResultResponse reportResultResponse =
                                        getReportResultResponse(reportResp);
                                if (reportResultResponse.hasRejectionInfo()) {
                                    return Futures.immediateFuture(
                                            reportResultResponse.getRejectionInfo());
                                }
                                return FluentFuture.from(
                                                processReportResultResponseAndUploadResult(
                                                        reportResultResponse, computationResult))
                                        .transform(
                                                resp -> {
                                                    validateHttpResponseStatus(
                                                            "Upload result", resp);
                                                    Trace.endAsyncSection(
                                                            TRACE_HTTP_REPORT_RESULT, 0);
                                                    return null;
                                                },
                                                getLightweightExecutor());
                            },
                            getBackgroundExecutor());
        } else {
            return FluentFuture.from(performReportResult(computationResult))
                    .transform(
                            resp -> {
                                validateHttpResponseStatus("Report failure result", resp);
                                return null;
                            },
                            getLightweightExecutor());
        }
    }

    private ListenableFuture<FederatedComputeHttpResponse> createTaskAssignment() {
        CreateTaskAssignmentRequest request =
                CreateTaskAssignmentRequest.newBuilder()
                        .setClientVersion(ClientVersion.newBuilder().setVersionCode(mClientVersion))
                        .setResourceCapabilities(
                                ResourceCapabilities.newBuilder()
                                        .addSupportedCompressionFormats(
                                                ResourceCompressionFormat
                                                        .RESOURCE_COMPRESSION_FORMAT_GZIP))
                        .build();

        String taskAssignmentUriSuffix =
                String.format(
                        "/taskassignment/v1/population/%1$s:create-task-assignment",
                        mPopulationName);
        FederatedComputeHttpRequest httpRequest =
                mTaskAssignmentRequestCreator.createProtoRequest(
                        taskAssignmentUriSuffix,
                        HttpMethod.POST,
                        new HashMap<>(),
                        request.toByteArray(),
                        /* isProtobufEncoded= */ true);
        httpRequest
                .getExtraHeaders()
                .put(ODP_IDEMPOTENCY_KEY, System.currentTimeMillis() + " - " + UUID.randomUUID());
        return mHttpClient.performRequestAsyncWithRetry(httpRequest);
    }

    private TaskAssignment getTaskAssignment(CreateTaskAssignmentResponse taskAssignmentResponse) {
        if (taskAssignmentResponse.hasRejectionInfo()) {
            throw new IllegalStateException("Device rejected by server.");
        }
        if (!taskAssignmentResponse.hasTaskAssignment()) {
            throw new IllegalStateException(
                    "Could not find both task assignment and rejection info.");
        }
        validateTaskAssignment(taskAssignmentResponse.getTaskAssignment());
        TaskAssignment taskAssignment = taskAssignmentResponse.getTaskAssignment();
        LogUtil.d(
                TAG,
                "Receive CreateTaskAssignmentResponse: task name %s assignment id %s",
                taskAssignment.getTaskName(),
                taskAssignment.getAssignmentId());
        return taskAssignment;
    }

    private void validateTaskAssignment(TaskAssignment taskAssignment) {
        Preconditions.checkArgument(
                taskAssignment.getPopulationName().equals(mPopulationName),
                "Population name should match");
        // These fields are required to construct ReportResultRequest.
        Preconditions.checkArgument(
                !taskAssignment.getTaskId().isEmpty(), "Task id should not be empty");
        Preconditions.checkArgument(
                !taskAssignment.getAggregationId().isEmpty(), "Aggregation id should not be empty");
        Preconditions.checkArgument(
                !taskAssignment.getAssignmentId().isEmpty(), "Assignment id should not be empty");
        this.mTaskId = taskAssignment.getTaskId();
        this.mAggregationId = taskAssignment.getAggregationId();
        this.mAssignmentId = taskAssignment.getAssignmentId();
    }

    private CheckinResult getCheckinResult(
            ListenableFuture<FederatedComputeHttpResponse> planDataResponseFuture,
            ListenableFuture<FederatedComputeHttpResponse> checkpointDataResponseFuture,
            TaskAssignment taskAssignment)
            throws Exception {

        FederatedComputeHttpResponse planDataResponse = Futures.getDone(planDataResponseFuture);
        FederatedComputeHttpResponse checkpointDataResponse =
                Futures.getDone(checkpointDataResponseFuture);
        validateHttpResponseStatus("Fetch plan", planDataResponse);
        validateHttpResponseStatus("Fetch checkpoint", checkpointDataResponse);

        // Process download ClientOnlyPlan.
        byte[] planData = planDataResponse.getPayload();
        if (taskAssignment.getPlan().getCompressionFormat()
                        == ResourceCompressionFormat.RESOURCE_COMPRESSION_FORMAT_GZIP
                || planDataResponse.isResponseCompressed()) {
            planData = uncompressWithGzip(planData);
        }
        ClientOnlyPlan clientOnlyPlan;
        try {
            clientOnlyPlan = ClientOnlyPlan.parseFrom(planData);
        } catch (InvalidProtocolBufferException e) {
            LogUtil.e(TAG, e, "Could not parse ClientOnlyPlan proto");
            throw new IllegalStateException("Could not parse ClientOnlyPlan proto", e);
        }

        // Process download checkpoint resource.
        String inputCheckpointFile = createTempFile("input", ".ckp");
        byte[] checkpointData = checkpointDataResponse.getPayload();
        if (taskAssignment.getInitCheckpoint().getCompressionFormat()
                        == ResourceCompressionFormat.RESOURCE_COMPRESSION_FORMAT_GZIP
                || checkpointDataResponse.isResponseCompressed()) {
            checkpointData = uncompressWithGzip(checkpointData);
        }
        writeToFile(inputCheckpointFile, checkpointData);
        Trace.endAsyncSection(TRACE_HTTP_ISSUE_CHECKIN, 0);
        return new CheckinResult(inputCheckpointFile, clientOnlyPlan, taskAssignment);
    }

    private ListenableFuture<FederatedComputeHttpResponse> performReportResult(
            ComputationResult computationResult) {
        Result result =
                (computationResult != null && computationResult.isResultSuccess())
                        ? Result.COMPLETED
                        : Result.FAILED;
        ReportResultRequest startDataUploadRequest =
                ReportResultRequest.newBuilder().setResult(result).build();
        String startDataUploadUri =
                String.format(
                        "/taskassignment/v1/population/%1$s/task/%2$s/aggregation"
                                + "/%3$s/task-assignment/%4$s:report-result",
                        mPopulationName, mTaskId, mAggregationId, mAssignmentId);
        LogUtil.d(
                TAG,
                "send ReportResultRequest: population name %s, task name %s,"
                        + " assignment id %s, result %s",
                mPopulationName,
                mTaskId,
                mAssignmentId,
                result.toString());
        FederatedComputeHttpRequest httpRequest =
                mTaskAssignmentRequestCreator.createProtoRequest(
                        startDataUploadUri,
                        HttpMethod.PUT,
                        startDataUploadRequest.toByteArray(),
                        /* isProtobufEncoded= */ true);
        return mHttpClient.performRequestAsyncWithRetry(httpRequest);
    }

    private ListenableFuture<FederatedComputeHttpResponse>
            processReportResultResponseAndUploadResult(
            ReportResultResponse reportResultResponse,
                    ComputationResult computationResult) {
        try {
            Preconditions.checkArgument(
                    !computationResult.getOutputCheckpointFile().isEmpty(),
                    "Output checkpoint file should not be empty");
            UploadInstruction uploadInstruction = reportResultResponse.getUploadInstruction();
            Preconditions.checkArgument(
                    !uploadInstruction.getUploadLocation().isEmpty(),
                    "UploadInstruction.upload_location must not be empty");
            byte[] outputBytes = readFileAsByteArray(computationResult.getOutputCheckpointFile());
            if (uploadInstruction.getCompressionFormat()
                    == ResourceCompressionFormat.RESOURCE_COMPRESSION_FORMAT_GZIP) {
                outputBytes = compressWithGzip(outputBytes);
            }
            HashMap<String, String> requestHeader = new HashMap<>();
            uploadInstruction
                    .getExtraRequestHeadersMap()
                    .forEach(
                            (key, value) -> {
                                requestHeader.put(key, value);
                            });
            LogUtil.d(
                    TAG,
                    "Start upload training result: population name %s, task name %s,"
                            + " assignment id %s",
                    mPopulationName,
                    mTaskId,
                    mAssignmentId);
            FederatedComputeHttpRequest httpUploadRequest =
                    FederatedComputeHttpRequest.create(
                            uploadInstruction.getUploadLocation(),
                            HttpMethod.PUT,
                            requestHeader,
                            outputBytes);
            return mHttpClient.performRequestAsyncWithRetry(httpUploadRequest);
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private ReportResultResponse getReportResultResponse(FederatedComputeHttpResponse httpResponse)
            throws InvalidProtocolBufferException {
        validateHttpResponseStatus("ReportResult", httpResponse);
        ReportResultResponse reportResultResponse =
                ReportResultResponse.parseFrom(httpResponse.getPayload());
        return reportResultResponse;
    }

    private void validateHttpResponseStatus(
            String stage, FederatedComputeHttpResponse httpResponse) {
        if (!HTTP_OK_STATUS.contains(httpResponse.getStatusCode())) {
            throw new IllegalStateException(stage + " failed: " + httpResponse.getStatusCode());
        } else {
            LogUtil.i(TAG, stage + " success.");
        }
    }

    private ListenableFuture<FederatedComputeHttpResponse> fetchTaskResource(Resource resource) {
        switch (resource.getResourceCase()) {
            case URI:
                Preconditions.checkArgument(
                        !resource.getUri().isEmpty(), "Resource.uri must be non-empty when set");
                HashMap<String, String> headerList = new HashMap<>();
                if (resource.getCompressionFormat()
                        == ResourceCompressionFormat.RESOURCE_COMPRESSION_FORMAT_GZIP) {
                    // Set this header to disable decompressive transcoding when download from
                    // Google Cloud Storage.
                    // https://cloud.google.com/storage/docs/transcoding#decompressive_transcoding
                    headerList.put(ACCEPT_ENCODING_HDR, GZIP_ENCODING_HDR);
                }
                LogUtil.d(TAG, "start fetch task resources");
                FederatedComputeHttpRequest httpRequest =
                        FederatedComputeHttpRequest.create(
                                resource.getUri(),
                                HttpMethod.GET,
                                headerList,
                                HttpClientUtil.EMPTY_BODY);
                return mHttpClient.performRequestAsyncWithRetry(httpRequest);
            case INLINE_RESOURCE:
                return Futures.immediateFailedFuture(
                        new UnsupportedOperationException("Inline resource is not supported yet."));
            default:
                return Futures.immediateFailedFuture(
                        new UnsupportedOperationException("Unknown Resource type"));
        }
    }
}
