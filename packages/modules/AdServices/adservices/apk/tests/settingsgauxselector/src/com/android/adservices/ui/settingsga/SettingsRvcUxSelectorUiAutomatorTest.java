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
package com.android.adservices.ui.settingsga;

import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DEBUG_UX;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_RVC_NOTIFICATION_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_RVC_UX_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Build;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsRvcUxSelectorUiAutomatorTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final int LAUNCH_TIMEOUT = 5000;
    public static final int PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT = 1000;
    private static UiDevice sDevice;
    private String mTestName;

    @Rule(order = 0)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Rule(order = 1)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setFlag(KEY_ENABLE_AD_SERVICES_SYSTEM_API, true)
                    .setFlag(KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE, true)
                    .setFlag(KEY_RVC_UX_ENABLED, true)
                    .setFlag(KEY_RVC_NOTIFICATION_ENABLED, true)
                    .setFlag(KEY_GA_UX_FEATURE_ENABLED, true)
                    .setFlag(KEY_DEBUG_UX, "RVC_UX")
                    .setCompatModeFlags();

    @Before
    public void setup() {
        // This test is only enabled on R.
        Assume.assumeTrue(SdkLevel.isAtLeastR() && !SdkLevel.isAtLeastS());
        Assume.assumeTrue(ApkTestUtil.isDeviceSupported());

        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen
        sDevice.pressHome();

        // Wait for launcher
        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);
    }

    @After
    public void teardown() {
        if (!ApkTestUtil.isDeviceSupported()) return;

        ApkTestUtil.takeScreenshot(sDevice, getClass().getSimpleName() + "_" + mTestName + "_");

        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @Test
    public void settingsRemoveMainToggleAndMeasurementEntryTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);

        UiObject appButton =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_measurement_view_title);
        appButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(appButton.exists()).isTrue();

        // make sure we are on the main settings page
        ApkTestUtil.click(sDevice, R.string.settingsUI_measurement_view_title);

        // verify have entered to measurement page
        UiObject measurementSwitch =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_measurement_switch_title);
        // needed as the new page is displayed (this can take time to propagate to the UiAutomator)
        measurementSwitch.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(measurementSwitch.exists()).isTrue();

        sDevice.pressBack();
        // verify back to the main page
        appButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(appButton.exists()).isTrue();
    }

    @Test
    public void measurementDialogTest() throws UiObjectNotFoundException, RemoteException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");
        sDevice.setOrientationNatural();
        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);
        // open measurement view
        ApkTestUtil.click(sDevice, R.string.settingsUI_measurement_view_title);

        // R Msmt UI is not scrollable
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            // click reset
            ApkTestUtil.click(sDevice, R.string.settingsUI_measurement_view_reset_title);
            UiObject resetButton =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_measurement_view_reset_title);
            resetButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
            assertThat(resetButton.exists()).isTrue();

            // click reset again
            ApkTestUtil.click(sDevice, R.string.settingsUI_measurement_view_reset_title);
            resetButton =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_measurement_view_reset_title);
            resetButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
            assertThat(resetButton.exists()).isTrue();
        } else {
            // click reset
            ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_measurement_view_reset_title);
            UiObject resetButton =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_measurement_view_reset_title);
            resetButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
            assertThat(resetButton.exists()).isTrue();

            // click reset again
            ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_measurement_view_reset_title);
            resetButton =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_measurement_view_reset_title);
            resetButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
            assertThat(resetButton.exists()).isTrue();
        }
    }

    @Test
    public void measurementToggleTest() throws UiObjectNotFoundException, RemoteException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand(
                "device_config put adservices ui_toggle_speed_bump_enabled false");

        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);
        // 1) disable Measurement API is enabled
        ApkTestUtil.click(sDevice, R.string.settingsUI_measurement_view_title);
        sDevice.waitForIdle();

        UiObject measurementToggle =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        measurementToggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        if (measurementToggle.isChecked()) {
            measurementToggle.click();
        }
        assertThat(measurementToggle.isChecked()).isFalse();
        sDevice.pressBack();

        // 2) enable Measurement API
        ApkTestUtil.click(sDevice, R.string.settingsUI_measurement_view_title);
        sDevice.waitForIdle();

        measurementToggle = sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        measurementToggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(measurementToggle.isChecked()).isFalse();
        measurementToggle.click();
        assertThat(measurementToggle.isChecked()).isTrue();
        sDevice.pressBack();

        // 3) check if Measurement API is enabled
        ApkTestUtil.click(sDevice, R.string.settingsUI_measurement_view_title);
        sDevice.waitForIdle();
        // rotate device to test rotating as well
        sDevice.setOrientationLeft();
        sDevice.setOrientationNatural();
        measurementToggle = sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        measurementToggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(measurementToggle.isChecked()).isTrue();
        sDevice.pressBack();
    }

    @Test
    public void measurementSubTitleTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        ShellUtils.runShellCommand(
                "device_config put adservices ui_toggle_speed_bump_enabled false");

        ApkTestUtil.launchSettingView(
                ApplicationProvider.getApplicationContext(), sDevice, LAUNCH_TIMEOUT);
        checkSubtitleMatchesToggle(
                ".*:id/measurement_preference_subtitle",
                R.string.settingsUI_measurement_view_title);
    }

    @Test
    public void dialogRotateTest() throws UiObjectNotFoundException, RemoteException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled false");
        ShellUtils.runShellCommand("device_config put adservices debug_ux BETA_UX");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");
        ShellUtils.runShellCommand("device_config put adservices ui_dialog_fragment_enabled true");

        sDevice.setOrientationNatural();
        ApkTestUtil.launchSettingView(
                ApplicationProvider.getApplicationContext(), sDevice, LAUNCH_TIMEOUT);

        UiObject consentSwitch = ApkTestUtil.getConsentSwitch(sDevice);
        assertThat(consentSwitch.exists()).isTrue();
        // turn it on if not
        if (!consentSwitch.isChecked()) {
            consentSwitch.click();
        }
        consentSwitch.click();
        UiObject dialogTitle =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_opt_out_title);

        assertThat(dialogTitle.exists()).isTrue();

        sDevice.setOrientationRight();
        assertThat(dialogTitle.exists()).isTrue();
        sDevice.setOrientationNatural();
    }

    @Test
    public void measurementToggleDialogTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand(
                "device_config put adservices ui_toggle_speed_bump_enabled true");
        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);

        ApkTestUtil.click(sDevice, R.string.settingsUI_measurement_ga_title);
        sDevice.waitForIdle();

        UiObject measurementToggle =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        measurementToggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        if (measurementToggle.isChecked()) {
            // turn it off
            measurementToggle.click();
            UiObject dialogOptOutTitle =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_measurement_opt_out_title);
            UiObject positiveButton =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_opt_out_positive_text);
            assertThat(dialogOptOutTitle.exists()).isTrue();
            positiveButton.click();
            assertThat(measurementToggle.isChecked()).isFalse();
            // then turn it on again
            measurementToggle.click();
            UiObject dialogOptInTitle =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_measurement_opt_in_title);
            UiObject okButton =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_acknowledge);
            assertThat(dialogOptInTitle.exists()).isTrue();
            okButton.click();
            assertThat(measurementToggle.isChecked()).isTrue();
        } else {
            // turn it on
            measurementToggle.click();
            UiObject dialogOptInTitle =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_measurement_opt_in_title);
            UiObject okButton =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_acknowledge);
            assertThat(dialogOptInTitle.exists()).isTrue();
            okButton.click();
            assertThat(measurementToggle.isChecked()).isTrue();
            // then turn it off
            measurementToggle.click();
            UiObject dialogOptOutTitle =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_measurement_opt_out_title);
            UiObject positiveButton =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_opt_out_positive_text);
            assertThat(dialogOptOutTitle.exists()).isTrue();
            positiveButton.click();
            assertThat(measurementToggle.isChecked()).isFalse();
        }
    }

    private void checkSubtitleMatchesToggle(String regexResId, int stringIdOfTitle)
            throws UiObjectNotFoundException {
        UiObject subtitle = sDevice.findObject(new UiSelector().resourceIdMatches(regexResId));
        subtitle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        if (subtitle.getText()
                .equals(ApkTestUtil.getString(R.string.settingsUI_subtitle_consent_off))) {
            ApkTestUtil.click(sDevice, stringIdOfTitle);
            UiObject toggle =
                    sDevice.findObject(new UiSelector().className("android.widget.Switch"));
            toggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
            assertThat(toggle.isChecked()).isFalse();
            toggle.click();
            sDevice.pressBack();
            toggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
            assertThat(
                            subtitle.getText()
                                    .equals(
                                            ApkTestUtil.getString(
                                                    R.string.settingsUI_subtitle_consent_off)))
                    .isFalse();
        } else {
            ApkTestUtil.click(sDevice, stringIdOfTitle);
            UiObject toggle =
                    sDevice.findObject(new UiSelector().className("android.widget.Switch"));
            toggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
            assertThat(toggle.isChecked()).isTrue();
            toggle.click();
            sDevice.pressBack();
            toggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
            assertThat(
                            subtitle.getText()
                                    .equals(
                                            ApkTestUtil.getString(
                                                    R.string.settingsUI_subtitle_consent_off)))
                    .isTrue();
        }
    }
}
