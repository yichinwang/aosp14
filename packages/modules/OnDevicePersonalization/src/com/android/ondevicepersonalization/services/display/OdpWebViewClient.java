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

package com.android.ondevicepersonalization.services.display;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.EventInputParcel;
import android.adservices.ondevicepersonalization.EventLogRecord;
import android.adservices.ondevicepersonalization.EventOutputParcel;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.adservices.ondevicepersonalization.UserData;
import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.DataAccessServiceImpl;
import com.android.ondevicepersonalization.services.data.events.Event;
import com.android.ondevicepersonalization.services.data.events.EventUrlHelper;
import com.android.ondevicepersonalization.services.data.events.EventUrlPayload;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.policyengine.UserDataAccessor;
import com.android.ondevicepersonalization.services.process.IsolatedServiceInfo;
import com.android.ondevicepersonalization.services.process.ProcessRunner;
import com.android.ondevicepersonalization.services.statsd.ApiCallStats;
import com.android.ondevicepersonalization.services.statsd.OdpStatsdLogger;
import com.android.ondevicepersonalization.services.util.Clock;
import com.android.ondevicepersonalization.services.util.MonotonicClock;
import com.android.ondevicepersonalization.services.util.OnDevicePersonalizationFlatbufferUtils;
import com.android.ondevicepersonalization.services.util.StatsUtils;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

class OdpWebViewClient extends WebViewClient {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OdpWebViewClient";
    public static final String TASK_NAME = "ComputeEventMetrics";

    @VisibleForTesting
    static class Injector {
        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        void openUrl(String landingPage, Context context) {
            if (landingPage != null) {
                sLogger.d(TAG + ": Sending intent to open landingPage: " + landingPage);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(landingPage));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        }

        Clock getClock() {
            return MonotonicClock.getInstance();
        }

        Flags getFlags() {
            return FlagsFactory.getFlags();
        }

        ListeningScheduledExecutorService getScheduledExecutor() {
            return OnDevicePersonalizationExecutors.getScheduledExecutor();
        }

        ProcessRunner getProcessRunner() {
            return ProcessRunner.getInstance();
        }
    }

    @NonNull private final Context mContext;
    @NonNull private final String mServicePackageName;
    long mQueryId;
    @NonNull private final RequestLogRecord mLogRecord;
    @NonNull private final Injector mInjector;

    OdpWebViewClient(Context context, String servicePackageName, long queryId,
            RequestLogRecord logRecord) {
        this(context, servicePackageName, queryId, logRecord, new Injector());
    }

    @VisibleForTesting
    OdpWebViewClient(Context context, String servicePackageName, long queryId,
            RequestLogRecord logRecord, Injector injector) {
        mContext = context;
        mServicePackageName = servicePackageName;
        mQueryId = queryId;
        mLogRecord = logRecord;
        mInjector = injector;
    }

    @Override public WebResourceResponse shouldInterceptRequest(
        @NonNull WebView webView, @NonNull WebResourceRequest request) {
        try {
            if (webView == null || request == null || request.getUrl() == null) {
                sLogger.e(TAG + ": Received null webView or Request or Url");
                return null;
            }
            String url = request.getUrl().toString();
            sLogger.d(TAG + ": shouldInterceptRequest: " + url);
            if (EventUrlHelper.isOdpUrl(url)) {
                EventUrlPayload payload = EventUrlHelper.getEventFromOdpEventUrl(url);
                mInjector.getExecutor().execute(() -> handleEvent(payload));
                byte[] responseData = payload.getResponseData();
                if (responseData == null || responseData.length == 0) {
                    return new WebResourceResponse(
                            null, null, HttpURLConnection.HTTP_NO_CONTENT, "No Content",
                            Collections.emptyMap(), InputStream.nullInputStream());
                } else {
                    return new WebResourceResponse(
                            payload.getMimeType(), null, HttpURLConnection.HTTP_OK, "OK",
                            Collections.emptyMap(),
                            new ByteArrayInputStream(responseData));
                }
            }
        } catch (Exception e) {
            sLogger.e(e, TAG + ": shouldInterceptRequest failed.");
        }
        return null;
    }

    @Override
    public boolean shouldOverrideUrlLoading(
            @NonNull WebView webView, @NonNull WebResourceRequest request) {
        try {
            if (webView == null || request == null) {
                sLogger.e(TAG + ": Received null webView or Request");
                return true;
            }
            //Decode odp://localhost/ URIs and call Events table API to write an event.
            String url = request.getUrl().toString();
            sLogger.d(TAG + ": shouldOverrideUrlLoading: " + url);
            if (EventUrlHelper.isOdpUrl(url)) {
                EventUrlPayload payload = EventUrlHelper.getEventFromOdpEventUrl(url);
                mInjector.getExecutor().execute(() -> handleEvent(payload));
                String landingPage = request.getUrl().getQueryParameter(
                        EventUrlHelper.URL_LANDING_PAGE_EVENT_KEY);
                mInjector.openUrl(landingPage, webView.getContext());
            } else {
                // TODO(b/263180569): Handle any non-odp URLs
                sLogger.d(TAG + ": Non-odp URL encountered: " + url);
            }
        } catch (Exception e) {
            sLogger.e(e, TAG + ": shouldOverrideUrlLoading failed.");
        }
        // Cancel the current load
        return true;
    }

