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

package com.android.federatedcompute.services.training;

import static com.android.federatedcompute.services.common.Constants.CLIENT_ONLY_PLAN_FILE_NAME;
import static com.android.federatedcompute.services.common.Constants.ISOLATED_TRAINING_SERVICE_NAME;
import static com.android.federatedcompute.services.common.Constants.TRACE_WORKER_RUN_FL_COMPUTATION;
import static com.android.federatedcompute.services.common.Constants.TRACE_WORKER_START_TRAINING_RUN;
import static com.android.federatedcompute.services.common.FederatedComputeExecutors.getBackgroundExecutor;
import static com.android.federatedcompute.services.common.FederatedComputeExecutors.getLightweightExecutor;
import static com.android.federatedcompute.services.common.FileUtils.createTempFile;
import static com.android.federatedcompute.services.common.FileUtils.createTempFileDescriptor;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.federatedcompute.aidl.IExampleStoreCallback;
import android.federatedcompute.aidl.IExampleStoreIterator;
import android.federatedcompute.aidl.IExampleStoreService;
import android.federatedcompute.common.ClientConstants;
import android.federatedcompute.common.ExampleConsumption;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Trace;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.Constants;
import com.android.federatedcompute.services.common.FileUtils;
import com.android.federatedcompute.services.data.FederatedTrainingTask;
import com.android.federatedcompute.services.data.fbs.TrainingConstraints;
import com.android.federatedcompute.services.examplestore.ExampleConsumptionRecorder;
import com.android.federatedcompute.services.http.CheckinResult;
import com.android.federatedcompute.services.http.HttpFederatedProtocol;
import com.android.federatedcompute.services.scheduling.FederatedComputeJobManager;
import com.android.federatedcompute.services.training.aidl.IIsolatedTrainingService;
import com.android.federatedcompute.services.training.aidl.ITrainingResultCallback;
import com.android.federatedcompute.services.training.util.ComputationResult;
import com.android.federatedcompute.services.training.util.ListenableSupplier;
import com.android.federatedcompute.services.training.util.TrainingConditionsChecker;
import com.android.federatedcompute.services.training.util.TrainingConditionsChecker.Condition;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.intelligence.fcp.client.FLRunnerResult.ContributionResult;
import com.google.intelligence.fcp.client.RetryInfo;
import com.google.intelligence.fcp.client.engine.TaskRetry;
import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.internal.federated.plan.ExampleSelector;
import com.google.internal.federatedcompute.v1.RejectionInfo;
import com.google.internal.federatedcompute.v1.RetryWindow;
import com.google.protobuf.Duration;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** The worker to execute federated computation jobs. */
public class FederatedComputeWorker {
    private static final String TAG = FederatedComputeWorker.class.getSimpleName();
    private static volatile FederatedComputeWorker sWorker;
    private final Object mLock = new Object();
    private final AtomicBoolean mInterruptFlag = new AtomicBoolean(false);
    private final ListenableSupplier<Boolean> mInterruptSupplier =
            new ListenableSupplier<>(mInterruptFlag::get);
    private final Context mContext;
    @Nullable private final FederatedComputeJobManager mJobManager;
    @Nullable private final TrainingConditionsChecker mTrainingConditionsChecker;
    private final ComputationRunner mComputationRunner;
    private final ResultCallbackHelper mResultCallbackHelper;
    @NonNull private final Injector mInjector;

    @GuardedBy("mLock")
    @Nullable
    private TrainingRun mActiveRun = null;

    private HttpFederatedProtocol mHttpFederatedProtocol;
    private AbstractServiceBinder<IExampleStoreService> mExampleStoreServiceBinder;
    private AbstractServiceBinder<IIsolatedTrainingService> mIsolatedTrainingServiceBinder;

