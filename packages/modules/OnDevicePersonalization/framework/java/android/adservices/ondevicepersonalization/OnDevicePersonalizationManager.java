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

import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.annotation.CallbackExecutor;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.SurfaceControlViewHost;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.modules.utils.build.SdkLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

// TODO(b/289102463): Add a link to the public ODP developer documentation.
/**
 * OnDevicePersonalizationManager provides APIs for apps to load an
 * {@link IsolatedService} in an isolated process and interact with it.
 *
 * An app can request an {@link IsolatedService} to generate content for display
 * within an {@link android.view.SurfaceView} within the app's view hierarchy, and also write
 * persistent results to on-device storage which can be consumed by Federated Analytics for
 * cross-device statistical analysis or by Federated Learning for model training. The displayed
 * content and the persistent output are both not directly accessible by the calling app.
 */
@FlaggedApi(KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS)
public class OnDevicePersonalizationManager {
    /** @hide */
    public static final String ON_DEVICE_PERSONALIZATION_SERVICE =
            "on_device_personalization_service";
    private static final String INTENT_FILTER_ACTION = "android.OnDevicePersonalizationService";
    private static final String ODP_MANAGING_SERVICE_PACKAGE_SUFFIX =
            "com.android.ondevicepersonalization.services";

    private static final String ALT_ODP_MANAGING_SERVICE_PACKAGE_SUFFIX =
            "com.google.android.ondevicepersonalization.services";

    private final AbstractServiceBinder<IOnDevicePersonalizationManagingService> mServiceBinder;
    private final Context mContext;

    /** @hide */
    public OnDevicePersonalizationManager(Context context) {
        mContext = context;
        this.mServiceBinder =
                AbstractServiceBinder.getServiceBinderByIntent(
                        context,
                        INTENT_FILTER_ACTION,
                        List.of(
                                ODP_MANAGING_SERVICE_PACKAGE_SUFFIX,
                                ALT_ODP_MANAGING_SERVICE_PACKAGE_SUFFIX),
                        SdkLevel.isAtLeastU() ? Context.BIND_ALLOW_ACTIVITY_STARTS : 0,
                        IOnDevicePersonalizationManagingService.Stub::asInterface);
    }

    /**
     * Executes an {@link IsolatedService} in the OnDevicePersonalization sandbox. The
     * platform binds to the specified {@link IsolatedService} in an isolated process
     * and calls {@link IsolatedWorker#onExecute(ExecuteInput, java.util.function.Consumer)}
     * with the caller-provided parameters. When the {@link IsolatedService} finishes execution,
     * the platform returns tokens that refer to the results from the service to the caller.
     * These tokens can be subsequently used to display results in a
     * {@link android.view.SurfaceView} within the calling app.
     *
     * @param handler The {@link ComponentName} of the {@link IsolatedService}.
     * @param params a {@link PersistableBundle} that is passed from the calling app to the
     *     {@link IsolatedService}. The expected contents of this parameter are defined
     *     by the{@link IsolatedService}. The platform does not interpret this parameter.
     * @param executor the {@link Executor} on which to invoke the callback.
     * @param receiver This returns a list of {@link SurfacePackageToken} objects, each of which is
     *     an opaque reference to a {@link RenderingConfig} returned by an
     *     {@link IsolatedService}, or an {@link Exception} on failure. The returned
     *     {@link SurfacePackageToken} objects can be used in a subsequent
     *     {@link #requestSurfacePackage(SurfacePackageToken, IBinder, int, int, int, Executor,
     *     OutcomeReceiver)} call to display the result in a view. The calling app and
     *     the {@link IsolatedService} must agree on the expected size of this list.
     *     An entry in the returned list of {@link SurfacePackageToken} objects may be null to
     *     indicate that the service has no output for that specific surface.
     *
     *     In case of an error, the receiver returns one of the following exceptions:
     *     Returns a {@link android.content.pm.PackageManager.NameNotFoundException} if the handler
     *     package is not installed or does not have a valid ODP manifest.
     *     Returns {@link ClassNotFoundException} if the handler class is not found.
     *     Returns an {@link OnDevicePersonalizationException} if execution of the handler fails.
     */
    public void execute(
            @NonNull ComponentName handler,
            @NonNull PersistableBundle params,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<List<SurfacePackageToken>, Exception> receiver
    ) {
        Objects.requireNonNull(handler);
        Objects.requireNonNull(params);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);
        long startTimeMillis = SystemClock.elapsedRealtime();

