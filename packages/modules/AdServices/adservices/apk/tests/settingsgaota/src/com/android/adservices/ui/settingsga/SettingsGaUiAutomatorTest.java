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
package com.android.adservices.ui.settingsga;

import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;

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
import androidx.test.uiautomator.UiScrollable;
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
public class SettingsGaUiAutomatorTest {
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
                    .setCompatModeFlags()
                    .setFlag(KEY_ENABLE_AD_SERVICES_SYSTEM_API, false);

    @Before
    public void setup() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());
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
        ApkTestUtil.takeScreenshot(sDevice, getClass().getSimpleName() + "_" + mTestName + "_");

        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @Test
    public void mainPageGaUxFlagEnableToDisableFlipTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");

        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);
        // beta switch shouldn't exist
        UiObject mainSwitch =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        mainSwitch.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(mainSwitch.exists()).isFalse();

        // make sure all the GA elements are there
        scrollTo(R.string.settingsUI_topics_ga_title);
        UiObject topicsButton =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_topics_ga_title);
        topicsButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(topicsButton.exists()).isTrue();

        scrollTo(R.string.settingsUI_apps_ga_title);
        UiObject appButton = ApkTestUtil.getElement(sDevice, R.string.settingsUI_apps_ga_title);
        appButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(appButton.exists()).isTrue();

        scrollTo(R.string.settingsUI_measurement_view_title);
        UiObject measurementButton =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_measurement_view_title);
        appButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(measurementButton.exists()).isTrue();

        sDevice.pressHome();
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled false");

        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);
        // beta switch should exist
        mainSwitch = sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        mainSwitch.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(mainSwitch.exists()).isTrue();

        // make sure all the GA elements are gone
        topicsButton = ApkTestUtil.getElement(sDevice, R.string.settingsUI_topics_ga_title);
        topicsButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(topicsButton.exists()).isFalse();

        appButton = ApkTestUtil.getElement(sDevice, R.string.settingsUI_apps_ga_title);
        appButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(appButton.exists()).isFalse();

        measurementButton =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_measurement_view_title);
        measurementButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(measurementButton.exists()).isFalse();
    }

    @Test
    public void mainPageGaUxFlagDisableToEnableFlipTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled false");

        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);
        // beta switch should exist
        UiObject mainSwitch =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        mainSwitch.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(mainSwitch.exists()).isTrue();

        // make sure all the elements are there
        UiObject topicsButton =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_topics_ga_title);
        topicsButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(topicsButton.exists()).isFalse();

        UiObject appButton = ApkTestUtil.getElement(sDevice, R.string.settingsUI_apps_ga_title);
        appButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(appButton.exists()).isFalse();

        UiObject measurementButton =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_measurement_view_title);
        measurementButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(measurementButton.exists()).isFalse();

        sDevice.pressHome();
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");

        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);
        // beta switch shouldn't exist
        mainSwitch = sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        mainSwitch.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(mainSwitch.exists()).isFalse();

        // make sure all the elements are there
        scrollTo(R.string.settingsUI_topics_ga_title);
        topicsButton = ApkTestUtil.getElement(sDevice, R.string.settingsUI_topics_ga_title);
        topicsButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(topicsButton.exists()).isTrue();

        scrollTo(R.string.settingsUI_apps_ga_title);
        appButton = ApkTestUtil.getElement(sDevice, R.string.settingsUI_apps_ga_title);
        appButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(appButton.exists()).isTrue();

        scrollTo(R.string.settingsUI_measurement_view_title);
        measurementButton =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_measurement_view_title);
        appButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(measurementButton.exists()).isTrue();
    }

    @Test
    public void settingsRemoveMainToggleAndMeasurementEntryTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");

        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);

        // make sure we are on the main settings page
        UiObject appButton = ApkTestUtil.scrollTo(sDevice, R.string.settingsUI_apps_ga_title);
        appButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(appButton.exists()).isTrue();

        UiObject topicsButton = ApkTestUtil.scrollTo(sDevice, R.string.settingsUI_topics_ga_title);
        topicsButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(topicsButton.exists()).isTrue();

        // click measurement page
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_measurement_view_title);

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
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");
        sDevice.setOrientationNatural();
        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);
        // open measurement view
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_measurement_view_title);

        // click reset
        clickResetBtn();
        UiObject resetButton =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_measurement_view_reset_title);
        resetButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(resetButton.exists()).isTrue();

        // click reset again
        clickResetBtn();
        resetButton =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_measurement_view_reset_title);

        resetButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(resetButton.exists()).isTrue();
    }

    @Test
    public void topicsToggleTest() throws UiObjectNotFoundException, RemoteException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand(
                "device_config put adservices ui_toggle_speed_bump_enabled false");

        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);
        // 1) disable Topics API is enabled
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_topics_ga_title);
        sDevice.waitForIdle();

        UiObject topicsToggle =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        topicsToggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        if (topicsToggle.isChecked()) {
            topicsToggle.click();
        }
        assertThat(topicsToggle.isChecked()).isFalse();
        sDevice.pressBack();

        // 2) enable Topics API
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_topics_ga_title);
        sDevice.waitForIdle();

        topicsToggle = sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        topicsToggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(topicsToggle.isChecked()).isFalse();
        topicsToggle.click();
        assertThat(topicsToggle.isChecked()).isTrue();
        sDevice.pressBack();

        // 3) check if Topics API is enabled
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_topics_ga_title);
        sDevice.waitForIdle();
        // rotate device to test rotating as well
        sDevice.setOrientationLeft();
        sDevice.setOrientationNatural();
        topicsToggle = sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        topicsToggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(topicsToggle.isChecked()).isTrue();
        sDevice.pressBack();
    }

    @Test
    public void fledgeToggleTest() throws UiObjectNotFoundException, RemoteException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand(
                "device_config put adservices ui_toggle_speed_bump_enabled false");

        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);
        // 1) disable Fledge API is enabled
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_apps_ga_title);
        sDevice.waitForIdle();

        UiObject fledgeToggle =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        fledgeToggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        if (fledgeToggle.isChecked()) {
            fledgeToggle.click();
        }
        assertThat(fledgeToggle.isChecked()).isFalse();
        sDevice.pressBack();

        // 2) enable Fledge API
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_apps_ga_title);
        sDevice.waitForIdle();

        fledgeToggle = sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        fledgeToggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(fledgeToggle.isChecked()).isFalse();
        fledgeToggle.click();
        assertThat(fledgeToggle.isChecked()).isTrue();
        sDevice.pressBack();

        // 3) check if Fledge API is enabled
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_apps_ga_title);
        sDevice.waitForIdle();
        // rotate device to test rotating as well
        sDevice.setOrientationLeft();
        sDevice.setOrientationNatural();
        fledgeToggle = sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        fledgeToggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(fledgeToggle.isChecked()).isTrue();
        sDevice.pressBack();
    }

    @Test
    public void measurementToggleTest() throws UiObjectNotFoundException, RemoteException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand(
                "device_config put adservices ui_toggle_speed_bump_enabled false");

        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);
        // 1) disable Measurement API is enabled
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_measurement_view_title);
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
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_measurement_view_title);
        sDevice.waitForIdle();

        measurementToggle = sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        measurementToggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(measurementToggle.isChecked()).isFalse();
        measurementToggle.click();
        assertThat(measurementToggle.isChecked()).isTrue();
        sDevice.pressBack();

        // 3) check if Measurement API is enabled
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_measurement_view_title);
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
    public void topicsSubTitleTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        ShellUtils.runShellCommand(
                "device_config put adservices ui_toggle_speed_bump_enabled false");

        ApkTestUtil.launchSettingView(
                ApplicationProvider.getApplicationContext(), sDevice, LAUNCH_TIMEOUT);
        checkSubtitleMatchesToggle(
                ".*:id/topics_preference_subtitle", R.string.settingsUI_topics_ga_title);
    }

    @Test
    public void appsSubTitleTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        ShellUtils.runShellCommand(
                "device_config put adservices ui_toggle_speed_bump_enabled false");

        ApkTestUtil.launchSettingView(
                ApplicationProvider.getApplicationContext(), sDevice, LAUNCH_TIMEOUT);
        checkSubtitleMatchesToggle(
                ".*:id/apps_preference_subtitle", R.string.settingsUI_apps_ga_title);
    }

    @Test
    public void measurementSubTitleTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
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
    public void topicsToggleDialogTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand(
                "device_config put adservices ui_toggle_speed_bump_enabled true");
        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);

        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_topics_ga_title);
        sDevice.waitForIdle();

        UiObject topicsToggle =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        topicsToggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        if (topicsToggle.isChecked()) {
            // turn it off
            topicsToggle.click();
            UiObject dialogOptOutTitle =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_topics_opt_out_title);
            UiObject positiveButton =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_opt_out_positive_text);
            assertThat(dialogOptOutTitle.exists()).isTrue();
            positiveButton.click();
            assertThat(topicsToggle.isChecked()).isFalse();
            // then turn it on again
            topicsToggle.click();
            UiObject dialogOptInTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_topics_opt_in_title);
            UiObject okButton =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_acknowledge);
            assertThat(dialogOptInTitle.exists()).isTrue();
            okButton.click();
            assertThat(topicsToggle.isChecked()).isTrue();
        } else {
            // turn it on
            topicsToggle.click();
            UiObject dialogOptInTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_topics_opt_in_title);
            UiObject okButton =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_acknowledge);
            assertThat(dialogOptInTitle.exists()).isTrue();
            okButton.click();
            assertThat(topicsToggle.isChecked()).isTrue();
            // then turn it off
            topicsToggle.click();
            UiObject dialogOptOutTitle =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_topics_opt_out_title);
            UiObject positiveButton =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_opt_out_positive_text);
            assertThat(dialogOptOutTitle.exists()).isTrue();
            positiveButton.click();
            assertThat(topicsToggle.isChecked()).isFalse();
        }
    }

    @Test
    public void appsToggleDialogTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand(
                "device_config put adservices ui_toggle_speed_bump_enabled true");
        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);

        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_apps_ga_title);
        sDevice.waitForIdle();

        UiObject appsToggle =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        appsToggle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        if (appsToggle.isChecked()) {
            // turn it off
            appsToggle.click();
            UiObject dialogOptOutTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_apps_opt_out_title);
            UiObject positiveButton =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_opt_out_positive_text);
            assertThat(dialogOptOutTitle.exists()).isTrue();
            positiveButton.click();
            assertThat(appsToggle.isChecked()).isFalse();
            // then turn it on again
            appsToggle.click();
            UiObject dialogOptInTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_apps_opt_in_title);
            UiObject okButton =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_acknowledge);
            assertThat(dialogOptInTitle.exists()).isTrue();
            okButton.click();
            assertThat(appsToggle.isChecked()).isTrue();
        } else {
            // turn it on
            appsToggle.click();
            UiObject dialogOptInTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_apps_opt_in_title);
            UiObject okButton =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_acknowledge);
            assertThat(dialogOptInTitle.exists()).isTrue();
            okButton.click();
            assertThat(appsToggle.isChecked()).isTrue();
            // then turn it off
            appsToggle.click();
            UiObject dialogOptOutTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_apps_opt_out_title);
            UiObject positiveButton =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_opt_out_positive_text);
            assertThat(dialogOptOutTitle.exists()).isTrue();
            positiveButton.click();
            assertThat(appsToggle.isChecked()).isFalse();
        }
    }

    @Test
    public void measurementToggleDialogTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand(
                "device_config put adservices ui_toggle_speed_bump_enabled true");
        ApkTestUtil.launchSettingView(sContext, sDevice, LAUNCH_TIMEOUT);

        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_measurement_ga_title);
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
        UiScrollable scrollView =
                new UiScrollable(
                        new UiSelector().scrollable(true).className("android.widget.ScrollView"));
        UiObject subtitle = sDevice.findObject(new UiSelector().resourceIdMatches(regexResId));
        subtitle.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        scrollView.scrollIntoView(subtitle);
        if (subtitle.getText()
                .equals(ApkTestUtil.getString(R.string.settingsUI_subtitle_consent_off))) {
            ApkTestUtil.scrollToAndClick(sDevice, stringIdOfTitle);
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
            ApkTestUtil.scrollToAndClick(sDevice, stringIdOfTitle);
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

    private UiObject scrollTo(int resId) throws UiObjectNotFoundException {
        UiScrollable scrollView =
                new UiScrollable(
                        new UiSelector().scrollable(true).className("android.widget.ScrollView"));
        UiObject element = ApkTestUtil.getPageElement(sDevice, resId);
        scrollView.scrollIntoView(element);
        return element;
    }

    public void clickResetBtn() throws UiObjectNotFoundException {
        // R Msmt UI is not scrollable
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            ApkTestUtil.click(
                    sDevice,
                    com.android.adservices.api.R.string.settingsUI_measurement_view_reset_title);
        } else {
            ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_measurement_view_reset_title);
        }
    }
}