    @VisibleForTesting
    public FederatedComputeWorker(
            Context context,
            FederatedComputeJobManager jobManager,
            TrainingConditionsChecker trainingConditionsChecker,
            ComputationRunner computationRunner,
            ResultCallbackHelper resultCallbackHelper,
            Injector injector) {
        this.mContext = context.getApplicationContext();
        this.mJobManager = jobManager;
        this.mTrainingConditionsChecker = trainingConditionsChecker;
        this.mComputationRunner = computationRunner;
        this.mInjector = injector;
        this.mResultCallbackHelper = resultCallbackHelper;
    }

    /** Gets an instance of {@link FederatedComputeWorker}. */
    @NonNull
    public static FederatedComputeWorker getInstance(Context context) {
        if (sWorker == null) {
            synchronized (FederatedComputeWorker.class) {
                if (sWorker == null) {
                    sWorker =
                            new FederatedComputeWorker(
                                    context,
                                    FederatedComputeJobManager.getInstance(context),
                                    TrainingConditionsChecker.getInstance(context),
                                    new ComputationRunner(),
                                    new ResultCallbackHelper(context),
                                    new Injector());
                }
            }
        }
        return sWorker;
    }

    /** Starts a training run with the given job Id. */
    public ListenableFuture<FLRunnerResult> startTrainingRun(int jobId) {
        LogUtil.d(TAG, "startTrainingRun() %d", jobId);
        return FluentFuture.from(
                        mInjector
                                .getBgExecutor()
                                .submit(
                                        () -> {
                                            return getTrainableTask(jobId);
                                        }))
                .transformAsync(
                        task -> {
                            if (task == null) {
                                return Futures.immediateFuture(null);
                            }
                            return startTrainingRun(jobId, task);
                        },
                        mInjector.getBgExecutor());
    }

    private ListenableFuture<FLRunnerResult> startTrainingRun(
            int jobId, FederatedTrainingTask trainingTask) {
        synchronized (mLock) {
            // Only allows one concurrent job running.
            Trace.beginAsyncSection(TRACE_WORKER_START_TRAINING_RUN, jobId);
            TrainingRun run = new TrainingRun(jobId, trainingTask);
            mActiveRun = run;
            ListenableFuture<FLRunnerResult> runCompletedFuture = doTraining(run);
            var unused =
                    Futures.whenAllComplete(runCompletedFuture)
                            .call(
                                    () -> {
                                        unBindServicesIfNecessary(run);
                                        Trace.endAsyncSection(
                                                TRACE_WORKER_START_TRAINING_RUN, jobId);
                                        return null;
                                    },
                                    mInjector.getBgExecutor());
            run.mFuture = runCompletedFuture;
            return runCompletedFuture;
        }
    }

    @Nullable
    private FederatedTrainingTask getTrainableTask(int jobId) {
        FederatedTrainingTask trainingTask = mJobManager.onTrainingStarted(jobId);
        if (trainingTask == null) {
            LogUtil.i(TAG, "Could not find task to run for job ID %s", jobId);
            return null;
        }
        if (!checkTrainingConditions(trainingTask.getTrainingConstraints())) {
            mJobManager.onTrainingCompleted(
                    jobId,
                    trainingTask.populationName(),
                    trainingTask.getTrainingIntervalOptions(),
                    /* taskRetry= */ null,
                    ContributionResult.FAIL);
            LogUtil.i(TAG, "Training conditions not satisfied (before bindService)!");
            return null;
        }
        synchronized (mLock) {
            // Only allows one concurrent job running.
            if (mActiveRun != null) {
                LogUtil.i(
                        TAG,
                        "Delaying %d/%s another run is already active!",
                        jobId,
                        trainingTask.populationName());
                mJobManager.onTrainingCompleted(
                        jobId,
                        trainingTask.populationName(),
                        trainingTask.getTrainingIntervalOptions(),
                        /* taskRetry= */ null,
                        ContributionResult.FAIL);
                return null;
            }
            return trainingTask;
        }
    }

