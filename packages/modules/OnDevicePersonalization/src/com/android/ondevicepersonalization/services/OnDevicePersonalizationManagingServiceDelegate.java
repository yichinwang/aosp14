/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ondevicepersonalization.services;

import android.adservices.ondevicepersonalization.CallerMetadata;
import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PersistableBundle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.services.request.AppRequestFlow;
import com.android.ondevicepersonalization.services.request.RenderFlow;

import java.util.Objects;

/** Implementation of OnDevicePersonalizationManagingService */
public class OnDevicePersonalizationManagingServiceDelegate
        extends IOnDevicePersonalizationManagingService.Stub {
    @NonNull private final Context mContext;

    @VisibleForTesting
    static class Injector {
        AppRequestFlow getAppRequestFlow(
                String callingPackageName,
                ComponentName handler,
                PersistableBundle params,
                IExecuteCallback callback,
                Context context,
                long startTimeMillis) {
            return new AppRequestFlow(
                    callingPackageName, handler, params, callback, context, startTimeMillis);
        }

        RenderFlow getRenderFlow(
                String slotResultToken,
                IBinder hostToken,
                int displayId,
                int width,
                int height,
                IRequestSurfacePackageCallback callback,
                Context context,
                long startTimeMillis) {
            return new RenderFlow(
                    slotResultToken, hostToken, displayId, width, height, callback, context,
                    startTimeMillis);
        }
    }

    @NonNull private final Injector mInjector;

    public OnDevicePersonalizationManagingServiceDelegate(@NonNull Context context) {
        this(context, new Injector());
    }

    @VisibleForTesting
    public OnDevicePersonalizationManagingServiceDelegate(
            @NonNull Context context,
            @NonNull Injector injector) {
        mContext = Objects.requireNonNull(context);
        mInjector = Objects.requireNonNull(injector);
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void execute(
            @NonNull String callingPackageName,
            @NonNull ComponentName handler,
            @NonNull PersistableBundle params,
            @NonNull CallerMetadata metadata,
            @NonNull IExecuteCallback callback) {
        if (getGlobalKillSwitch()) {
            throw new IllegalStateException("Service skipped as the global kill switch is on.");
        }

        Objects.requireNonNull(callingPackageName);
        Objects.requireNonNull(handler);
        Objects.requireNonNull(handler.getPackageName());
        Objects.requireNonNull(handler.getClassName());
        Objects.requireNonNull(params);
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(callback);
        if (callingPackageName.isEmpty()) {
            throw new IllegalArgumentException("missing app package name");
        }
        if (handler.getPackageName().isEmpty()) {
            throw new IllegalArgumentException("missing service package name");
        }
        if (handler.getClassName().isEmpty()) {
            throw new IllegalArgumentException("missing service class name");
        }

        final int uid = Binder.getCallingUid();
        enforceCallingPackageBelongsToUid(callingPackageName, uid);

        AppRequestFlow flow = mInjector.getAppRequestFlow(
                callingPackageName,
                handler,
                params,
                callback,
                mContext,
                metadata.getStartTimeMillis());
        flow.run();
    }

    @Override
    public void requestSurfacePackage(
            @NonNull String slotResultToken,
            @NonNull IBinder hostToken,
            int displayId,
            int width,
            int height,
            @NonNull CallerMetadata metadata,
            @NonNull IRequestSurfacePackageCallback callback) {
        if (getGlobalKillSwitch()) {
            throw new IllegalStateException("Service skipped as the global kill switch is on.");
        }

        Objects.requireNonNull(slotResultToken);
        Objects.requireNonNull(hostToken);
        Objects.requireNonNull(callback);
        if (width <= 0) {
            throw new IllegalArgumentException("width must be > 0");
        }

        if (height <= 0) {
            throw new IllegalArgumentException("height must be > 0");
        }

        if (displayId < 0) {
            throw new IllegalArgumentException("displayId must be >= 0");
        }

        RenderFlow flow = mInjector.getRenderFlow(
                slotResultToken,
                hostToken,
                displayId,
                width,
                height,
                callback,
                mContext,
                metadata.getStartTimeMillis());
        flow.run();
    }

    private boolean getGlobalKillSwitch() {
        long origId = Binder.clearCallingIdentity();
        boolean globalKillSwitch = FlagsFactory.getFlags().getGlobalKillSwitch();
        Binder.restoreCallingIdentity(origId);
        return globalKillSwitch;
    }
    private void enforceCallingPackageBelongsToUid(@NonNull String packageName, int uid) {
        int packageUid;
        PackageManager pm = mContext.getPackageManager();
        try {
            packageUid = pm.getPackageUid(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException(packageName + " not found");
        }
        if (packageUid != uid) {
            throw new SecurityException(packageName + " does not belong to uid " + uid);
        }
        //TODO(b/242792629): Handle requests from the SDK sandbox.
    }
}
