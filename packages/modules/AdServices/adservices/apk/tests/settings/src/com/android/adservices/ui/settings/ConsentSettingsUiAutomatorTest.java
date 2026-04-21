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

package com.android.adservices.ui.settings;

import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ConsentSettingsUiAutomatorTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final int LAUNCH_TIMEOUT = 5000;
    private static UiDevice sDevice;

    private String mTestName;

    @Rule(order = 0)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Rule(order = 1)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setCompatModeFlags()
                    .setFlag(KEY_GA_UX_FEATURE_ENABLED, false);

    @Before
    public void setup() {
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

        AdservicesTestHelper.killAdservicesProcess(CONTEXT);
    }

    @Test
    @Ignore("b/293366771")
    public void consentSystemServerOnlyTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        Assume.assumeTrue(SdkLevel.isAtLeastT());

        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 0");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        consentTest(false);
    }

    @Test
    @Ignore("b/293366771")
    public void consentPpApiOnlyTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 1");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        consentTest(false);
    }

    @Test
    @Ignore("b/293366771")
    public void consentSystemServerAndPpApiTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        Assume.assumeTrue(SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 2");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        consentTest(false);
    }

    @Test
    @Ignore("b/293366771")
    public void consentSystemServerOnlyDialogsOnTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        Assume.assumeTrue(SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 0");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");
        consentTest(true);
    }

    @Test
    @Ignore("b/293366771")
    public void consentPpApiOnlyDialogsOnTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 1");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");

        consentTest(true);
    }

    @Test
    @Ignore("b/293366771")
    public void consentSystemServerAndPpApiDialogsOnTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        Assume.assumeTrue(SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 2");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");
        consentTest(true);
    }

    @Test
    @Ignore("b/293366771")
    public void consentAppSearchOnlyTest() throws UiObjectNotFoundException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        // APPSEARCH_ONLY is not a valid choice of consent_source_of_truth on T+.
        Assume.assumeTrue(!SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 3");
        ShellUtils.runShellCommand(
                "device_config put adservices enable_appsearch_consent_data true");
        consentTest(false);
    }

    @Test
    @Ignore("b/293366771")
    public void consentAppSearchOnlyDialogsOnTest() throws UiObjectNotFoundException {
        // APPSEARCH_ONLY is not a valid choice of consent_source_of_truth on T+.
        Assume.assumeTrue(!SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 3");
        ShellUtils.runShellCommand(
                "device_config put adservices enable_appsearch_consent_data true");
        consentTest(true);
    }

    private void consentTest(boolean dialogsOn) throws UiObjectNotFoundException {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_activity_debug_mode true");
        ShellUtils.runShellCommand("device_config put adservices debug_ux BETA_UX");

        ApkTestUtil.launchSettingViewGivenUx(
                ApplicationProvider.getApplicationContext(), sDevice, LAUNCH_TIMEOUT, "BETA_UX");

        UiObject consentSwitch = ApkTestUtil.getConsentSwitch(sDevice);
        setConsentToFalse(dialogsOn);

        // click switch
        performSwitchClick(dialogsOn, consentSwitch);
        assertThat(consentSwitch.isChecked()).isTrue();

        // click switch
        performSwitchClick(dialogsOn, consentSwitch);
        assertThat(consentSwitch.isChecked()).isFalse();
    }

    private void setConsentToFalse(boolean dialogsOn) throws UiObjectNotFoundException {
        UiObject consentSwitch = ApkTestUtil.getConsentSwitch(sDevice);
        if (consentSwitch.isChecked()) {
            performSwitchClick(dialogsOn, consentSwitch);
        }
    }

    private void performSwitchClick(boolean dialogsOn, UiObject mainSwitch)
            throws UiObjectNotFoundException {
        if (dialogsOn && mainSwitch.isChecked()) {
            mainSwitch.click();
            UiObject dialogTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_opt_out_title);
            UiObject positiveText =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_opt_out_positive_text);
            assertThat(dialogTitle.exists()).isTrue();
            assertThat(positiveText.exists()).isTrue();
            positiveText.click();
        } else {
            mainSwitch.click();
        }
    }
}
