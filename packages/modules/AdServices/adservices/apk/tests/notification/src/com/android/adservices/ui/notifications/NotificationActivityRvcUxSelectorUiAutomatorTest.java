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

import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DEBUG_UX;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_U18_UX_ENABLED;
import static com.android.adservices.tests.ui.libs.AdservicesWorkflows.startNotificationActivity;
import static com.android.adservices.tests.ui.libs.UiUtils.LAUNCH_TIMEOUT;
import static com.android.adservices.tests.ui.libs.UiUtils.clickMoreToBottom;
import static com.android.adservices.ui.util.ApkTestUtil.getElement;

import static com.google.common.truth.Truth.assertThat;

import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
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

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class NotificationActivityRvcUxSelectorUiAutomatorTest {
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
                    .setFlag(KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE, true)
                    .setFlag(KEY_GA_UX_FEATURE_ENABLED, true)
                    .setFlag(KEY_U18_UX_ENABLED, true)
                    .setFlag(KEY_DEBUG_UX, "RVC_UX");

    /***
     * Setup for test.
     * @throws InterruptedException interruptedException
     */
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
    public void acceptFlowTest() throws UiObjectNotFoundException, InterruptedException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        startNotificationActivity(ApplicationProvider.getApplicationContext(), sDevice, false);

        UiObject u18NotificationTitle =
                getElement(sDevice, R.string.notificationUI_u18_notification_title);
        assertThat(u18NotificationTitle.exists()).isTrue();

        UiObject moreButton = getElement(sDevice, R.string.notificationUI_more_button_text);
        clickMoreToBottom(moreButton);

        UiObject
                leftControlButton =
                        getElement(sDevice, R.string.notificationUI_u18_left_control_button_text),
                rightControlButton =
                        getElement(sDevice, R.string.notificationUI_u18_right_control_button_text);
        assertThat(leftControlButton.exists()).isTrue();
        rightControlButton.click();
        assertThat(u18NotificationTitle.exists()).isFalse();
    }

    @FlakyTest(bugId = 309468369)
    @Test
    public void clickSettingsTest() throws UiObjectNotFoundException, InterruptedException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        startNotificationActivity(ApplicationProvider.getApplicationContext(), sDevice, false);

        UiObject moreButton = getElement(sDevice, R.string.notificationUI_more_button_text);
        clickMoreToBottom(moreButton);

        UiObject
                leftControlButton =
                        getElement(sDevice, R.string.notificationUI_u18_left_control_button_text),
                rightControlButton =
                        getElement(sDevice, R.string.notificationUI_u18_right_control_button_text);
        assertThat(rightControlButton.exists()).isTrue();

        leftControlButton.click();

        // make sure it goes to u18 page rather than GA page
        UiObject topicTitle = getElement(sDevice, R.string.settingsUI_topics_ga_title);
        assertThat((topicTitle.exists())).isFalse();
        UiObject measurementTitle =
                getElement(sDevice, R.string.settingsUI_u18_measurement_view_title);
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            ApkTestUtil.click(sDevice, R.string.settingsUI_u18_measurement_view_title);
        } else {
            ApkTestUtil.scrollTo(sDevice, R.string.settingsUI_u18_measurement_view_title);
        }
        assertThat(measurementTitle.exists()).isTrue();
    }
}
