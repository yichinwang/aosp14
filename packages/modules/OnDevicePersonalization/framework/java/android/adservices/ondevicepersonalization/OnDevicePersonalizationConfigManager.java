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

package android.adservices.ondevicepersonalization;

import static android.adservices.ondevicepersonalization.Constants.KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS;
import static android.adservices.ondevicepersonalization.OnDevicePersonalizationPermissions.MODIFY_ONDEVICEPERSONALIZATION_STATE;

import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationConfigService;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationConfigServiceCallback;
import android.annotation.CallbackExecutor;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * OnDevicePersonalizationConfigManager provides system APIs
 * for privileged APKs to control OnDevicePersonalization's enablement status.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS)
public class OnDevicePersonalizationConfigManager {
    /** @hide */
    public static final String ON_DEVICE_PERSONALIZATION_CONFIG_SERVICE =
            "on_device_personalization_config_service";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationConfigManager";
    private static final String ODP_CONFIG_SERVICE_INTENT =
            "android.OnDevicePersonalizationConfigService";
    private static final int BIND_SERVICE_TIMEOUT_SEC = 5;
    private final Context mContext;
    private final CountDownLatch mConnectionLatch = new CountDownLatch(1);
    private boolean mBound = false;
    private IOnDevicePersonalizationConfigService mService = null;
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mService = IOnDevicePersonalizationConfigService.Stub.asInterface(binder);
            mBound = true;
            mConnectionLatch.countDown();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            mBound = false;
            mConnectionLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
        }
    };

    /** @hide */
    public OnDevicePersonalizationConfigManager(@NonNull Context context) {
        mContext = context;
    }

    /**
     * API users are expected to call this to modify personalization status for
     * On Device Personalization. The status is persisted both in memory and to the disk.
     * When reboot, the in-memory status will be restored from the disk.
     * Personalization is disabled by default.
     *
     * @param enabled boolean whether On Device Personalization should be enabled.
     * @param executor The {@link Executor} on which to invoke the callback.
     * @param receiver This either returns null on success or {@link Exception} on failure.
     *
     *     In case of an error, the receiver returns one of the following exceptions:
     *     Returns an {@link IllegalStateException} if the callback is unable to send back results.
     *     Returns a {@link SecurityException} if the caller is unauthorized to modify
     *     personalization status.
     */
    @FlaggedApi(KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS)
    @RequiresPermission(MODIFY_ONDEVICEPERSONALIZATION_STATE)
    public void setPersonalizationEnabled(boolean enabled,
                                          @NonNull @CallbackExecutor Executor executor,
                                          @NonNull OutcomeReceiver<Void, Exception> receiver) {

        try {
            bindService(executor);

            mService.setPersonalizationStatus(enabled,
                    new IOnDevicePersonalizationConfigServiceCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(() -> {
                                Binder.clearCallingIdentity();
                                receiver.onResult(null);
                            });
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            executor.execute(() -> {
                                sLogger.w(TAG + ": Unexpected failure from ODP"
                                        + "config service with error code: " + errorCode);
                                Binder.clearCallingIdentity();
                                receiver.onError(new IllegalStateException("Unexpected failure."));
                            });
                        }
                    });
        } catch (IllegalStateException | InterruptedException | RemoteException e) {
            executor.execute(() -> {
                receiver.onError(new IllegalStateException(e));
            });
        } catch (SecurityException e) {
            executor.execute(() -> {
                sLogger.w(TAG + ": Unauthorized call to ODP config service.");
                receiver.onError(e);
            });
        }
    }

    private void bindService(@NonNull Executor executor) throws InterruptedException {
        if (!mBound) {
            Intent intent = new Intent(ODP_CONFIG_SERVICE_INTENT);
            ComponentName serviceComponent = resolveService(intent);
            if (serviceComponent == null) {
                sLogger.e(TAG + ": Invalid component for ODP config service");
                return;
            }

            intent.setComponent(serviceComponent);
            boolean r = mContext.bindService(
                    intent, Context.BIND_AUTO_CREATE, executor, mConnection);
            if (!r) {
                return;
            }
            mConnectionLatch.await(BIND_SERVICE_TIMEOUT_SEC, TimeUnit.SECONDS);
        }
    }

    /**
     * Find the ComponentName of the service, given its intent.
     *
     * @return ComponentName of the service. Null if the service is not found.
     */
    @Nullable
    private ComponentName resolveService(@NonNull Intent intent) {
        List<ResolveInfo> services = mContext.getPackageManager().queryIntentServices(intent, 0);
        if (services == null || services.isEmpty()) {
            sLogger.e(TAG + ": Failed to find OnDevicePersonalizationConfigService");
            return null;
        }

        for (int i = 0; i < services.size(); i++) {
            ServiceInfo serviceInfo = services.get(i).serviceInfo;
            if (serviceInfo == null) {
                sLogger.e(TAG + ": Failed to find serviceInfo "
                        + "for OnDevicePersonalizationConfigService.");
                return null;
            }
            // There should only be one matching service inside the given package.
            // If there's more than one, return the first one found.
            return new ComponentName(serviceInfo.packageName, serviceInfo.name);
        }
        sLogger.e(TAG + ": Didn't find any matching OnDevicePersonalizationConfigService.");
        return null;
    }
}
