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

package com.android.adservices.ui.settings;

import static com.android.adservices.service.FlagsConstants.KEY_BLOCKED_TOPICS_SOURCE_OF_TRUTH;
import static com.android.adservices.service.FlagsConstants.KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH;
import static com.android.adservices.service.FlagsConstants.KEY_UI_DIALOGS_FEATURE_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LoggerFactory;
import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Class to test the CUJ that blocks/unblocks/resets topics with dialog enabled. There are two tests
 * for Beta UX view or GA UX view respectively.
 */
public class BlockedTopicsSettingsUiAutomatorTest {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String LOG_TAG = "adservices";
    // Used to get the package name. Copied over from com.android.adservices.AdServicesCommon
    private static final String TOPICS_SERVICE_NAME = "android.adservices.TOPICS_SERVICE";
    private static final String ADSERVICES_PACKAGE_NAME = getAdServicesPackageName();
    private static final int EPOCH_JOB_ID = 2;
    // Time out to start UI launcher.
    private static final int LAUNCHER_LAUNCH_TIMEOUT = 3000;
    // Time out to allow UI Object to appear on the screen. This allows devices with different
    // performance to switch between views.
    public static final int PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT = 1000;
    // The epoch length to override. It would increase the test running time if it's too long. And
    // it would make the test flaky if it's too short -- it may have passed 3 epochs so that the
    // generated topic wouldn't take effect during the test.
    //
    // Set it to 10 seconds because AVD takes longer time to operate UI. Normally 3 seconds are
    // enough for a non-ui test.
    private static final long TEST_EPOCH_JOB_PERIOD_MS = 10000;

    private static UiDevice sDevice;

    private String mTestName;

    @Rule(order = 0)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Rule(order = 1)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setTopicsEpochJobPeriodMsForTests(TEST_EPOCH_JOB_PERIOD_MS)
                    .setTopicsKillSwitch(false)
                    .setFlag(KEY_CONSENT_SOURCE_OF_TRUTH, 2)
                    .setFlag(KEY_BLOCKED_TOPICS_SOURCE_OF_TRUTH, 2)
                    .setFlag(KEY_UI_DIALOGS_FEATURE_ENABLED, true)
                    .setDisableTopicsEnrollmentCheckForTests(true)
                    .setFlag(KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES, true)
                    .setCompatModeFlags();