    private ListenableFuture<EventOutputParcel> executeEventHandler(
            IsolatedServiceInfo isolatedServiceInfo,
            EventUrlPayload payload) {
        try {
            sLogger.d(TAG + ": executeEventHandler() called");
            Bundle serviceParams = new Bundle();
            DataAccessServiceImpl binder = new DataAccessServiceImpl(
                    mServicePackageName, mContext, /* includeLocalData */ true,
                    /* includeEventData */ true);
            serviceParams.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, binder);
            // TODO(b/259950177): Add Query row to input.
            EventInputParcel input = new EventInputParcel.Builder()
                    .setParameters(payload.getEventParams())
                    .setRequestLogRecord(mLogRecord)
                    .build();
            serviceParams.putParcelable(Constants.EXTRA_INPUT, input);
            UserDataAccessor userDataAccessor = new UserDataAccessor();
            UserData userData = userDataAccessor.getUserData();
            serviceParams.putParcelable(Constants.EXTRA_USER_DATA, userData);
            return FluentFuture.from(
                    mInjector.getProcessRunner().runIsolatedService(
                        isolatedServiceInfo,
                        AppManifestConfigHelper.getServiceNameFromOdpSettings(
                                mContext, mServicePackageName),
                        Constants.OP_WEB_VIEW_EVENT,
                        serviceParams))
                    .transform(
                            result -> {
                                writeServiceRequestMetrics(
                                        result, isolatedServiceInfo.getStartTimeMillis(),
                                        Constants.STATUS_SUCCESS);
                                return result.getParcelable(
                                        Constants.EXTRA_RESULT, EventOutputParcel.class);
                            },
                            mInjector.getExecutor())
                    .catchingAsync(
                            Exception.class,
                            e -> {
                                writeServiceRequestMetrics(
                                        null, isolatedServiceInfo.getStartTimeMillis(),
                                        Constants.STATUS_INTERNAL_ERROR);
                                return Futures.immediateFailedFuture(e);
                            },
                            mInjector.getExecutor()
                    );
        } catch (Exception e) {
            sLogger.e(TAG + ": executeEventHandler() failed", e);
            return Futures.immediateFailedFuture(e);
        }

    }

    ListenableFuture<EventOutputParcel> getEventOutputParcel(
            ListenableFuture<IsolatedServiceInfo> loadFuture,
            EventUrlPayload payload) {
        try {
            sLogger.d(TAG + ": getEventOutputParcel(): Starting isolated process.");
            return FluentFuture.from(loadFuture)
                .transformAsync(
                        result -> executeEventHandler(result, payload),
                        mInjector.getExecutor());

        } catch (Exception e) {
            sLogger.e(TAG + ": getEventOutputParcel() failed", e);
            return Futures.immediateFailedFuture(e);
        }
    }

    private ListenableFuture<Void> writeEvent(EventOutputParcel result) {
        try {
            sLogger.d(TAG + ": writeEvent() called. EventOutputParcel: " + result.toString());
            if (result == null || result.getEventLogRecord() == null) {
                return Futures.immediateFuture(null);
            }
            EventLogRecord eventData = result.getEventLogRecord();
            int rowCount = 0;
            if (mLogRecord.getRows() != null) {
                rowCount = mLogRecord.getRows().size();
            }
            if (eventData.getType() <= 0 || eventData.getRowIndex() < 0
                    || eventData.getRowIndex() >= rowCount) {
                sLogger.w(TAG + ": rowOffset out of range");
                return Futures.immediateFuture(null);
            }
            byte[] data = OnDevicePersonalizationFlatbufferUtils.createEventData(
                    eventData.getData());
            Event event = new Event.Builder()
                    .setType(eventData.getType())
                    .setQueryId(mQueryId)
                    .setServicePackageName(mServicePackageName)
                    .setTimeMillis(mInjector.getClock().currentTimeMillis())
                    .setRowIndex(eventData.getRowIndex())
                    .setEventData(data)
                    .build();
            if (-1 == EventsDao.getInstance(mContext).insertEvent(event)) {
                sLogger.e(TAG + ": Failed to insert event: " + event);
            }
            return Futures.immediateFuture(null);
        } catch (Exception e) {
            sLogger.e(TAG + ": writeEvent() failed", e);
            return Futures.immediateFailedFuture(e);
        }
    }

    private void handleEvent(EventUrlPayload eventUrlPayload) {
        try {
            sLogger.d(TAG + ": handleEvent() called");

            ListenableFuture<IsolatedServiceInfo> loadFuture =
                    mInjector.getProcessRunner().loadIsolatedService(
                        TASK_NAME, mServicePackageName);

            var doneFuture = FluentFuture.from(getEventOutputParcel(loadFuture, eventUrlPayload))
                    .transformAsync(
                        result -> writeEvent(result),
                        mInjector.getExecutor())
                    .withTimeout(
                        mInjector.getFlags().getIsolatedServiceDeadlineSeconds(),
                        TimeUnit.SECONDS,
                        mInjector.getScheduledExecutor()
                    );

            var unused = Futures.whenAllComplete(loadFuture, doneFuture)
                    .callAsync(() -> mInjector.getProcessRunner().unloadIsolatedService(
                            loadFuture.get()),
                    mInjector.getExecutor());
        } catch (Exception e) {
            sLogger.e(TAG + ": Failed to handle Event", e);
        }
    }

    private void writeServiceRequestMetrics(Bundle result, long startTimeMillis, int responseCode) {
        int latencyMillis = (int) (mInjector.getClock().elapsedRealtime() - startTimeMillis);
        int overheadLatencyMillis =
                (int) StatsUtils.getOverheadLatencyMillis(latencyMillis, result);
        ApiCallStats callStats = new ApiCallStats.Builder(ApiCallStats.API_SERVICE_ON_EVENT)
                .setLatencyMillis(latencyMillis)
                .setOverheadLatencyMillis(overheadLatencyMillis)
                .setResponseCode(responseCode)
                .build();
        OdpStatsdLogger.getInstance().logApiCallStats(callStats);
    }
}
