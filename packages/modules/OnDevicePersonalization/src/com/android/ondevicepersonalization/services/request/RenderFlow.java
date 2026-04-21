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

package com.android.ondevicepersonalization.services.request;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.RenderInputParcel;
import android.adservices.ondevicepersonalization.RenderOutputParcel;
import android.adservices.ondevicepersonalization.RenderingConfig;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.SurfaceControlViewHost.SurfacePackage;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.DataAccessServiceImpl;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.display.DisplayHelper;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.process.IsolatedServiceInfo;
import com.android.ondevicepersonalization.services.process.ProcessRunner;
import com.android.ondevicepersonalization.services.statsd.ApiCallStats;
import com.android.ondevicepersonalization.services.statsd.OdpStatsdLogger;
import com.android.ondevicepersonalization.services.util.Clock;
import com.android.ondevicepersonalization.services.util.CryptUtils;
import com.android.ondevicepersonalization.services.util.MonotonicClock;
import com.android.ondevicepersonalization.services.util.StatsUtils;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Handles a surface package request from an app or SDK.
 */
public class RenderFlow {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "RenderFlow";
    private static final String TASK_NAME = "Render";

    @VisibleForTesting
    static class Injector {
        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        SlotWrapper decryptToken(String slotResultToken) throws Exception {
            return (SlotWrapper) CryptUtils.decrypt(slotResultToken);
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

    @NonNull
    private final String mSlotResultToken;
    @NonNull
    private final IBinder mHostToken;
    @NonNull private final int mDisplayId;
    @NonNull private final int mWidth;
    @NonNull private final int mHeight;
    @NonNull
    private final IRequestSurfacePackageCallback mCallback;
    @NonNull
    private final Context mContext;
    private final long mStartTimeMillis;
    @NonNull
    private final Injector mInjector;
    @NonNull
    private final DisplayHelper mDisplayHelper;
    @NonNull
    private String mServicePackageName;
    @NonNull
    private String mServiceClassName;

    public RenderFlow(
            @NonNull String slotResultToken,
            @NonNull IBinder hostToken,
            int displayId,
            int width,
            int height,
            @NonNull IRequestSurfacePackageCallback callback,
            @NonNull Context context,
            long startTimeMillis) {
        this(slotResultToken, hostToken, displayId, width, height,
                callback, context, startTimeMillis,
                new Injector(),
                new DisplayHelper(context));
    }

    @VisibleForTesting
    RenderFlow(
            @NonNull String slotResultToken,
            @NonNull IBinder hostToken,
            int displayId,
            int width,
            int height,
            @NonNull IRequestSurfacePackageCallback callback,
            @NonNull Context context,
            long startTimeMillis,
            @NonNull Injector injector,
            @NonNull DisplayHelper displayHelper) {
        sLogger.d(TAG + ": RenderFlow created.");
        mSlotResultToken = Objects.requireNonNull(slotResultToken);
        mHostToken = Objects.requireNonNull(hostToken);
        mDisplayId = displayId;
        mWidth = width;
        mHeight = height;
        mCallback = Objects.requireNonNull(callback);
        mStartTimeMillis = startTimeMillis;
        mInjector = Objects.requireNonNull(injector);
        mContext = Objects.requireNonNull(context);
        mDisplayHelper = Objects.requireNonNull(displayHelper);
    }

    /** Runs the request processing flow. */
    public void run() {
        var unused = Futures.submit(() -> this.processRequest(), mInjector.getExecutor());
    }

    private void processRequest() {
        try {
            if (!isPersonalizationStatusEnabled()) {
                sLogger.d(TAG + ": Personalization is disabled.");
                sendErrorResult(Constants.STATUS_PERSONALIZATION_DISABLED);
                return;
            }
            SlotWrapper slotWrapper = Objects.requireNonNull(
                    mInjector.decryptToken(mSlotResultToken));
            mServicePackageName = Objects.requireNonNull(
                    slotWrapper.getServicePackageName());
            mServiceClassName = Objects.requireNonNull(
                    AppManifestConfigHelper.getServiceNameFromOdpSettings(
                        mContext, mServicePackageName));

            ListenableFuture<IsolatedServiceInfo> loadFuture =
                    mInjector.getProcessRunner().loadIsolatedService(
                            TASK_NAME, mServicePackageName);
            ListenableFuture<SurfacePackage> surfacePackageFuture =
                    FluentFuture.from(renderContentForSlot(loadFuture, slotWrapper))
                    .withTimeout(
                            mInjector.getFlags().getIsolatedServiceDeadlineSeconds(),
                            TimeUnit.SECONDS,
                            mInjector.getScheduledExecutor()
                    );

            Futures.addCallback(
                    surfacePackageFuture,
                    new FutureCallback<SurfacePackage>() {
                        @Override
                        public void onSuccess(SurfacePackage surfacePackage) {
                            sendDisplayResult(surfacePackage);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            sLogger.w(TAG + ": Request failed.", t);
                            sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
                        }
                    },
                    mInjector.getExecutor());

            var unused = Futures.whenAllComplete(loadFuture, surfacePackageFuture)
                    .callAsync(() -> mInjector.getProcessRunner().unloadIsolatedService(
                            loadFuture.get()),
                    mInjector.getExecutor());
        } catch (Exception e) {
            sLogger.e(TAG + ": Could not process request.", e);
            sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
        }
    }

    private ListenableFuture<SurfacePackage> renderContentForSlot(
            @NonNull ListenableFuture<IsolatedServiceInfo> loadFuture,
            @NonNull SlotWrapper slotWrapper
    ) {
        try {
            sLogger.d(TAG + ": renderContentForSlot() started.");
            Objects.requireNonNull(slotWrapper);
            RequestLogRecord logRecord = Objects.requireNonNull(slotWrapper.getLogRecord());
            RenderingConfig renderingConfig =
                    Objects.requireNonNull(slotWrapper.getRenderingConfig());
            long queryId = slotWrapper.getQueryId();

            return FluentFuture.from(loadFuture)
                    .transformAsync(
                            loadResult -> executeRenderContentRequest(
                                    loadResult, slotWrapper.getSlotIndex(), renderingConfig),
                            mInjector.getExecutor())
                    .transform(result -> {
                        return result.getParcelable(
                                Constants.EXTRA_RESULT, RenderOutputParcel.class);
                    }, mInjector.getExecutor())
                    .transform(
                            result -> mDisplayHelper.generateHtml(result, mServicePackageName),
                            mInjector.getExecutor())
                    .transformAsync(
                            result -> mDisplayHelper.displayHtml(
                                    result,
                                    logRecord,
                                    queryId,
                                    mServicePackageName,
                                    mHostToken,
                                    mDisplayId,
                                    mWidth,
                                    mHeight),
                            mInjector.getExecutor());
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private ListenableFuture<Bundle> executeRenderContentRequest(
            IsolatedServiceInfo isolatedServiceInfo, int slotIndex,
            RenderingConfig renderingConfig) {
        sLogger.d(TAG + "executeRenderContentRequest() started.");
        Bundle serviceParams = new Bundle();
        RenderInputParcel input =
                new RenderInputParcel.Builder()
                    .setHeight(mHeight)
                    .setWidth(mWidth)
                    .setRenderingConfigIndex(slotIndex)
                    .setRenderingConfig(renderingConfig)
                    .build();
        serviceParams.putParcelable(Constants.EXTRA_INPUT, input);
        DataAccessServiceImpl binder = new DataAccessServiceImpl(
                mServicePackageName, mContext, /* includeLocalData */ false,
                /* includeEventData */ false);
        serviceParams.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, binder);
        ListenableFuture<Bundle> result = mInjector.getProcessRunner().runIsolatedService(
                isolatedServiceInfo, mServiceClassName, Constants.OP_RENDER,
                serviceParams);
        return FluentFuture.from(result)
                .transform(
                    val -> {
                        writeServiceRequestMetrics(
                                val, isolatedServiceInfo.getStartTimeMillis(),
                                Constants.STATUS_SUCCESS);
                        return val;
                    },
                    mInjector.getExecutor()
                )
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
    }

    private void sendDisplayResult(SurfacePackage surfacePackage) {
        if (surfacePackage != null) {
            sendSuccessResult(surfacePackage);
        } else {
            sLogger.w(TAG + ": surfacePackages is null or empty");
            sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
        }
    }

    private void sendSuccessResult(SurfacePackage surfacePackage) {
        int responseCode = Constants.STATUS_SUCCESS;
        try {
            mCallback.onSuccess(surfacePackage);
        } catch (RemoteException e) {
            responseCode = Constants.STATUS_INTERNAL_ERROR;
            sLogger.w(TAG + ": Callback error", e);
        } finally {
            writeAppRequestMetrics(responseCode);
        }
    }

    private void sendErrorResult(int errorCode) {
        try {
            mCallback.onError(errorCode);
        } catch (RemoteException e) {
            sLogger.w(TAG + ": Callback error", e);
        } finally {
            writeAppRequestMetrics(errorCode);
        }
    }

    private void writeAppRequestMetrics(int responseCode) {
        int latencyMillis = (int) (mInjector.getClock().elapsedRealtime() - mStartTimeMillis);
        ApiCallStats callStats = new ApiCallStats.Builder(ApiCallStats.API_REQUEST_SURFACE_PACKAGE)
                .setLatencyMillis(latencyMillis)
                .setResponseCode(responseCode)
                .build();
        OdpStatsdLogger.getInstance().logApiCallStats(callStats);
    }

    private void writeServiceRequestMetrics(Bundle result, long startTimeMillis, int responseCode) {
        int latencyMillis = (int) (mInjector.getClock().elapsedRealtime() - startTimeMillis);
        int overheadLatencyMillis =
                (int) StatsUtils.getOverheadLatencyMillis(latencyMillis, result);
        ApiCallStats callStats = new ApiCallStats.Builder(ApiCallStats.API_SERVICE_ON_RENDER)
                .setLatencyMillis(latencyMillis)
                .setOverheadLatencyMillis(overheadLatencyMillis)
                .setResponseCode(responseCode)
                .build();
        OdpStatsdLogger.getInstance().logApiCallStats(callStats);
    }

    private boolean isPersonalizationStatusEnabled() {
        UserPrivacyStatus privacyStatus = UserPrivacyStatus.getInstance();
        return privacyStatus.isPersonalizationStatusEnabled();
    }
}
