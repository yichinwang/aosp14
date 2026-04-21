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

package com.android.tests.sdksandbox.host;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.hosttestutils.DeviceSupportHostUtils;

import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class BroadcastRestrictionsHostTest extends BaseHostJUnit4Test {
    private static final String TEST_APP_RESTRICTIONS_PACKAGE = "com.android.tests.sdksandbox";
    private static final String TEST_APP_BROADCAST_RESTRICTIONS_APK =
            "BroadcastRestrictionsTestApp.apk";
    private static final String TEST_APP_BROADCAST_RESTRICTIONS_PRE_U_APK =
            "BroadcastRestrictionsPreUTestApp.apk";
    private static final String TEST_BROADCAST_RESTRICTIONS_CLASS_NAME =
            TEST_APP_RESTRICTIONS_PACKAGE + ".BroadcastRestrictionsTestApp";
    private static final String TEST_BROADCAST_RESTRICTIONS_PRE_U_CLASS_NAME =
            TEST_APP_RESTRICTIONS_PACKAGE + ".BroadcastRestrictionsPreUTestApp";

    private DeviceSdkLevel mDeviceSdkLevel;

    private final DeviceSupportHostUtils mDeviceSupportUtils = new DeviceSupportHostUtils(this);

    /**
     * Runs the given phase of a test by calling into the device. Throws an exception if the test
     * phase fails.
     *
     * <p>For example, <code>runPhase("testExample");</code>
     */
    private void runPhase(String testClassName, String phase) throws Exception {
        assertThat(runDeviceTests(TEST_APP_RESTRICTIONS_PACKAGE, testClassName, phase)).isTrue();
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue("Device supports SdkSandbox", mDeviceSupportUtils.isSdkSandboxSupported());
        mDeviceSdkLevel = new DeviceSdkLevel(getDevice());

        if (mDeviceSdkLevel.isDeviceAtLeastU()) {
            installPackage(TEST_APP_BROADCAST_RESTRICTIONS_APK);
        } else {
            installPackage(TEST_APP_BROADCAST_RESTRICTIONS_PRE_U_APK);
        }
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(TEST_APP_RESTRICTIONS_PACKAGE);
    }

    @Test
    public void testRegisterBroadcastReceiver_defaultValueRestrictionsApplied() throws Exception {
        if (mDeviceSdkLevel.isDeviceAtLeastU()) {
            runPhase(
                    TEST_BROADCAST_RESTRICTIONS_CLASS_NAME,
                    "testRegisterBroadcastReceiver_defaultValueRestrictionsApplied");
        } else {
            runPhase(
                    TEST_BROADCAST_RESTRICTIONS_PRE_U_CLASS_NAME,
                    "testRegisterBroadcastReceiver_defaultValueRestrictionsApplied");
        }
    }

    @Test
    public void testRegisterBroadcastReceiver_restrictionsApplied() throws Exception {
        if (mDeviceSdkLevel.isDeviceAtLeastU()) {
            runPhase(
                    TEST_BROADCAST_RESTRICTIONS_CLASS_NAME,
                    "testRegisterBroadcastReceiver_restrictionsApplied");
        } else {
            runPhase(
                    TEST_BROADCAST_RESTRICTIONS_PRE_U_CLASS_NAME,
                    "testRegisterBroadcastReceiver_restrictionsApplied");
        }
    }

    @Test
    public void testRegisterBroadcastReceiver_restrictionsNotApplied() throws Exception {
        if (mDeviceSdkLevel.isDeviceAtLeastU()) {
            runPhase(
                    TEST_BROADCAST_RESTRICTIONS_CLASS_NAME,
                    "testRegisterBroadcastReceiver_restrictionsNotApplied");
        } else {
            runPhase(
                    TEST_BROADCAST_RESTRICTIONS_PRE_U_CLASS_NAME,
                    "testRegisterBroadcastReceiver_restrictionsNotApplied");
        }
    }

    @Test
    public void testRegisterBroadcastReceiver_intentFilterWithoutAction() throws Exception {
        if (mDeviceSdkLevel.isDeviceAtLeastU()) {
            runPhase(
                    TEST_BROADCAST_RESTRICTIONS_CLASS_NAME,
                    "testRegisterBroadcastReceiver_intentFilterWithoutAction");
        } else {
            runPhase(
                    TEST_BROADCAST_RESTRICTIONS_PRE_U_CLASS_NAME,
                    "testRegisterBroadcastReceiver_intentFilterWithoutAction");
        }
    }

    @Test
    public void testRegisterBroadcastReceiver_DeviceConfigEmptyAllowlistApplied() throws Exception {
        if (mDeviceSdkLevel.isDeviceAtLeastU()) {
            runPhase(
                    TEST_BROADCAST_RESTRICTIONS_CLASS_NAME,
                    "testRegisterBroadcastReceiver_DeviceConfigEmptyAllowlistApplied");
        }
    }

    @Test
    public void testRegisterBroadcastReceiver_DeviceConfigAllowlistApplied() throws Exception {
        if (mDeviceSdkLevel.isDeviceAtLeastU()) {
            runPhase(
                    TEST_BROADCAST_RESTRICTIONS_CLASS_NAME,
                    "testRegisterBroadcastReceiver_DeviceConfigAllowlistApplied");
        }
    }

    @Test
    public void testRegisterBroadcastReceiver_DeviceConfigNextAllowlistApplied() throws Exception {
        if (mDeviceSdkLevel.isDeviceAtLeastU()) {
            runPhase(
                    TEST_BROADCAST_RESTRICTIONS_CLASS_NAME,
                    "testRegisterBroadcastReceiver_DeviceConfigNextAllowlistApplied");
        }
    }

    @Test
    public void testRegisterBroadcastReceiver_DeviceConfigNextRestrictions_AllowlistNotSet()
            throws Exception {
        if (mDeviceSdkLevel.isDeviceAtLeastU()) {
            runPhase(
                    TEST_BROADCAST_RESTRICTIONS_CLASS_NAME,
                    "testRegisterBroadcastReceiver_DeviceConfigNextRestrictions_AllowlistNotSet");
        }
    }

    @Test
    public void testRegisterBroadcastReceiver_protectedBroadcast() throws Exception {
        if (mDeviceSdkLevel.isDeviceAtLeastU()) {
            runPhase(
                    TEST_BROADCAST_RESTRICTIONS_CLASS_NAME,
                    "testRegisterBroadcastReceiver_protectedBroadcast");
        }
    }
}
