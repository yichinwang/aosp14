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
package com.android.adservices.ui.notifications;

import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_EU_NOTIF_FLOW_CHANGE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_U18_UX_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.adservices.LogUtil;
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
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class NotificationActivityGAV2UxSelectorUiAutomatorTest {
    private static final String NOTIFICATION_PACKAGE = "android.adservices.ui.NOTIFICATIONS";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static final int SCROLL_WAIT_TIME = 2000;
    private static final UiDevice sDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private String mTestName;

    @Rule(order = 0)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Rule(order = 1)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setCompatModeFlags()
                    .setFlag(KEY_ENABLE_AD_SERVICES_SYSTEM_API, true)
                    .setFlag(KEY_GA_UX_FEATURE_ENABLED, true)
                    .setFlag(KEY_U18_UX_ENABLED, true)
                    .setFlag(KEY_EU_NOTIF_FLOW_CHANGE_ENABLED, true);

    @BeforeClass
    public static void classSetup() throws InterruptedException {
        AdservicesTestHelper.killAdservicesProcess(ApplicationProvider.getApplicationContext());
        // sleep for 1 min for bootCompleteReceiver to get invoked on S-
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            TimeUnit.SECONDS.sleep(60);
        }
    }

    @Before
    public void setup() throws UiObjectNotFoundException, IOException {
        Assume.assumeTrue(SdkLevel.isAtLeastS());
        sDevice.pressHome();
        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);
    }

    @After
    public void teardown() throws Exception {
        ApkTestUtil.takeScreenshot(sDevice, getClass().getSimpleName() + "_" + mTestName + "_");

        AdservicesTestHelper.killAdservicesProcess(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void euAcceptFlowTest() throws UiObjectNotFoundException, InterruptedException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        startActivity(true);
        UiObject leftControlButton =
                getElement(R.string.notificationUI_confirmation_left_control_button_text);
        UiObject rightControlButton =
                getElement(R.string.notificationUI_confirmation_right_control_button_text);

        clickMoreToBottom();
        assertThat(leftControlButton.exists()).isTrue();
        assertThat(rightControlButton.exists()).isTrue();

        rightControlButton.click();
        UiObject title2 = getElement(R.string.notificationUI_header_ga_title_eu_v2);
        assertThat(title2.exists()).isTrue();
        leftControlButton = getElement(R.string.notificationUI_left_control_button_text_eu);
        rightControlButton = getElement(R.string.notificationUI_right_control_button_ga_text_eu_v2);
        assertThat(leftControlButton.exists()).isFalse();
        assertThat(rightControlButton.exists()).isFalse();

        clickMoreToBottom();
        assertThat(leftControlButton.exists()).isTrue();
        assertThat(rightControlButton.exists()).isTrue();

        rightControlButton.click();
        assertThat(title2.exists()).isFalse();
    }

    @Test
    @FlakyTest(bugId = 302607350)
    public void rowClickSettingsTest() throws UiObjectNotFoundException, InterruptedException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        startActivity(false);
        UiObject leftControlButton = getElement(R.string.notificationUI_left_control_button_text);
        UiObject rightControlButton = getElement(R.string.notificationUI_right_control_button_text);
        clickMoreToBottom();
        assertThat(leftControlButton.exists()).isTrue();
        assertThat(rightControlButton.exists()).isTrue();
        leftControlButton.click();
        UiObject topicsTitle = getElement(R.string.settingsUI_topics_ga_title);
        ApkTestUtil.scrollTo(sDevice, R.string.settingsUI_topics_ga_title);
        assertThat(topicsTitle.exists()).isTrue();
        UiObject appsTitle = getElement(R.string.settingsUI_apps_ga_title);
        ApkTestUtil.scrollTo(sDevice, R.string.settingsUI_apps_ga_title);
        assertThat(appsTitle.exists()).isTrue();
    }

    private void startActivity(boolean isEUActivity) {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_activity_debug_mode true");
        ShellUtils.runShellCommand("device_config put adservices debug_ux GA_UX");

        String notificationPackage = NOTIFICATION_PACKAGE;
        Intent intent = new Intent(notificationPackage);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("isEUDevice", isEUActivity);

        ApplicationProvider.getApplicationContext().startActivity(intent);
        sDevice.wait(Until.hasObject(By.pkg(notificationPackage).depth(0)), LAUNCH_TIMEOUT);
    }

    private void clickMoreToBottom() throws UiObjectNotFoundException, InterruptedException {
        UiObject moreButton = getElement(R.string.notificationUI_more_button_text);
        if (!moreButton.exists()) {
            LogUtil.e("More Button not Found");
            return;
        }

        int clickCount = 10;
        while (moreButton.exists() && clickCount-- > 0) {
            moreButton.click();
            Thread.sleep(SCROLL_WAIT_TIME);
        }
        assertThat(moreButton.exists()).isFalse();
    }

    private String getString(int resourceId) {
        return ApplicationProvider.getApplicationContext().getResources().getString(resourceId);
    }

    private UiObject getElement(int resId) {
        return sDevice.findObject(new UiSelector().text(getString(resId)));
    }
}