    @Before
    public void setup() {
        // Initialize UiDevice instance.
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen.
        sDevice.pressHome();

        // Wait for launcher.
        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCHER_LAUNCH_TIMEOUT);
    }

    @After
    public void teardown() throws UiObjectNotFoundException {
        ApkTestUtil.takeScreenshot(sDevice, getClass().getSimpleName() + "_" + mTestName + "_");

        AdservicesTestHelper.killAdservicesProcess(ADSERVICES_PACKAGE_NAME);
    }

    @Test
    @Ignore("b/272511638")
    public void topicBlockUnblockResetTest_betaUxView() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        // Enable Beta UX view for Privacy Sandbox Settings.
        shouldEnableGaUx(false);

        // Launch main view of Privacy Sandbox Settings.
        ApkTestUtil.launchSettingView(CONTEXT, sDevice, LAUNCHER_LAUNCH_TIMEOUT);

        // Enable user consent. If it has been enabled due to stale test failures, disable it and
        // enable it again. This is to ensure no stale data or pending jobs.
        UiObject consentSwitch = ApkTestUtil.getConsentSwitch(sDevice);
        consentSwitch.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        if (consentSwitch.isChecked()) {
            disableUserConsentWithDialog(consentSwitch);
        }
        consentSwitch.click();

        // Generate a topic to block.
        generateATopicToBlock();

        // Open the blocked topics view.
        UiObject topicsViewButton = scrollTo(R.string.settingsUI_topics_title);
        topicsViewButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        topicsViewButton.click();

        // Verify there is a topic to block and block it.
        UiObject blockTopicButton =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_block_topic_title, 0);
        blockTopicButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        blockATopicWithDialog(blockTopicButton);

        // When there is no topic available to be blocked, it will display "no topics" text and the
        // "Block" button will not be displayed.
        assertThat(blockTopicButton.exists()).isFalse();
        UiObject noTopicsText = getElement(R.string.settingsUI_topics_view_no_topics_text);
        assertThat(noTopicsText.exists()).isTrue();

        // Click viewBlockedTopicsButton to view topics being blocked.
        UiObject viewBlockedTopicsButton = scrollTo(R.string.settingsUI_blocked_topics_title);
        viewBlockedTopicsButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(viewBlockedTopicsButton.exists()).isTrue();
        viewBlockedTopicsButton.click();

        // There is 1 topic being blocked and "Unblock" button should be visible. Unblock it.
        unblockATopicWithDialog();

        // Verify there is no blocked topic.
        UiObject noUnblockedTopicsText =
                getElement(R.string.settingsUI_topics_view_no_blocked_topics_text);
        noUnblockedTopicsText.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(noUnblockedTopicsText.exists()).isTrue();

        // Press back to return to the blocked topic view.
        sDevice.pressBack();

        // Verify there is a topic to be blocked
        blockTopicButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(blockTopicButton.exists()).isTrue();

        // Reset blocked topics
        UiObject resetButton = scrollTo(R.string.settingsUI_reset_topics_title);
        resetButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        resetATopicWithDialog(resetButton);

        // Verify there is no topic to block after resetting.
        assertThat(blockTopicButton.exists()).isFalse();

        // Press back to consent main view.
        sDevice.pressBack();

        // restart the app since scrollToBeginning does not work.
        AdservicesTestHelper.killAdservicesProcess(ADSERVICES_PACKAGE_NAME);
        Thread.sleep(3000);
        ApkTestUtil.launchSettingView(CONTEXT, sDevice, LAUNCHER_LAUNCH_TIMEOUT);

        // Disable user consent.
        consentSwitch.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        scrollTo(R.string.settingsUI_privacy_sandbox_beta_switch_title);
        consentSwitch.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        disableUserConsentWithDialog(consentSwitch);
        assertThat(consentSwitch.isChecked()).isFalse();
    }

    @Test
    @Ignore("b/272511638")
    public void topicBlockUnblockResetTest_gaUxView() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        // Enable GA UX view for Privacy Sandbox Settings.
        shouldEnableGaUx(true);

        // Launch main view of Privacy Sandbox Settings.
        ApkTestUtil.launchSettingView(CONTEXT, sDevice, LAUNCHER_LAUNCH_TIMEOUT);

        // Enter Topics Consent view.
        enterGaTopicsConsentView();

        // Enable Topics consent. If it has been enabled due to stale test failures, disable it and
        // enable it again. This is to ensure no stale data or pending jobs.
        //
        // Note there is no dialog when the user opts out in GA.
        UiObject consentSwitch = ApkTestUtil.getConsentSwitch(sDevice);
        consentSwitch.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        if (consentSwitch.isChecked()) {
            consentSwitch.click();
        }
        consentSwitch.click();

        // Navigate back to main view. This allows to refresh the topics list by re-entering Topics
        // consent view after a topic is generated . (There is no real-time listener)
        sDevice.pressBack();

        // Generate a topic to block.
        generateATopicToBlock();

        // Re-enter Topics Consent View.
        enterGaTopicsConsentView();
        consentSwitch.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);

        // Verify there is a topic to be blocked.
        UiObject blockTopicButton =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_block_topic_title, 0);
        blockTopicButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        blockATopicWithDialog(blockTopicButton);

        // When there is no topic available to be blocked, it will display "no topics" text and the
        // "Block" button will not be displayed.
        assertThat(blockTopicButton.exists()).isFalse();
        UiObject noTopicsText = getElement(R.string.settingsUI_topics_view_no_topics_ga_text);
        assertThat(noTopicsText.exists()).isTrue();

        // Click viewBlockedTopicsButton to view topics being blocked.
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_view_blocked_topics_title);

        // There is 1 topic being blocked and "Unblock" button should be visible. Unblock it.
        unblockATopicWithDialog();

        // Verify there is no blocked topic.
        UiObject noUnblockedTopicsText = getElement(R.string.settingsUI_no_blocked_topics_ga_text);
        noUnblockedTopicsText.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(noUnblockedTopicsText.exists()).isTrue();

        // Press back to return to the Topics Consent view.
        sDevice.pressBack();

        // Verify there is a topic to be blocked.
        blockTopicButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(blockTopicButton.exists()).isTrue();

        // Reset blocked topics.
        UiObject resetButton = scrollTo(R.string.settingsUI_reset_topics_ga_title);
        resetButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        resetATopicWithDialog(resetButton);

        // Scroll to consent switch and verify there is no topic to block after resetting.
        consentSwitch = scrollTo(R.string.settingsUI_topics_switch_title);
        consentSwitch.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(blockTopicButton.exists()).isFalse();

        // Disable user consent.
        consentSwitch.click();
        assertThat(consentSwitch.isChecked()).isFalse();
    }

    // Enter Topics Consent view when GA UX is enabled.
    private void enterGaTopicsConsentView() throws UiObjectNotFoundException {
        ApkTestUtil.scrollToAndClick(sDevice, R.string.settingsUI_topics_ga_title);
    }

    // Block a topic when dialog is enabled.
    private void blockATopicWithDialog(UiObject blockTopicButton) throws UiObjectNotFoundException {
        // Verify the button is existed and click it.
        assertThat(blockTopicButton.exists()).isTrue();
        blockTopicButton.click();

        // Handle dialog for blocking a topic
        UiObject dialogTitle =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_block_topic_message);
        UiObject positiveText =
                ApkTestUtil.getElement(
                        sDevice, R.string.settingsUI_dialog_block_topic_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // Confirm to block.
        positiveText.click();
    }

    // Unblock a blocked topic when dialog is enabled.
    private void unblockATopicWithDialog() throws UiObjectNotFoundException {
        // Get unblock topic button.
        UiObject unblockTopicButton =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_unblock_topic_title, 0);
        unblockTopicButton.waitForExists(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        assertThat(unblockTopicButton.exists()).isTrue();

        // Click "Unblock" and UI should display text "no blocked topics".
        unblockTopicButton.click();

        // Handle dialog for unblocking a topic.
        UiObject dialogTitle = getElement(R.string.settingsUI_dialog_unblock_topic_message);
        UiObject positiveText = getElement(R.string.settingsUI_dialog_unblock_topic_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // Confirm to unblock.
        positiveText.click();
    }

    // Reset blocked topics.
    private void resetATopicWithDialog(UiObject resetButton) throws UiObjectNotFoundException {
        // Verify the button is existed and click it.
        assertThat(resetButton.exists()).isTrue();
        resetButton.click();

        // Handle dialog for resetting topics.
        UiObject dialogTitle =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_reset_topic_message);
        UiObject positiveText =
                ApkTestUtil.getElement(
                        sDevice, R.string.settingsUI_dialog_reset_topic_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // Confirm to reset.
        positiveText.click();
    }

    // Disable user consent when dialog is enabled.
    private void disableUserConsentWithDialog(UiObject consentSwitch)
            throws UiObjectNotFoundException {
        // Verify the button is existed and click it.
        assertThat(consentSwitch.exists()).isTrue();
        consentSwitch.click();

        // Handle dialog to disable user consent
        UiObject dialogTitle =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_opt_out_title);
        UiObject positiveText =
                ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_opt_out_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // Confirm to disable.
        positiveText.click();
    }

    // Call Topics API and run epoch computation so there will be a topic to block.
    private void generateATopicToBlock() throws ExecutionException, InterruptedException {
        // Generate a client and ask it to call Topics API.
        AdvertisingTopicsClient advertisingTopicsClient1 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(CONTEXT)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        GetTopicsResponse sdk1Result = advertisingTopicsClient1.getTopics().get();
        // There should be no past data to return a topic when Topics API is called for the first
        // time.
        assertThat(sdk1Result.getTopics()).isEmpty();

        // Force epoch computation. Add a delay to allow background executor to finish the Topics
        // API invocation.
        Thread.sleep(500);
        forceEpochComputationJob();

        // Move to the next epoch because the computed result takes effect from the next epoch.
        Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);
    }

    // Scroll to a UI object.
    private UiObject scrollTo(int resId) throws UiObjectNotFoundException {
        UiScrollable scrollView =
                new UiScrollable(
                        new UiSelector().scrollable(true).className("android.widget.ScrollView"));
        UiObject element =
                sDevice.findObject(
                        new UiSelector().childSelector(new UiSelector().text(getString(resId))));
        scrollView.scrollIntoView(element);
        return element;
    }

    // Get a UI object by its resource id.
    private UiObject getElement(int resId) {
        return sDevice.findObject(new UiSelector().text(getString(resId)));
    }

    private String getString(int resourceId) {
        return ApplicationProvider.getApplicationContext().getResources().getString(resourceId);
    }

    // Forces JobScheduler to run the Epoch Computation job
    private void forceEpochComputationJob() {
        ShellUtils.runShellCommand(
                "cmd jobscheduler run -f" + " " + ADSERVICES_PACKAGE_NAME + " " + EPOCH_JOB_ID);
    }

    // Toggles GA UX.
    private void shouldEnableGaUx(boolean isEnabled) {
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled " + isEnabled);
    }

    // Get the adservices package name. Copied over from com.android.adservices.AdServicesCommon
    private static String getAdServicesPackageName() {
        final Intent intent = new Intent(TOPICS_SERVICE_NAME);
        final List<ResolveInfo> resolveInfos =
                CONTEXT.getPackageManager()
                        .queryIntentServices(intent, PackageManager.MATCH_SYSTEM_ONLY);
        final ServiceInfo serviceInfo =
                AdServicesCommon.resolveAdServicesService(resolveInfos, TOPICS_SERVICE_NAME);
        if (serviceInfo == null) {
            sLogger.e(LOG_TAG, "Failed to find serviceInfo for adServices service");
            return null;
        }

        return serviceInfo.packageName;
    }
}
