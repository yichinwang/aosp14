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

package com.android.adservices.ui.notifications;

import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.BETA_UX;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.content.Intent;

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

import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.PhFlags;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.io.IOException;

@SpyStatic(PhFlags.class)
@SpyStatic(BackgroundJobsManager.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(ConsentManager.class)
@SpyStatic(UxStatesManager.class)
@RunWith(AndroidJUnit4.class)
public final class NotificationActivityUiAutomatorTest extends AdServicesExtendedMockitoTestCase {

    private static final String NOTIFICATION_TEST_PACKAGE =
            "android.test.adservices.ui.NOTIFICATIONS";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static final UiDevice sDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    @Mock private ConsentManager mConsentManager;
    private Context mContext;

    // TODO(b/261216850): Migrate this NotificationActivity to non-mock test
    @Mock private Flags mMockFlags;
    @Mock private UxStatesManager mUxStatesManager;

    @Before
    public void setup() throws UiObjectNotFoundException, IOException {
        mContext = spy(appContext.get());

        doReturn(false).when(mMockFlags).getEuNotifFlowChangeEnabled();
        doReturn(true).when(mMockFlags).getUIDialogsFeatureEnabled();
        doReturn(true).when(mMockFlags).isUiFeatureTypeLoggingEnabled();
        doReturn(true).when(mMockFlags).getRecordManualInteractionEnabled();
        doReturn(true).when(mMockFlags).getConsentNotificationActivityDebugMode();
        doReturn(BETA_UX).when(mUxStatesManager).getUx();
        doReturn("BETA_UX").when(mMockFlags).getDebugUx();

        extendedMockito.mockGetFlags(mMockFlags);
        ExtendedMockito.doReturn(mUxStatesManager)
                .when(() -> UxStatesManager.getInstance(any(Context.class)));
        ExtendedMockito.doReturn(mConsentManager)
                .when(() -> ConsentManager.getInstance(any(Context.class)));
        ExtendedMockito.doNothing()
                .when(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));

        sDevice.pressHome();

        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);
    }

    @After
    public void teardown() throws Exception {
        ApkTestUtil.takeScreenshot(sDevice, getClass().getSimpleName() + "_" + getTestName() + "_");

        AdservicesTestHelper.killAdservicesProcess(mContext);
    }

    @Test
    @FlakyTest(bugId = 302607350)
    public void moreButtonTest() throws Exception {
        startActivity(true);
        UiObject leftControlButton =
                getElement(R.string.notificationUI_left_control_button_text_eu);
        UiObject rightControlButton =
                getElement(R.string.notificationUI_right_control_button_text_eu);
        UiObject moreButton = getElement(R.string.notificationUI_more_button_text);
        while (moreButton.exists()) {
            moreButton.click();
            Thread.sleep(2000);
        }
        assertThat(leftControlButton.exists()).isTrue();
        assertThat(rightControlButton.exists()).isTrue();
        assertThat(moreButton.exists()).isFalse();
    }

    @Test
    public void acceptedConfirmationScreenTest() throws Exception {
        doReturn(false).when(mMockFlags).getGaUxFeatureEnabled();

        startActivity(true);
        UiObject leftControlButton =
                getElement(R.string.notificationUI_left_control_button_text_eu);
        UiObject rightControlButton =
                getElement(R.string.notificationUI_right_control_button_text_eu);
        UiObject moreButton = getElement(R.string.notificationUI_more_button_text);
        while (moreButton.exists()) {
            moreButton.click();
            Thread.sleep(2000);
        }
        assertThat(leftControlButton.exists()).isTrue();
        assertThat(rightControlButton.exists()).isTrue();
        assertThat(moreButton.exists()).isFalse();

        rightControlButton.click();
        UiObject acceptedTitle = getElement(R.string.notificationUI_confirmation_accept_title);
        assertThat(acceptedTitle.exists()).isTrue();
    }

    @Test
    @FlakyTest(bugId = 302607350)
    public void notificationEuGaTest() throws Exception {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn("GA_UX").when(mMockFlags).getDebugUx();

        startActivity(true);

        UiObject notificationEuGaTitle = getElement(R.string.notificationUI_header_ga_title_eu);
        assertThat(notificationEuGaTitle.exists()).isTrue();

        UiObject leftControlButton =
                getElement(R.string.notificationUI_left_control_button_text_eu);
        UiObject rightControlButton =
                getElement(R.string.notificationUI_right_control_button_ga_text_eu);
        UiObject moreButton = getElement(R.string.notificationUI_more_button_text);

        verifyControlsAndMoreButtonAreDisplayed(leftControlButton, rightControlButton, moreButton);
    }

    @Test
    @FlakyTest(bugId = 302607350)
    public void notificationRowGaTest() throws Exception {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn("GA_UX").when(mMockFlags).getDebugUx();

        startActivity(false);

        UiObject notificationGaTitle = getElement(R.string.notificationUI_header_ga_title);
        assertThat(notificationGaTitle.exists()).isTrue();

        UiObject leftControlButton = getElement(R.string.notificationUI_left_control_button_text);
        UiObject rightControlButton = getElement(R.string.notificationUI_right_control_button_text);
        UiObject moreButton = getElement(R.string.notificationUI_more_button_text);
    }

    @Test
    public void acceptedConfirmationScreenGaTest() throws Exception {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn("GA_UX").when(mMockFlags).getDebugUx();

        startActivity(true);

        UiObject leftControlButton =
                getElement(R.string.notificationUI_left_control_button_text_eu);
        UiObject rightControlButton =
                getElement(R.string.notificationUI_right_control_button_ga_text_eu);
        UiObject moreButton = getElement(R.string.notificationUI_more_button_text);

        verifyControlsAndMoreButtonAreDisplayed(leftControlButton, rightControlButton, moreButton);

        rightControlButton.click();

        UiObject acceptedTitle = getElement(R.string.notificationUI_fledge_measurement_title);
        assertThat(acceptedTitle.exists()).isTrue();
        UiObject leftControlButtonOnSecondPage =
                getElement(R.string.notificationUI_confirmation_left_control_button_text);
        UiObject rightControlButtonOnSecondPage =
                getElement(R.string.notificationUI_confirmation_right_control_button_text);
        UiObject moreButtonOnSecondPage = getElement(R.string.notificationUI_more_button_text);
        verifyControlsAndMoreButtonAreDisplayed(
                leftControlButtonOnSecondPage,
                rightControlButtonOnSecondPage,
                moreButtonOnSecondPage);
    }

    @Test
    @FlakyTest(bugId = 302607350)
    public void declinedConfirmationScreenGaTest() throws Exception {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn("GA_UX").when(mMockFlags).getDebugUx();

        startActivity(true);

        UiObject leftControlButton =
                getElement(R.string.notificationUI_left_control_button_text_eu);
        UiObject rightControlButton =
                getElement(R.string.notificationUI_right_control_button_ga_text_eu);
        UiObject moreButton = getElement(R.string.notificationUI_more_button_text);

        verifyControlsAndMoreButtonAreDisplayed(leftControlButton, rightControlButton, moreButton);

        leftControlButton.click();

        UiObject acceptedTitle = getElement(R.string.notificationUI_fledge_measurement_title);
        assertThat(acceptedTitle.exists()).isTrue();
        UiObject leftControlButtonOnSecondPage =
                getElement(R.string.notificationUI_confirmation_left_control_button_text);
        UiObject rightControlButtonOnSecondPage =
                getElement(R.string.notificationUI_confirmation_right_control_button_text);
        UiObject moreButtonOnSecondPage = getElement(R.string.notificationUI_more_button_text);
        verifyControlsAndMoreButtonAreDisplayed(
                leftControlButtonOnSecondPage,
                rightControlButtonOnSecondPage,
                moreButtonOnSecondPage);
    }

    private void verifyControlsAndMoreButtonAreDisplayed(
            UiObject leftControlButton, UiObject rightControlButton, UiObject moreButton)
            throws UiObjectNotFoundException, InterruptedException {
        UiObject scrollView =
                sDevice.findObject(new UiSelector().className("android.widget.ScrollView"));

        // TODO: clean up the following code with NotificationPages.goThroughNotificationPage()
        if (scrollView.isScrollable()) {
            // there should be a more button
            assertThat(leftControlButton.exists()).isFalse();
            assertThat(rightControlButton.exists()).isFalse();
            assertThat(moreButton.exists()).isTrue();

            while (moreButton.exists()) {
                moreButton.click();
                Thread.sleep(2000);
            }
            assertThat(leftControlButton.exists()).isTrue();
            assertThat(rightControlButton.exists()).isTrue();
            assertThat(moreButton.exists()).isFalse();
        } else {
            // fix the flaky test where test fails due to only moreButton exists
            int clickCount = 10;
            while (moreButton.exists() && clickCount-- > 0) {
                moreButton.click();
                Thread.sleep(2000);
            }
            assertThat(leftControlButton.exists()).isTrue();
            assertThat(rightControlButton.exists()).isTrue();
            assertThat(moreButton.exists()).isFalse();
        }
    }

    private void startActivity(boolean isEUActivity) {
        Intent intent = new Intent(NOTIFICATION_TEST_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("isEUDevice", isEUActivity);
        mContext.startActivity(intent);

        sDevice.wait(Until.hasObject(By.pkg(NOTIFICATION_TEST_PACKAGE).depth(0)), LAUNCH_TIMEOUT);
    }

    private String getString(int resourceId) {
        return ApplicationProvider.getApplicationContext().getResources().getString(resourceId);
    }

    private UiObject getElement(int resId) {
        return sDevice.findObject(new UiSelector().text(getString(resId)));
    }

    private boolean isDefaultBrowserOpenedAfterClicksOnTheBottomOfSentence(
            String packageNameOfDefaultBrowser, UiObject sentence, int countOfClicks)
            throws Exception {
        int right = sentence.getBounds().right,
                bottom = sentence.getBounds().bottom,
                left = sentence.getBounds().left;
        for (int x = left; x < right; x += (right - left) / countOfClicks) {
            sDevice.click(x, bottom - 2);
            Thread.sleep(200);
        }

        if (!sentence.exists()) {
            sDevice.pressBack();
            ApkTestUtil.killDefaultBrowserPkgName(sDevice, mContext);
            return true;
        }

        return false;
    }
}
