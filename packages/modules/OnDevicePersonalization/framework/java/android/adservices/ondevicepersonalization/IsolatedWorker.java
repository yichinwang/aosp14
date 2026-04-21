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

package android.adservices.ondevicepersonalization;

import static android.adservices.ondevicepersonalization.Constants.KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;

import java.util.function.Consumer;

/**
 * Interface with methods that need to be implemented to handle requests from the OS to an {@link
 * IsolatedService}. The {@link IsolatedService} creates an instance of {@link IsolatedWorker} on
 * each request and calls one of the methods below, depending the type of the request. The {@link
 * IsolatedService} calls the method on a Binder thread and the {@link IsolatedWorker} should
 * offload long running operations to a worker thread. The consumer parameter of each method is used
 * to return results.
 */
@FlaggedApi(KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS)
public interface IsolatedWorker {

    /**
     * Handles a request from an app. This method is called when an app calls {@code
     * OnDevicePersonalizationManager#execute(ComponentName, PersistableBundle,
     * java.util.concurrent.Executor, OutcomeReceiver)} that refers to a named
     * {@link IsolatedService}.
     *
     * @param input Request Parameters from the calling app.
     * @param consumer Callback that receives the result {@link ExecuteOutput}. Should be called
     *     with <code>null</code> on an error. The error is propagated to the calling app as an
     *     {@link OnDevicePersonalizationException} with error code {@link
     *     OnDevicePersonalizationException#ERROR_ISOLATED_SERVICE_FAILED}. To avoid leaking private
     *     data to the calling app, more detailed error reporting is not available. If the {@link
     *     IsolatedService} needs to report error stats to its backend, it should populate {@link
     *     ExecuteOutput} with error data for logging, and rely on Federated Analytics to aggregate
     *     the error reports.
     *     <p>If this method throws a {@link RuntimeException}, that is also reported to
     *     calling apps as an {@link OnDevicePersonalizationException} with error code {@link
     *     OnDevicePersonalizationException#ERROR_ISOLATED_SERVICE_FAILED}.
     */
    default void onExecute(@NonNull ExecuteInput input, @NonNull Consumer<ExecuteOutput> consumer) {
        consumer.accept(null);
    }

    /**
     * Handles a completed download. The platform downloads content using the parameters defined in
     * the package manifest of the {@link IsolatedService}, calls this function after the download
     * is complete, and updates the REMOTE_DATA table from
     * {@link IsolatedService#getRemoteData(RequestToken)} with the result of this method.
     *
     * @param input Download handler parameters.
     * @param consumer Callback that receives the result. Should be called with <code>null</code> on
     *     an error. If called with <code>null</code>, no updates are made to the REMOTE_DATA table.
     *     <p>If this method throws a {@link RuntimeException}, no updates are made to the
     *     REMOTE_DATA table.
     */
    default void onDownloadCompleted(
            @NonNull DownloadCompletedInput input,
            @NonNull Consumer<DownloadCompletedOutput> consumer) {
        consumer.accept(null);
    }

    /**
     * Generates HTML for the results that were returned as a result of
     * {@link #onExecute(ExecuteInput, Consumer)}. Called when a client app calls
     * {@link OnDevicePersonalizationManager#requestSurfacePackage(SurfacePackageToken, IBinder, int, int, int, java.util.concurrent.Executor, OutcomeReceiver)}.
     * The platform will render this HTML in an {@link android.webkit.WebView} inside a fenced
     * frame.
     *
     * @param input Parameters for the render request.
     * @param consumer Callback that receives the result. Should be called with <code>null</code> on
     *     an error. The error is propagated to the calling app as an {@link
     *     OnDevicePersonalizationException} with error code {@link
     *     OnDevicePersonalizationException#ERROR_ISOLATED_SERVICE_FAILED}.
     *     <p>If this method throws a {@link RuntimeException}, that is also reported to calling
     *     apps as an {@link OnDevicePersonalizationException} with error code {@link
     *     OnDevicePersonalizationException#ERROR_ISOLATED_SERVICE_FAILED}.
     */
    default void onRender(@NonNull RenderInput input, @NonNull Consumer<RenderOutput> consumer) {
        consumer.accept(null);
    }

    /**
     * Handles an event triggered by a request to a platform-provided tracking URL {@link
     * EventUrlProvider} that was embedded in the HTML output returned by
     * {@link #onRender(RenderInput, Consumer)}. The platform updates the EVENTS table with
     * {@link EventOutput#getEventLogRecord()}.
     *
     * @param input The parameters needed to compute event data.
     * @param consumer Callback that receives the result. Should be called with <code>null</code> on
     *     an error. If called with <code>null</code>, no data is written to the EVENTS table.
     *     <p>If this method throws a {@link RuntimeException}, no data is written to the EVENTS
     *     table.
     */
    default void onEvent(
            @NonNull EventInput input, @NonNull Consumer<EventOutput> consumer) {
        consumer.accept(null);
    }

    /**
     * Generate a list of training examples used for federated compute job. The platform will call
     * this function when a federated compute job starts. The federated compute job is scheduled by
     * an app through {@link FederatedComputeScheduler#schedule}.
     *
     * @param input The parameters needed to generate the training example.
     * @param consumer Callback that receives the result. Should be called with <code>null</code> on
     *     an error. If called with <code>null</code>, no training examples is produced for this
     *     training session.
     */
    default void onTrainingExamples(
            @NonNull TrainingExamplesInput input,
            @NonNull Consumer<TrainingExamplesOutput> consumer) {
        consumer.accept(null);
    }
}