    private ListenableFuture<FLRunnerResult> doTraining(TrainingRun run) {
        try {
            // 1. Communicate with remote federated compute server to start task assignment and
            // download client plan and initial model checkpoint. Note: use bLocking executors for
            // http requests.
            mHttpFederatedProtocol =
                    getHttpFederatedProtocol(run.mTask.serverAddress(), run.mTask.populationName());
            ListenableFuture<CheckinResult> checkinResultFuture =
                    mHttpFederatedProtocol.issueCheckin();

            return FluentFuture.from(checkinResultFuture)
                    .transformAsync(
                            checkinResult -> processCheckinAndDoFlTraining(run, checkinResult),
                            mInjector.getBgExecutor());
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    @androidx.annotation.NonNull
    private ListenableFuture<FLRunnerResult> processCheckinAndDoFlTraining(
            TrainingRun run, CheckinResult checkinResult) {
        // Stop processing if have rejection Info
        if (checkinResult.getRejectionInfo() != null) {
            LogUtil.d(TAG, "job %d was rejected during check in, reason %s",
                    run.mTask.jobId(), checkinResult.getRejectionInfo().getReason());
            mJobManager.onTrainingCompleted(
                    run.mTask.jobId(),
                    run.mTask.populationName(),
                    run.mTask.getTrainingIntervalOptions(),
                    buildTaskRetry(checkinResult.getRejectionInfo()),
                    ContributionResult.FAIL);
            return Futures.immediateFuture(null);
        }
        // 2. Bind to client app implemented ExampleStoreService based on ExampleSelector.
        // Set active run's task name.
        String taskName = checkinResult.getTaskAssignment().getTaskName();
        Preconditions.checkArgument(!taskName.isEmpty(), "Task name should not be empty");
        synchronized (mLock) {
            mActiveRun.mTaskName = taskName;
        }
        ListenableFuture<IExampleStoreIterator> iteratorFuture =
                getExampleStoreIterator(
                        run,
                        run.mTask.appPackageName(),
                        run.mTaskName,
                        getExampleSelector(checkinResult));
        // report failure to server if getting iterator failed with any exception.
        FutureCallback<Object> serverFailureReportCallback = getServerFailureReportCallback();
        Futures.addCallback(
                iteratorFuture, serverFailureReportCallback, getLightweightExecutor());

        // 3. Run federated learning or federated analytic depends on task type. Federated
        // learning job will start a new isolated process to run TFLite training.
        FluentFuture<ComputationResult> computationResultFuture =
                FluentFuture.from(iteratorFuture)
                        .transformAsync(
                                iterator -> runFederatedComputation(checkinResult, run, iterator),
                                mInjector.getBgExecutor());
        // report failure to server if computation failed with any exception.
        computationResultFuture.addCallback(
                serverFailureReportCallback, getLightweightExecutor());

        // 4. Report computation result to federated compute server.
        ListenableFuture<RejectionInfo> reportToServerFuture =
                computationResultFuture.transformAsync(
                        result -> mHttpFederatedProtocol.reportResult(result),
                        getLightweightExecutor());
        return Futures.whenAllSucceed(reportToServerFuture, computationResultFuture)
                .call(
                        () -> {
                            ComputationResult computationResult =
                                    Futures.getDone(computationResultFuture);
                            RejectionInfo reportToServer = Futures.getDone(reportToServerFuture);
                            // report to Server will hold null in case of success, or rejection info
                            // in case server answered with rejection
                            if (reportToServer != null) {
                                ComputationResult failedReportComputationResult =
                                        new ComputationResult(
                                                null,
                                                FLRunnerResult.newBuilder()
                                                        .mergeFrom(
                                                                computationResult
                                                                        .getFlRunnerResult())
                                                        .setContributionResult(
                                                                ContributionResult.FAIL)
                                                        .build(),
                                                null);
                                var unused =
                                        mResultCallbackHelper.callHandleResult(
                                                run.mTaskName,
                                                run.mTask,
                                                failedReportComputationResult);
                                mJobManager.onTrainingCompleted(
                                        run.mTask.jobId(),
                                        run.mTask.populationName(),
                                        run.mTask.getTrainingIntervalOptions(),
                                        buildTaskRetry(reportToServer),
                                        ContributionResult.FAIL);
                                return null;
                            }
                            // 5. Publish computation result and consumed
                            // examples to client implemented
                            // ResultHandlingService.
                            var unused =
                                    mResultCallbackHelper.callHandleResult(
                                            run.mTaskName, run.mTask, computationResult);
                            return computationResult.getFlRunnerResult();
                        },
                        mInjector.getBgExecutor());
    }

    @androidx.annotation.NonNull
    private FutureCallback<Object> getServerFailureReportCallback() {
        return new FutureCallback<Object>() {
            volatile int mNumberOfInvocations = 0;

            @Override
            public void onSuccess(Object unused) {
                // do nothing.
            }

            // We do not want race condition and repeating reporting failures from computation
            // failed future in right before case Example Store iterator failed.
            // Thus method is synchronised.
            @Override
            public synchronized void onFailure(Throwable throwable) {
                if (mNumberOfInvocations < 1) {
                    LogUtil.d(
                            TAG,
                            "Training failed. Reporting failure result to server due to exception.",
                            throwable);
                    ComputationResult failedReportComputationResult =
                            new ComputationResult(
                                    null,
                                    FLRunnerResult.newBuilder()
                                            .setContributionResult(ContributionResult.FAIL)
                                            .setErrorMessage(throwable.getMessage())
                                            .build(),
                                    null);
                    var unused = mHttpFederatedProtocol.reportResult(failedReportComputationResult);
                }
                mNumberOfInvocations++;
            }
        };
    }

    private static TaskRetry buildTaskRetry(RejectionInfo rejectionInfo) {
        TaskRetry.Builder taskRetryBuilder =
                TaskRetry.newBuilder();
        if (rejectionInfo.hasRetryWindow()) {
            RetryWindow retryWindow =
                    rejectionInfo.getRetryWindow();
            Duration delayMin = retryWindow.getDelayMin();
            // convert rejection info seconds and nanoseconds to
            // retry milliseconds
            taskRetryBuilder.setDelayMin(
                    delayMin.getSeconds() * 1000
                            + delayMin.getNanos() / 1000000);
            Duration delayMax = retryWindow.getDelayMax();
            taskRetryBuilder.setDelayMax(
                    delayMax.getSeconds() * 1000
                            + delayMax.getNanos() / 1000000);
        }
        return taskRetryBuilder.build();
    }

    /**
     * Completes the running job , schedule recurrent job, and unbind from ExampleStoreService and
     * ResultHandlingService etc.
     */
    public void finish(FLRunnerResult flRunnerResult) {
        TaskRetry taskRetry = null;
        ContributionResult contributionResult = ContributionResult.UNSPECIFIED;
        if (flRunnerResult != null) {
            contributionResult = flRunnerResult.getContributionResult();
            if (flRunnerResult.hasRetryInfo()) {
                RetryInfo retryInfo = flRunnerResult.getRetryInfo();
                long delay = retryInfo.getMinimumDelay().getSeconds() * 1000L;
                taskRetry =
                        TaskRetry.newBuilder()
                                .setRetryToken(retryInfo.getRetryToken())
                                .setDelayMin(delay)
                                .setDelayMax(delay)
                                .build();
                LogUtil.i(TAG, "Finished with task retry= %s", taskRetry);
            }
        }
        finish(taskRetry, contributionResult, true);
    }

    /**
     * Cancel the current running job, schedule recurrent job, unbind from ExampleStoreService and
     * ResultHandlingService etc.
     */
    public void finish(
            TaskRetry taskRetry, ContributionResult contributionResult, boolean cancelFuture) {
        TrainingRun runToFinish;
        synchronized (mLock) {
            if (mActiveRun == null) {
                return;
            }

            runToFinish = mActiveRun;
            mActiveRun = null;
            if (cancelFuture) {
                runToFinish.mFuture.cancel(true);
            }
        }

        mJobManager.onTrainingCompleted(
                runToFinish.mJobId,
                runToFinish.mTask.populationName(),
                runToFinish.mTask.getTrainingIntervalOptions(),
                taskRetry,
                contributionResult);
    }

    private void unBindServicesIfNecessary(TrainingRun runToFinish) {
        if (runToFinish.mIsolatedTrainingService != null) {
            LogUtil.i(TAG, "Unbinding from IsolatedTrainingService");
            unbindFromIsolatedTrainingService();
            runToFinish.mIsolatedTrainingService = null;
        }
        if (runToFinish.mExampleStoreService != null) {
            LogUtil.i(TAG, "Unbinding from ExampleStoreService");
            unbindFromExampleStoreService();
            runToFinish.mExampleStoreService = null;
        }
    }

    @VisibleForTesting
    HttpFederatedProtocol getHttpFederatedProtocol(String serverAddress, String populationName) {
        return HttpFederatedProtocol.create(serverAddress, "1.0.0.1", populationName);
    }

    private ExampleSelector getExampleSelector(CheckinResult checkinResult) {
        ClientOnlyPlan clientPlan = checkinResult.getPlanData();
        switch (clientPlan.getPhase().getSpecCase()) {
            case EXAMPLE_QUERY_SPEC:
                // Only support one FA query for now.
                return clientPlan
                        .getPhase()
                        .getExampleQuerySpec()
                        .getExampleQueries(0)
                        .getExampleSelector();
            case TENSORFLOW_SPEC:
                return clientPlan.getPhase().getTensorflowSpec().getExampleSelector();
            default:
                throw new IllegalArgumentException(
                        String.format(
                                "Client plan spec is not supported %s",
                                clientPlan.getPhase().getSpecCase().toString()));
        }
    }

    private boolean checkTrainingConditions(TrainingConstraints constraints) {
        Set<Condition> conditions =
                mTrainingConditionsChecker.checkAllConditionsForFlTraining(constraints);
        for (Condition condition : conditions) {
            switch (condition) {
                case THERMALS_NOT_OK:
                    LogUtil.e(TAG, "training job service interrupt thermals not ok");
                    break;
                case BATTERY_NOT_OK:
                    LogUtil.e(TAG, "training job service interrupt battery not ok");
                    break;
            }
        }
        return conditions.isEmpty();
    }

    @VisibleForTesting
    ListenableFuture<ComputationResult> runFlComputation(
            TrainingRun run,
            CheckinResult checkinResult,
            String outputCheckpointFile,
            IExampleStoreIterator iterator) {
        Trace.beginAsyncSection(TRACE_WORKER_RUN_FL_COMPUTATION, 0);
        ParcelFileDescriptor outputCheckpointFd =
                createTempFileDescriptor(
                        outputCheckpointFile, ParcelFileDescriptor.MODE_READ_WRITE);
        ParcelFileDescriptor inputCheckpointFd =
                createTempFileDescriptor(
                        checkinResult.getInputCheckpointFile(),
                        ParcelFileDescriptor.MODE_READ_ONLY);
        ExampleSelector exampleSelector = getExampleSelector(checkinResult);
        ClientOnlyPlan clientPlan = checkinResult.getPlanData();
        if (clientPlan.getTfliteGraph().isEmpty()) {
            LogUtil.e(
                    TAG,
                    "ClientOnlyPlan input tflite graph is empty."
                            + " population name: %s, task name: %s",
                    run.mTask.populationName(),
                    run.mTaskName);
            return Futures.immediateFailedFuture(
                    new IllegalStateException("Client plan input tflite graph is empty"));
        }

        try {
            // Write ClientOnlyPlan to file and pass ParcelFileDescriptor to isolated process to
            // avoid TransactionTooLargeException through IPC.
            String clientOnlyPlanFile = createTempFile(CLIENT_ONLY_PLAN_FILE_NAME, ".pb");
            FileUtils.writeToFile(clientOnlyPlanFile, clientPlan.toByteArray());
            ParcelFileDescriptor clientPlanFd =
                    createTempFileDescriptor(
                            clientOnlyPlanFile, ParcelFileDescriptor.MODE_READ_ONLY);
            IIsolatedTrainingService trainingService = getIsolatedTrainingService();
            if (trainingService == null) {
                LogUtil.w(TAG, "Could not bind to IsolatedTrainingService");
                throw new IllegalStateException("Could not bind to IsolatedTrainingService");
            }
            run.mIsolatedTrainingService = trainingService;

            Bundle bundle = new Bundle();
            bundle.putByteArray(Constants.EXTRA_EXAMPLE_SELECTOR, exampleSelector.toByteArray());
            bundle.putString(ClientConstants.EXTRA_POPULATION_NAME, run.mTask.populationName());
            bundle.putString(ClientConstants.EXTRA_TASK_NAME, run.mTaskName);
            bundle.putParcelable(Constants.EXTRA_CLIENT_ONLY_PLAN_FD, clientPlanFd);
            bundle.putParcelable(Constants.EXTRA_INPUT_CHECKPOINT_FD, inputCheckpointFd);
            bundle.putParcelable(Constants.EXTRA_OUTPUT_CHECKPOINT_FD, outputCheckpointFd);
            bundle.putBinder(Constants.EXTRA_EXAMPLE_STORE_ITERATOR_BINDER, iterator.asBinder());

            return FluentFuture.from(runIsolatedTrainingProcess(run, bundle))
                    .transform(
                            result -> {
                                ComputationResult computationResult =
                                        processIsolatedTrainingResult(outputCheckpointFile, result);
                                // Close opened file descriptor.
                                try {
                                    if (outputCheckpointFd != null) {
                                        outputCheckpointFd.close();
                                    }
                                    if (inputCheckpointFd != null) {
                                        inputCheckpointFd.close();
                                    }
                                } catch (IOException e) {
                                    LogUtil.e(TAG, "Failed to close file descriptor", e);
                                } finally {
                                    // Unbind from IsolatedTrainingService.
                                    LogUtil.i(TAG, "Unbinding from IsolatedTrainingService");
                                    unbindFromIsolatedTrainingService();
                                    run.mIsolatedTrainingService = null;
                                }
                                Trace.endAsyncSection(TRACE_WORKER_RUN_FL_COMPUTATION, 0);
                                return computationResult;
                            },
                            getLightweightExecutor());
        } catch (Exception e) {
            // Close opened file descriptor.
            try {
                if (outputCheckpointFd != null) {
                    outputCheckpointFd.close();
                }
                if (inputCheckpointFd != null) {
                    inputCheckpointFd.close();
                }
            } catch (IOException t) {
                LogUtil.e(TAG, t, "Failed to close file descriptor");
            } finally {
                // Unbind from IsolatedTrainingService.
                LogUtil.i(TAG, "Unbinding from IsolatedTrainingService");
                unbindFromIsolatedTrainingService();
                run.mIsolatedTrainingService = null;
            }
            return Futures.immediateFailedFuture(e);
        }
    }

    private ComputationResult processIsolatedTrainingResult(
            String outputCheckpoint, Bundle result) {
        byte[] resultBytes =
                Objects.requireNonNull(result.getByteArray(Constants.EXTRA_FL_RUNNER_RESULT));
        FLRunnerResult flRunnerResult;
        try {
            flRunnerResult = FLRunnerResult.parseFrom(resultBytes);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException(e);
        }
        if (flRunnerResult.getContributionResult() == ContributionResult.FAIL) {
            return new ComputationResult(outputCheckpoint, flRunnerResult, new ArrayList<>());
        }
        ArrayList<ExampleConsumption> exampleList =
                result.getParcelableArrayList(
                        ClientConstants.EXTRA_EXAMPLE_CONSUMPTION_LIST, ExampleConsumption.class);
        if (exampleList == null || exampleList.isEmpty()) {
            throw new IllegalArgumentException("example consumption list should not be empty");
        }

        return new ComputationResult(outputCheckpoint, flRunnerResult, exampleList);
    }

    private ListenableFuture<Bundle> runIsolatedTrainingProcess(TrainingRun run, Bundle input) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    try {
                        run.mIsolatedTrainingService.runFlTraining(
                                input,
                                new ITrainingResultCallback.Stub() {
                                    @Override
                                    public void onResult(Bundle result) {
                                        completer.set(result);
                                    }
                                });
                    } catch (Exception e) {
                        LogUtil.e(TAG, e, "Got exception when runIsolatedTrainingProcess");
                        completer.setException(e);
                    }
                    return "runIsolatedTrainingProcess";
                });
    }

    private ListenableFuture<ComputationResult> runFederatedComputation(
            CheckinResult checkinResult, TrainingRun run, IExampleStoreIterator iterator) {
        ClientOnlyPlan clientPlan = checkinResult.getPlanData();
        String outputCheckpointFile = createTempFile("output", ".ckp");

        ListenableFuture<ComputationResult> computationResultFuture;
        switch (clientPlan.getPhase().getSpecCase()) {
            case EXAMPLE_QUERY_SPEC:
                computationResultFuture =
                        runFAComputation(run, checkinResult, outputCheckpointFile, iterator);
                break;
            case TENSORFLOW_SPEC:
                computationResultFuture =
                        runFlComputation(run, checkinResult, outputCheckpointFile, iterator);
                break;
            default:
                return Futures.immediateFailedFuture(
                        new IllegalArgumentException(
                                String.format(
                                        "Client plan spec is not supported %s",
                                        clientPlan.getPhase().getSpecCase().toString())));
        }
        return computationResultFuture;
    }

    private ListenableFuture<ComputationResult> runFAComputation(
            TrainingRun run,
            CheckinResult checkinResult,
            String outputCheckpointFile,
            IExampleStoreIterator exampleStoreIterator) {
        ExampleSelector exampleSelector = getExampleSelector(checkinResult);
        ClientOnlyPlan clientPlan = checkinResult.getPlanData();
        // The federated analytic runs in main process which has permission to file system.
        ExampleConsumptionRecorder recorder = mInjector.getExampleConsumptionRecorder();
        FLRunnerResult runResult =
                mComputationRunner.runTaskWithNativeRunner(
                        run.mTaskName,
                        run.mTask.populationName(),
                        checkinResult.getInputCheckpointFile(),
                        outputCheckpointFile,
                        clientPlan,
                        exampleSelector,
                        recorder,
                        exampleStoreIterator,
                        mInterruptSupplier);
        ArrayList<ExampleConsumption> exampleConsumptions = recorder.finishRecordingAndGet();
        return Futures.immediateFuture(
                new ComputationResult(outputCheckpointFile, runResult, exampleConsumptions));
    }

    @VisibleForTesting
    IExampleStoreService getExampleStoreService(String packageName) {
        mExampleStoreServiceBinder =
                AbstractServiceBinder.getServiceBinderByIntent(
                        mContext,
                        ClientConstants.EXAMPLE_STORE_ACTION,
                        packageName,
                        IExampleStoreService.Stub::asInterface);
        return mExampleStoreServiceBinder.getService(Runnable::run);
    }

    @VisibleForTesting
    void unbindFromExampleStoreService() {
        mExampleStoreServiceBinder.unbindFromService();
    }

    private ListenableFuture<IExampleStoreIterator> runExampleStoreStartQuery(
            TrainingRun run, Bundle input) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    try {
                        run.mExampleStoreService.startQuery(
                                input,
                                new IExampleStoreCallback.Stub() {
                                    @Override
                                    public void onStartQuerySuccess(
                                            IExampleStoreIterator iterator) {
                                        LogUtil.d(TAG, "Acquire iterator");
                                        completer.set(iterator);
                                    }

                                    @Override
                                    public void onStartQueryFailure(int errorCode) {
                                        LogUtil.e(TAG, "Could not acquire iterator: " + errorCode);
                                        completer.setException(
                                                new IllegalStateException(
                                                        "StartQuery failed: " + errorCode));
                                    }
                                });
                    } catch (Exception e) {
                        completer.setException(e);
                    }
                    return "runExampleStoreStartQuery";
                });
    }

    private ListenableFuture<IExampleStoreIterator> getExampleStoreIterator(
            TrainingRun run, String packageName, String taskName, ExampleSelector exampleSelector) {
        try {
            run.mTaskName = taskName;

            IExampleStoreService exampleStoreService = getExampleStoreService(packageName);
            if (exampleStoreService == null) {
                return Futures.immediateFailedFuture(
                        new IllegalStateException(
                                "Could not bind to ExampleStoreService " + packageName));
            }
            run.mExampleStoreService = exampleStoreService;

            byte[] criteria = exampleSelector.getCriteria().toByteArray();
            byte[] resumptionToken = exampleSelector.getResumptionToken().toByteArray();
            Bundle bundle = new Bundle();
            bundle.putString(ClientConstants.EXTRA_POPULATION_NAME, run.mTask.populationName());
            bundle.putString(ClientConstants.EXTRA_TASK_NAME, taskName);
            bundle.putByteArray(ClientConstants.EXTRA_CONTEXT_DATA, run.mTask.contextData());
            bundle.putByteArray(
                    ClientConstants.EXTRA_EXAMPLE_ITERATOR_RESUMPTION_TOKEN, resumptionToken);
            bundle.putByteArray(ClientConstants.EXTRA_EXAMPLE_ITERATOR_CRITERIA, criteria);

            return runExampleStoreStartQuery(run, bundle);
        } catch (Exception e) {
            LogUtil.e(TAG, "StartQuery failure: " + e.getMessage());
            return Futures.immediateFailedFuture(e);
        }
    }

    @VisibleForTesting
    @Nullable
    IIsolatedTrainingService getIsolatedTrainingService() {
        mIsolatedTrainingServiceBinder =
                AbstractServiceBinder.getServiceBinderByServiceName(
                        mContext,
                        ISOLATED_TRAINING_SERVICE_NAME,
                        mContext.getPackageName(),
                        IIsolatedTrainingService.Stub::asInterface);
        return mIsolatedTrainingServiceBinder.getService(Runnable::run);
    }

    @VisibleForTesting
    void unbindFromIsolatedTrainingService() {
        mIsolatedTrainingServiceBinder.unbindFromService();
    }

    @VisibleForTesting
    static class Injector {
        ExampleConsumptionRecorder getExampleConsumptionRecorder() {
            return new ExampleConsumptionRecorder();
        }

        ListeningExecutorService getBgExecutor() {
            return getBackgroundExecutor();
        }
    }

    private static final class TrainingRun {
        private final int mJobId;

        private String mTaskName;
        private final FederatedTrainingTask mTask;

        @Nullable private ListenableFuture<?> mFuture;

        @Nullable private IIsolatedTrainingService mIsolatedTrainingService = null;

        @Nullable private IExampleStoreService mExampleStoreService = null;

        private TrainingRun(int jobId, FederatedTrainingTask task) {
            this.mJobId = jobId;
            this.mTask = task;
        }
    }
}
