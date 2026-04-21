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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adid.AdId;
import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.common.UpdateAdIdRequest;
import android.content.Context;
import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.OutcomeReceiverForTests;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.common.SdkLevelSupportRule;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// TODO(b/302610220): Migrate the test to use a test helper class for AdservicesOutcomeReceiver.
@RunWith(JUnit4.class)
public class AdServicesCommonManagerTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private final AdServicesCommonManager mCommonManager = AdServicesCommonManager.get(sContext);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAnyLevel();

    // Skip the test if it runs on unsupported platforms.
    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    // Sets flags used in the test (and automatically reset them at the end)
    @Rule(order = 2)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forCommonManagerE2ETests(sContext.getPackageName());

    @Test
    @RequiresSdkLevelAtLeastS(reason = "uses OutcomeReceiver, which is only available on S+.")
    public void testStatusManagerNotAuthorizedOnSPlus() {
        flags.setAdserviceEnableStatus(false);

        // At beginning, Sdk1 receives a false status.
        ListenableFuture<Boolean> adServicesStatusResponse = getAdservicesStatus();

        Exception adServicesStatusResponseException =
                assertThrows(
                        ExecutionException.class,
                        () -> adServicesStatusResponse.get(1, TimeUnit.SECONDS));
        assertThat(adServicesStatusResponseException.getCause())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testSetStatusEnabledNotExecutedOnSPlus() {
        mCommonManager.setAdServicesEnabled(true, true);

        ListenableFuture<Boolean> adServicesStatusResponse = getAdservicesStatus();

        Exception adServicesStatusResponseException =
                assertThrows(
                        ExecutionException.class,
                        () -> adServicesStatusResponse.get(1, TimeUnit.SECONDS));
        assertThat(adServicesStatusResponseException.getCause())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    public void testStatusManagerNotAuthorizedCompat() {
        flags.setAdserviceEnableStatus(false);

        // At beginning, Sdk1 receives a false status.
        ListenableFuture<Boolean> adServicesStatusResponse = getAdservicesStatusCompat();

        Exception adServicesStatusResponseException =
                assertThrows(
                        ExecutionException.class,
                        () -> adServicesStatusResponse.get(1, TimeUnit.SECONDS));
        assertThat(adServicesStatusResponseException.getCause())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    public void testSetStatusEnabledNotExecutedCompat() {
        mCommonManager.setAdServicesEnabled(true, true);

        ListenableFuture<Boolean> adServicesStatusResponse = getAdservicesStatusCompat();

        Exception adServicesStatusResponseException =
                assertThrows(
                        ExecutionException.class,
                        () -> adServicesStatusResponse.get(1, TimeUnit.SECONDS));
        assertThat(adServicesStatusResponseException.getCause())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "uses OutcomeReceiver, which is only available on T")
    public void tesUpdateAdIdCache_notAuthorized_sPlus() throws Exception {
        flags.setUpdateAdIdCacheEnabled(true);

        UpdateAdIdRequest request = new UpdateAdIdRequest.Builder(AdId.ZERO_OUT).build();

        OutcomeReceiverForTests<Boolean> updateAdIdCallback = new OutcomeReceiverForTests<>();
        mCommonManager.updateAdId(request, CALLBACK_EXECUTOR, updateAdIdCallback);

        updateAdIdCallback.assertFailure(SecurityException.class);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "uses OutcomeReceiver, which is only available on T")
    public void tesUpdateAdIdCache_notEnabled_sPlus() throws Exception {
        flags.setUpdateAdIdCacheEnabled(false);

        UpdateAdIdRequest request = new UpdateAdIdRequest.Builder(AdId.ZERO_OUT).build();

        OutcomeReceiverForTests<Boolean> updateAdIdCallback = new OutcomeReceiverForTests<>();
        mCommonManager.updateAdId(request, CALLBACK_EXECUTOR, updateAdIdCallback);

        updateAdIdCallback.assertFailure(IllegalStateException.class);
    }

    @Test
    public void tesUpdateAdIdCache_notAuthorized_rPlus() {
        flags.setUpdateAdIdCacheEnabled(true);

        ListenableFuture<Boolean> updateAdIdCacheResponse = updateAdIdCacheRMinus();

        Exception updateAdIdResponseException =
                assertThrows(
                        ExecutionException.class,
                        () -> updateAdIdCacheResponse.get(1, TimeUnit.SECONDS));
        assertThat(updateAdIdResponseException)
                .hasCauseThat()
                .isInstanceOf(SecurityException.class);
    }

    @Test
    public void tesUpdateAdIdCache_notEnabled_rPlus() {
        flags.setUpdateAdIdCacheEnabled(false);

        ListenableFuture<Boolean> updateAdIdCacheResponse = updateAdIdCacheRMinus();

        Exception updateAdIdResponseException =
                assertThrows(
                        ExecutionException.class,
                        () -> updateAdIdCacheResponse.get(1, TimeUnit.SECONDS));
        assertThat(updateAdIdResponseException)
                .hasCauseThat()
                .isInstanceOf(IllegalStateException.class);
    }

    private ListenableFuture<Boolean> getAdservicesStatus() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mCommonManager.isAdServicesEnabled(
                            CALLBACK_EXECUTOR,
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Boolean result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "getStatus";
                });
    }

    private ListenableFuture<Boolean> getAdservicesStatusCompat() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mCommonManager.isAdServicesEnabled(
                            CALLBACK_EXECUTOR,
                            new AdServicesOutcomeReceiver<>() {
                                @Override
                                public void onResult(Boolean result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "getStatus";
                });
    }

    private ListenableFuture<Boolean> updateAdIdCacheRMinus() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mCommonManager.updateAdId(
                            new UpdateAdIdRequest.Builder(AdId.ZERO_OUT).build(),
                            CALLBACK_EXECUTOR,
                            new AdServicesOutcomeReceiver<>() {
                                @Override
                                public void onResult(Boolean result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "getStatus";
                });
    }
}