        try {
            final IOnDevicePersonalizationManagingService service =
                    mServiceBinder.getService(executor);

            IExecuteCallback callbackWrapper = new IExecuteCallback.Stub() {
                @Override
                public void onSuccess(
                        @NonNull List<String> tokenStrings) {
                    executor.execute(() -> {
                        try {
                            ArrayList<SurfacePackageToken> tokens =
                                    new ArrayList<>(tokenStrings.size());
                            for (String tokenString : tokenStrings) {
                                if (tokenString == null) {
                                    tokens.add(null);
                                } else {
                                    tokens.add(new SurfacePackageToken(tokenString));
                                }
                            }
                            receiver.onResult(tokens);
                        } catch (Exception e) {
                            receiver.onError(e);
                        }
                    });
                }

                @Override
                public void onError(int errorCode) {
                    executor.execute(() -> receiver.onError(createException(errorCode)));
                }
            };

            service.execute(
                    mContext.getPackageName(),
                    handler,
                    params,
                    new CallerMetadata.Builder().setStartTimeMillis(startTimeMillis).build(),
                    callbackWrapper);

        } catch (RemoteException e) {
            receiver.onError(new IllegalStateException(e));
        }
    }

    /**
     * Requests a {@link android.view.SurfaceControlViewHost.SurfacePackage} to be inserted into a
     * {@link android.view.SurfaceView} inside the calling app. The surface package will contain an
     * {@link android.view.View} with the content from a result of a prior call to
     * {@code #execute(ComponentName, PersistableBundle, Executor, OutcomeReceiver)} running in
     * the OnDevicePersonalization sandbox.
     *
     * @param surfacePackageToken a reference to a {@link SurfacePackageToken} returned by a prior
     *     call to {@code #execute(ComponentName, PersistableBundle, Executor, OutcomeReceiver)}.
     * @param surfaceViewHostToken the hostToken of the {@link android.view.SurfaceView}, which is
     *     returned by {@link android.view.SurfaceView#getHostToken()} after the
     *     {@link android.view.SurfaceView} has been added to the view hierarchy.
     * @param displayId the integer ID of the logical display on which to display the
     *     {@link android.view.SurfaceControlViewHost.SurfacePackage}, returned by
     *     {@code Context.getDisplay().getDisplayId()}.
     * @param width the width of the {@link android.view.SurfaceControlViewHost.SurfacePackage}
     *     in pixels.
     * @param height the height of the {@link android.view.SurfaceControlViewHost.SurfacePackage}
     *     in pixels.
     * @param executor the {@link Executor} on which to invoke the callback
     * @param receiver This either returns a
     *     {@link android.view.SurfaceControlViewHost.SurfacePackage} on success, or
     *     {@link Exception} on failure. The exception type is
     *     {@link OnDevicePersonalizationException} if execution of the handler fails.
     */
    public void requestSurfacePackage(
            @NonNull SurfacePackageToken surfacePackageToken,
            @NonNull IBinder surfaceViewHostToken,
            int displayId,
            int width,
            int height,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<SurfaceControlViewHost.SurfacePackage, Exception> receiver
    ) {
        Objects.requireNonNull(surfacePackageToken);
        Objects.requireNonNull(surfaceViewHostToken);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);
        long startTimeMillis = SystemClock.elapsedRealtime();

        try {
            final IOnDevicePersonalizationManagingService service =
                    mServiceBinder.getService(executor);

            IRequestSurfacePackageCallback callbackWrapper =
                    new IRequestSurfacePackageCallback.Stub() {
                        @Override
                        public void onSuccess(
                                @NonNull SurfaceControlViewHost.SurfacePackage surfacePackage) {
                            executor.execute(() -> {
                                receiver.onResult(surfacePackage);
                            });
                        }

                        @Override
                        public void onError(int errorCode) {
                            executor.execute(() -> receiver.onError(createException(errorCode)));
                        }
                    };

            service.requestSurfacePackage(
                    surfacePackageToken.getTokenString(),
                    surfaceViewHostToken,
                    displayId,
                    width,
                    height,
                    new CallerMetadata.Builder().setStartTimeMillis(startTimeMillis).build(),
                    callbackWrapper);

        } catch (RemoteException e) {
            receiver.onError(new IllegalStateException(e));
        }
    }

    private Exception createException(int errorCode) {
        if (errorCode == Constants.STATUS_NAME_NOT_FOUND) {
            return new PackageManager.NameNotFoundException();
        } else if (errorCode == Constants.STATUS_CLASS_NOT_FOUND) {
            return new ClassNotFoundException();
        } else if (errorCode == Constants.STATUS_SERVICE_FAILED) {
            return new OnDevicePersonalizationException(
                    OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_FAILED);
        } else if (errorCode == Constants.STATUS_PERSONALIZATION_DISABLED) {
            return new OnDevicePersonalizationException(
                    OnDevicePersonalizationException.ERROR_PERSONALIZATION_DISABLED);
        } else {
            return new IllegalStateException("Error: " + errorCode);
        }
    }
}
