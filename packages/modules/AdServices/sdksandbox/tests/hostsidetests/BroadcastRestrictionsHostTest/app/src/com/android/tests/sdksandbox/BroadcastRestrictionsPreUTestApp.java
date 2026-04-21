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

package com.android.tests.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.ConfigListener;
import android.app.sdksandbox.testutils.DeviceConfigUtils;
import android.app.sdksandbox.testutils.EmptyActivity;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.tests.sdkprovider.restrictions.broadcasts.IBroadcastSdkApi;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public class BroadcastRestrictionsPreUTestApp {
    private SdkSandboxManager mSdkSandboxManager;
    private static final String PROPERTY_ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS =
            "enforce_broadcast_receiver_restrictions";
    private static final String SDK_PACKAGE =
            "com.android.tests.sdkprovider.restrictions.broadcasts";

    private String mEnforceBroadcastRestrictions;
    private static final List<String> INTENT_ACTIONS =
            new ArrayList<>(Arrays.asList(Intent.ACTION_SEND, Intent.ACTION_VIEW));
    private static final String NAMESPACE = DeviceConfig.NAMESPACE_SDK_SANDBOX;
    private IBroadcastSdkApi mBroadcastSdkApi;
    private ConfigListener mConfigListener;
    private DeviceConfigUtils mDeviceConfigUtils;

    /** This rule is defined to start an activity in the foreground to call the sandbox APIs */
    @Rule public final ActivityScenarioRule mRule = new ActivityScenarioRule<>(EmptyActivity.class);

    @Before
    public void setup() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.READ_DEVICE_CONFIG);

        mConfigListener = new ConfigListener();
        DeviceConfig.addOnPropertiesChangedListener(
                NAMESPACE, context.getMainExecutor(), mConfigListener);
        mDeviceConfigUtils = new DeviceConfigUtils(mConfigListener, NAMESPACE);

        mEnforceBroadcastRestrictions =
                DeviceConfig.getProperty(
                        NAMESPACE, PROPERTY_ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS);

        mDeviceConfigUtils.deleteProperty(PROPERTY_ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS);

        mRule.getScenario();
    }

    @After
    public void tearDown() throws Exception {
        mDeviceConfigUtils.resetToInitialValue(
                PROPERTY_ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS, mEnforceBroadcastRestrictions);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();

        DeviceConfig.removeOnPropertiesChangedListener(mConfigListener);

        // Greedily unload SDK to reduce flakiness
        mSdkSandboxManager.unloadSdk(SDK_PACKAGE);
    }

    @Test
    public void testRegisterBroadcastReceiver_defaultValueRestrictionsApplied() throws Exception {
        loadSdk();
        mBroadcastSdkApi.registerBroadcastReceiver(INTENT_ACTIONS);
    }

    @Test
    public void testRegisterBroadcastReceiver_restrictionsApplied() throws Exception {
        mDeviceConfigUtils.setProperty(PROPERTY_ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS, "true");
        loadSdk();

        final SecurityException thrown =
                assertThrows(
                        SecurityException.class,
                        () -> mBroadcastSdkApi.registerBroadcastReceiver(INTENT_ACTIONS));

        assertThat(thrown)
                .hasMessageThat()
                .contains("SDK sandbox process not allowed to call registerReceiver");
    }

    /**
     * Tests that a SecurityException is not thrown when SDK sandbox process tries to register a
     * broadcast receiver. This behavior depends on the value of a {@link DeviceConfig} property.
     */
    @Test(expected = Test.None.class /* no exception expected */)
    public void testRegisterBroadcastReceiver_restrictionsNotApplied() throws Exception {
        mDeviceConfigUtils.setProperty(PROPERTY_ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS, "false");
        loadSdk();
        mBroadcastSdkApi.registerBroadcastReceiver(INTENT_ACTIONS);
    }

    /**
     * Tests that broadcast receiver is registered successfully from SDK sandbox process with no
     * action mentioned in the {@link android.content.IntentFilter} object.
     */
    @Test
    public void testRegisterBroadcastReceiver_intentFilterWithoutAction() throws Exception {
        loadSdk();
        mBroadcastSdkApi.registerBroadcastReceiver(new ArrayList<>());
    }

    private void loadSdk() {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        mBroadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);
    }
}
