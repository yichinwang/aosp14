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

package com.android.tests.sdksandbox.endtoend;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.sdksandbox.cts.provider.mediationtest.IMediationTestSdkApi;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class SdkSandboxMediationTest extends SandboxKillerBeforeTest {

    private static final String MEDIATOR_SDK_NAME =
            "com.android.sdksandbox.cts.provider.mediationtest";
    private static final String MEDIATEE_SDK_NAME = "com.android.ctssdkprovider";
    private static final String APP_OWNED_SDK_SANDBOX_INTERFACE_NAME =
            "com.android.ctsappownedsdksandboxinterface";

    private static final String APP_OWNED_SDK_SANDBOX_INTERFACE_NAME_2 =
            "com.android.ctsappownedsdksandboxinterface2";

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Rule(order = 1)
    public final ActivityScenarioRule activityScenarioRule =
            new ActivityScenarioRule<>(TestActivity.class);

    private Context mContext;
    private SdkSandboxManager mSdkSandboxManager;
    private IMediationTestSdkApi mSdk;

    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();
        mSdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();
        activityScenarioRule.getScenario();

        // unload SDK to fix flakiness
        mSdkSandboxManager.unloadSdk(MEDIATOR_SDK_NAME);
        mSdkSandboxManager.unloadSdk(MEDIATEE_SDK_NAME);
        mSdkSandboxManager.unregisterAppOwnedSdkSandboxInterface(
                APP_OWNED_SDK_SANDBOX_INTERFACE_NAME);
        mSdkSandboxManager.unregisterAppOwnedSdkSandboxInterface(
                APP_OWNED_SDK_SANDBOX_INTERFACE_NAME_2);
    }

    @After
    public void tearDown() {
        // unload SDK to fix flakiness
        if (mSdkSandboxManager != null) {
            mSdkSandboxManager.unloadSdk(MEDIATOR_SDK_NAME);
            mSdkSandboxManager.unloadSdk(MEDIATEE_SDK_NAME);
            mSdkSandboxManager.unregisterAppOwnedSdkSandboxInterface(
                    APP_OWNED_SDK_SANDBOX_INTERFACE_NAME);
            mSdkSandboxManager.unregisterAppOwnedSdkSandboxInterface(
                    APP_OWNED_SDK_SANDBOX_INTERFACE_NAME_2);
        }
    }

    @Test
    public void testGetAppOwnedSdkSandboxInterfaces() throws Exception {
        loadMediatorSdkAndPopulateInterface();
        IBinder iBinder = new Binder();
        mSdkSandboxManager.registerAppOwnedSdkSandboxInterface(
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ iBinder));

        final List<AppOwnedSdkSandboxInterface> appOwnedSdkSandboxInterfaceList =
                mSdk.getAppOwnedSdkSandboxInterfaces();

        assertThat(appOwnedSdkSandboxInterfaceList).hasSize(1);

        assertThat(appOwnedSdkSandboxInterfaceList.get(0).getName())
                .isEqualTo(APP_OWNED_SDK_SANDBOX_INTERFACE_NAME);
        assertThat(appOwnedSdkSandboxInterfaceList.get(0).getVersion()).isEqualTo(0);
        assertThat(appOwnedSdkSandboxInterfaceList.get(0).getInterface()).isEqualTo(iBinder);
    }

    @Test
    public void testGetAppOwnedSdkSandboxInterfaces_NoInterface() throws Exception {
        loadMediatorSdkAndPopulateInterface();
        assertThat(mSdk.getAppOwnedSdkSandboxInterfaces()).hasSize(0);
    }

    @Test
    public void testGetAppOwnedSdkSandboxInterfaces_MultipleInterfaces() throws Exception {
        loadMediatorSdkAndPopulateInterface();
        IBinder iBinder = new Binder();
        mSdkSandboxManager.registerAppOwnedSdkSandboxInterface(
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ iBinder));

        IBinder iBinder2 = new Binder();
        mSdkSandboxManager.registerAppOwnedSdkSandboxInterface(
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME_2,
                        /*version=*/ 1,
                        /*interfaceIBinder=*/ iBinder2));
        final List<AppOwnedSdkSandboxInterface> appOwnedSdkSandboxInterfaceList =
                mSdk.getAppOwnedSdkSandboxInterfaces();

        assertThat(appOwnedSdkSandboxInterfaceList).hasSize(2);

        assertThat(
                        Arrays.asList(
                                appOwnedSdkSandboxInterfaceList.get(0).getName(),
                                appOwnedSdkSandboxInterfaceList.get(1).getName()))
                .containsExactly(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME_2);
        assertThat(
                        Arrays.asList(
                                appOwnedSdkSandboxInterfaceList.get(0).getVersion(),
                                appOwnedSdkSandboxInterfaceList.get(1).getVersion()))
                .containsExactly((long) 0, (long) 1);
        assertThat(
                        Arrays.asList(
                                appOwnedSdkSandboxInterfaceList.get(0).getInterface(),
                                appOwnedSdkSandboxInterfaceList.get(1).getInterface()))
                .containsExactly(iBinder, iBinder2);
    }

    @Test
    public void testGetSandboxedSdk_GetsAllSdksLoadedInTheSandbox() throws Exception {
        loadMediatorSdkAndPopulateInterface();

        final List<SandboxedSdk> sandboxedSdks = mSdk.getSandboxedSdks();
        assertThat(sandboxedSdks).hasSize(1);
        assertThat(sandboxedSdks.get(0).getInterface().getInterfaceDescriptor())
                .isEqualTo(
                        "com.android.sdksandbox.cts.provider.mediationtest"
                                + ".IMediationTestSdkApi");
    }

    @Test
    public void testGetSandboxedSdk_MultipleSdks() throws Exception {
        loadMediatorSdkAndPopulateInterface();
        loadMediateeSdk();

        final List<SandboxedSdk> sandboxedSdks = mSdk.getSandboxedSdks();
        assertThat(sandboxedSdks).hasSize(2);
        Set<String> interfaceDescriptors =
                sandboxedSdks.stream()
                        .map(
                                s -> {
                                    try {
                                        return s.getInterface().getInterfaceDescriptor();
                                    } catch (RemoteException e) {
                                        // Pass through exception
                                    }
                                    return null;
                                })
                        .collect(Collectors.toSet());

        assertThat(interfaceDescriptors)
                .containsExactly(
                        "com.android.ctssdkprovider.ICtsSdkProviderApi",
                        "com.android.sdksandbox.cts.provider.mediationtest.IMediationTestSdkApi");
    }

    @Test
    public void testLoadSdkByOtherSdk() throws Exception {
        loadMediatorSdkAndPopulateInterface();
        mSdk.loadSdkBySdk(MEDIATEE_SDK_NAME);

        final List<SandboxedSdk> sandboxedSdks = mSdk.getSandboxedSdks();
        assertThat(sandboxedSdks).hasSize(2);
        Set<String> loadedSdks =
                sandboxedSdks.stream()
                        .map(
                                s -> {
                                    return s.getSharedLibraryInfo().getName();
                                })
                        .collect(Collectors.toSet());

        assertThat(loadedSdks).contains(MEDIATEE_SDK_NAME);
    }

    @Test
    public void testLoadSdkBySameSdk() throws Exception {
        loadMediatorSdkAndPopulateInterface();
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> mSdk.loadSdkBySdk(MEDIATOR_SDK_NAME));
        assertThat(exception.getMessage())
                .isEqualTo(
                        "java.lang.AssertionError: Load SDK was not successful. errorCode: 101,"
                                + " errorMsg: "
                                + MEDIATOR_SDK_NAME
                                + " has been loaded already");
    }

    @Test
    public void testLoadSdkThatDoesNotExist() throws Exception {
        loadMediatorSdkAndPopulateInterface();
        final String nonExistingSdk = "non-existing-sdk";
        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> mSdk.loadSdkBySdk(nonExistingSdk));
        assertThat(exception.getMessage())
                .isEqualTo(
                        "java.lang.AssertionError: "
                                + "Load SDK was not successful. "
                                + "errorCode: 100, "
                                + "errorMsg: "
                                + nonExistingSdk
                                + " not found for loading");
    }

    private void loadMediatorSdkAndPopulateInterface() {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(MEDIATOR_SDK_NAME, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        // Store the returned SDK interface so that we can interact with it later.
        mSdk = IMediationTestSdkApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
    }

    private void loadMediateeSdk() {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(MEDIATEE_SDK_NAME, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
    }
}
