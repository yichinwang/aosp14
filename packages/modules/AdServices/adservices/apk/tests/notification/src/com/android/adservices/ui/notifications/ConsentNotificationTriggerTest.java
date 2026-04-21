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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__AD_ID_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__PP_API_DEFAULT_OPT_OUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__MEASUREMENT_DEFAULT_OPT_OUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__FIRST_CONSENT_NOTIFICATION_CHANNEL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__RVC_POST_OTA_NOTIFICATION_CHANNEL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__GA_UX;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__RVC_UX;
import static com.android.adservices.service.FlagsConstants.KEY_EU_NOTIF_FLOW_CHANGE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_NOTIFICATION_DISMISSED_ON_CLICK;
import static com.android.adservices.service.FlagsConstants.KEY_RVC_UX_ENABLED;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.BETA_UX;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.GA_UX;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.RVC_UX;
import static com.android.adservices.ui.util.ApkTestUtil.getPageElement;
import static com.android.adservices.ui.util.ApkTestUtil.getString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.adservices.AdServicesManager;
import android.content.Context;

import androidx.core.app.NotificationManagerCompat;
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
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.consent.DeviceRegionProvider;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.UIStats;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.collection.GaUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.RvcUxEnrollmentChannelCollection;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.io.IOException;

@SpyStatic(ConsentManager.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(NotificationManagerCompat.class)
@SpyStatic(AdServicesLoggerImpl.class)
@SpyStatic(DeviceRegionProvider.class)
@SpyStatic(UxStatesManager.class)
@SpyStatic(UiStatsLogger.class)
@RunWith(AndroidJUnit4.class)
public final class ConsentNotificationTriggerTest extends AdServicesExtendedMockitoTestCase {

    private static final String NOTIFICATION_CHANNEL_ID = "PRIVACY_SANDBOX_CHANNEL";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static UiDevice sDevice;

    private AdServicesManager mAdServicesManager;
    private NotificationManager mNotificationManager;
    private Context mContext;

    @Mock private AdServicesLogger mAdServicesLogger;
    @Mock private NotificationManagerCompat mNotificationManagerCompat;
    @Mock private ConsentManager mConsentManager;
    @Mock private UxStatesManager mMockUxStatesManager;
    @Mock private Flags mMockFlags;

    @Before
    public void setUp() {
        mContext = spy(appContext.get());
        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mNotificationManager = mContext.getSystemService(NotificationManager.class);

        extendedMockito.mockGetFlags(mMockFlags);
        doReturn(mAdServicesLogger).when(UiStatsLogger::getAdServicesLogger);
        doReturn(mMockUxStatesManager).when(() -> UxStatesManager.getInstance(any(Context.class)));
        doReturn(mAdServicesManager).when(mContext).getSystemService(AdServicesManager.class);
        doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any(Context.class)));
        doReturn(true).when(mMockFlags).isEeaDeviceFeatureEnabled();
        doReturn(true).when(mMockFlags).isUiFeatureTypeLoggingEnabled();
        doReturn(false).when(mMockUxStatesManager).isEeaDevice();
        doReturn(false).when(mMockUxStatesManager).getFlag(any(String.class));
        doReturn(GA_UX).when(mMockUxStatesManager).getUx();
        doReturn(true).when(mMockUxStatesManager).getFlag(KEY_NOTIFICATION_DISMISSED_ON_CLICK);
        doReturn(false).when(mMockUxStatesManager).getFlag(KEY_EU_NOTIF_FLOW_CHANGE_ENABLED);
        cancelAllPreviousNotifications();
    }

    @After
    public void tearDown() throws IOException {
        ApkTestUtil.takeScreenshot(sDevice, getClass().getSimpleName() + "_" + getTestName() + "_");

        AdservicesTestHelper.killAdservicesProcess(mContext);
    }

    @Test
    public void testEuNotification() throws InterruptedException, UiObjectNotFoundException {
        doReturn(true).when(mMockFlags).isEeaDevice();
        doReturn(false).when(mMockUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(BETA_UX).when(mMockUxStatesManager).getUx();

        final String expectedTitle =
                mContext.getString(R.string.notificationUI_notification_title_eu);
        final String expectedContent =
                mContext.getString(R.string.notificationUI_notification_content_eu);

        ConsentNotificationTrigger.showConsentNotification(mContext, true);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(mAdServicesLogger, times(2)).logUIStats(any());

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).disable(mContext);
        verify(mConsentManager).recordNotificationDisplayed(true);

        assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
        final Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags).isEqualTo(0);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags).isEqualTo(0);
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags)
                .isEqualTo(Notification.FLAG_AUTO_CANCEL);

        sDevice.openNotification();
        sDevice.wait(Until.hasObject(By.pkg("com.android.systemui")), LAUNCH_TIMEOUT);
        UiObject scroller =
                sDevice.findObject(
                        new UiSelector()
                                .packageName("com.android.systemui")
                                .resourceId("com.android.systemui:id/notification_stack_scroller"));

        UiSelector notificationCardSelector =
                new UiSelector().text(getString(R.string.notificationUI_notification_title_eu));
        UiObject notificationCard = scroller.getChild(notificationCardSelector);
        assertThat(notificationCard.exists()).isTrue();

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        UiObject title = getPageElement(sDevice, R.string.notificationUI_header_title_eu);
        assertThat(title.exists()).isTrue();
    }

    @Test
    public void testEuNotification_gaUxFlagEnabled()
            throws InterruptedException, UiObjectNotFoundException {
        doReturn(true).when(mMockFlags).isEeaDevice();
        doReturn(true).when(mMockUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(GA_UX).when(mMockUxStatesManager).getUx();

        final String expectedTitle =
                mContext.getString(R.string.notificationUI_notification_ga_title_eu);
        final String expectedContent =
                mContext.getString(R.string.notificationUI_notification_ga_content_eu);

        ConsentNotificationTrigger.showConsentNotification(mContext, true);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(mAdServicesLogger, times(2)).logUIStats(any());

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).recordTopicsDefaultConsent(false);
        verify(mConsentManager).recordFledgeDefaultConsent(false);
        verify(mConsentManager).recordMeasurementDefaultConsent(false);
        verify(mConsentManager).disable(mContext, AdServicesApiType.FLEDGE);
        verify(mConsentManager).disable(mContext, AdServicesApiType.TOPICS);
        verify(mConsentManager).disable(mContext, AdServicesApiType.MEASUREMENTS);
        verify(mConsentManager).recordNotificationDisplayed(true);
        verify(mConsentManager).recordGaUxNotificationDisplayed(true);

        assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
        final Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags)
                .isEqualTo(Notification.FLAG_ONGOING_EVENT);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags)
                .isEqualTo(Notification.FLAG_NO_CLEAR);
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags)
                .isEqualTo(Notification.FLAG_AUTO_CANCEL);

        sDevice.openNotification();
        sDevice.wait(Until.hasObject(By.pkg("com.android.systemui")), LAUNCH_TIMEOUT);

        UiObject scroller =
                sDevice.findObject(
                        new UiSelector()
                                .packageName("com.android.systemui")
                                .resourceId("com.android.systemui:id/notification_stack_scroller"));
        assertThat(scroller.exists()).isTrue();
        UiObject notificationCard =
                scroller.getChild(
                        new UiSelector()
                                .text(getString(R.string.notificationUI_notification_ga_title_eu)));
        assertThat(notificationCard.exists()).isTrue();

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        assertThat(mNotificationManager.getActiveNotifications()).hasLength(0);
    }

    @Test
    public void testNonEuNotifications() throws InterruptedException, UiObjectNotFoundException {
        doReturn(false).when(mMockFlags).isEeaDevice();
        doReturn(false).when(mMockUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(BETA_UX).when(mMockUxStatesManager).getUx();

        final String expectedTitle = mContext.getString(R.string.notificationUI_notification_title);
        final String expectedContent =
                mContext.getString(R.string.notificationUI_notification_content);

        ConsentNotificationTrigger.showConsentNotification(mContext, false);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(mAdServicesLogger, times(2)).logUIStats(any());

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).enable(mContext);
        verify(mConsentManager).recordNotificationDisplayed(true);

        assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
        final Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags).isEqualTo(0);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags).isEqualTo(0);
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags)
                .isEqualTo(Notification.FLAG_AUTO_CANCEL);

        sDevice.openNotification();
        sDevice.wait(Until.hasObject(By.pkg("com.android.systemui")), LAUNCH_TIMEOUT);

        UiObject scroller =
                sDevice.findObject(
                        new UiSelector()
                                .packageName("com.android.systemui")
                                .resourceId("com.android.systemui:id/notification_stack_scroller"));
        assertThat(scroller.exists()).isTrue();
        UiObject notificationCard =
                scroller.getChild(
                        new UiSelector()
                                .text(getString(R.string.notificationUI_notification_title)));
        assertThat(notificationCard.exists()).isTrue();

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        UiObject title = getPageElement(sDevice, R.string.notificationUI_header_title);
        assertThat(title.exists()).isTrue();
    }

    @Test
    public void testNonEuNotifications_gaUxEnabled() throws InterruptedException {
        doReturn(false).when(mMockFlags).isEeaDevice();
        doReturn(true).when(mMockUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(GA_UX).when(mMockUxStatesManager).getUx();

        final String expectedTitle =
                mContext.getString(R.string.notificationUI_notification_ga_title);
        final String expectedContent =
                mContext.getString(R.string.notificationUI_notification_ga_content);

        ConsentNotificationTrigger.showConsentNotification(mContext, false);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(mAdServicesLogger, times(2)).logUIStats(any());

        verify(mConsentManager).enable(mContext, AdServicesApiType.TOPICS);
        verify(mConsentManager).enable(mContext, AdServicesApiType.FLEDGE);
        verify(mConsentManager).enable(mContext, AdServicesApiType.MEASUREMENTS);

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).recordTopicsDefaultConsent(true);
        verify(mConsentManager).recordFledgeDefaultConsent(true);
        verify(mConsentManager).recordMeasurementDefaultConsent(true);
        verify(mConsentManager).recordGaUxNotificationDisplayed(true);
        verify(mConsentManager).recordNotificationDisplayed(true);

        assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
        final Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags).isEqualTo(0);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags).isEqualTo(0);
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags)
                .isEqualTo(Notification.FLAG_AUTO_CANCEL);
        assertThat(notification.actions).isNull();
    }

    @Test
    public void testEuNotifications_gaUxEnabled_nonDismissable()
            throws InterruptedException, UiObjectNotFoundException {
        doReturn(true).when(mMockFlags).isEeaDevice();
        doReturn(true).when(mMockUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(GA_UX).when(mMockUxStatesManager).getUx();
        doReturn(false).when(mMockUxStatesManager).getFlag(KEY_NOTIFICATION_DISMISSED_ON_CLICK);

        final String expectedTitle =
                mContext.getString(R.string.notificationUI_notification_ga_title_eu);
        final String expectedContent =
                mContext.getString(R.string.notificationUI_notification_ga_content_eu);

        ConsentNotificationTrigger.showConsentNotification(mContext, true);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(mAdServicesLogger, times(2)).logUIStats(any());

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).recordTopicsDefaultConsent(false);
        verify(mConsentManager).recordFledgeDefaultConsent(false);
        verify(mConsentManager).recordMeasurementDefaultConsent(false);
        verify(mConsentManager).disable(mContext, AdServicesApiType.FLEDGE);
        verify(mConsentManager).disable(mContext, AdServicesApiType.TOPICS);
        verify(mConsentManager).disable(mContext, AdServicesApiType.MEASUREMENTS);
        verify(mConsentManager).recordNotificationDisplayed(true);
        verify(mConsentManager).recordGaUxNotificationDisplayed(true);

        assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
        final Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags)
                .isEqualTo(Notification.FLAG_ONGOING_EVENT);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags)
                .isEqualTo(Notification.FLAG_NO_CLEAR);
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags).isEqualTo(0);
        assertThat(notification.actions).isNull();

        sDevice.openNotification();
        sDevice.wait(Until.hasObject(By.pkg("com.android.systemui")), LAUNCH_TIMEOUT);

        UiObject scroller =
                sDevice.findObject(
                        new UiSelector()
                                .packageName("com.android.systemui")
                                .resourceId("com.android.systemui:id/notification_stack_scroller"));

        // there might be only one notification and no scroller exists.
        UiObject notificationCard;
        // notification card title might be cut off, so check for first portion of title
        UiSelector notificationCardSelector =
                new UiSelector()
                        .textContains(
                                getString(R.string.notificationUI_notification_ga_title_eu)
                                        .substring(0, 15));
        if (scroller.exists()) {
            notificationCard = scroller.getChild(notificationCardSelector);
        } else {
            notificationCard = sDevice.findObject(notificationCardSelector);
        }
        notificationCard.waitForExists(LAUNCH_TIMEOUT);
        assertThat(notificationCard.exists()).isTrue();

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
    }

    @Test
    @FlakyTest(bugId = 302607350)
    public void testEuNotifications_gaUxEnabled_nonDismissable_dismissedOnConfirmationPage()
            throws InterruptedException, UiObjectNotFoundException {
        doReturn(true).when(mMockFlags).isEeaDevice();
        doReturn(true).when(mMockFlags).getEnableAdServicesSystemApi();
        doReturn("GA_UX").when(mMockFlags).getDebugUx();
        doReturn(true).when(mMockFlags).getConsentNotificationActivityDebugMode();
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(GA_UX).when(mMockUxStatesManager).getUx();
        doReturn(true).when(mMockUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(false).when(mMockUxStatesManager).getFlag(KEY_EU_NOTIF_FLOW_CHANGE_ENABLED);
        doReturn(false).when(mMockUxStatesManager).getFlag(KEY_NOTIFICATION_DISMISSED_ON_CLICK);

        final String expectedTitle =
                mContext.getString(R.string.notificationUI_notification_ga_title_eu);
        final String expectedContent =
                mContext.getString(R.string.notificationUI_notification_ga_content_eu);

        ConsentNotificationTrigger.showConsentNotification(mContext, true);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        verify(mAdServicesLogger, times(2)).logUIStats(any());

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).recordTopicsDefaultConsent(false);
        verify(mConsentManager).recordFledgeDefaultConsent(false);
        verify(mConsentManager).recordMeasurementDefaultConsent(false);
        verify(mConsentManager).disable(mContext, AdServicesApiType.FLEDGE);
        verify(mConsentManager).disable(mContext, AdServicesApiType.TOPICS);
        verify(mConsentManager).disable(mContext, AdServicesApiType.MEASUREMENTS);
        verify(mConsentManager).recordGaUxNotificationDisplayed(true);

        assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
        final Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags)
                .isEqualTo(Notification.FLAG_ONGOING_EVENT);
        assertThat(Notification.FLAG_NO_CLEAR & notification.flags)
                .isEqualTo(Notification.FLAG_NO_CLEAR);
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags).isEqualTo(0);
        assertThat(notification.actions).isNull();

        // verify that notification was displayed
        sDevice.openNotification();
        sDevice.wait(Until.hasObject(By.pkg("com.android.systemui")), LAUNCH_TIMEOUT);
        UiObject scroller =
                sDevice.findObject(
                        new UiSelector()
                                .packageName("com.android.systemui")
                                .resourceId("com.android.systemui:id/notification_stack_scroller"));

        // there might be only one notification and no scroller exists.
        UiObject notificationCard;
        // notification card title might be cut off, so check for first portion of title
        UiSelector notificationCardSelector =
                new UiSelector()
                        .textContains(
                                getString(R.string.notificationUI_notification_ga_title_eu)
                                        .substring(0, 15));
        if (scroller.exists()) {
            notificationCard = scroller.getChild(notificationCardSelector);
        } else {
            notificationCard = sDevice.findObject(notificationCardSelector);
        }
        notificationCard.waitForExists(LAUNCH_TIMEOUT);
        assertThat(notificationCard.exists()).isTrue();

        // click the notification and verify that notification still exists (wasn't dismissed)
        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);

        // go to confirmation page and verify that notification was dismissed
        UiObject leftControlButton =
                getPageElement(sDevice, R.string.notificationUI_left_control_button_text_eu);
        UiObject rightControlButton =
                getPageElement(sDevice, R.string.notificationUI_right_control_button_ga_text_eu);
        UiObject moreButton = getPageElement(sDevice, R.string.notificationUI_more_button_text);
        verifyControlsAndMoreButtonAreDisplayed(leftControlButton, rightControlButton, moreButton);
        Thread.sleep(LAUNCH_TIMEOUT);
        rightControlButton.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        assertThat(mNotificationManager.getActiveNotifications()).hasLength(0);
    }

    @Test
    public void testNotificationsDisabled() {
        doReturn(false).when(mMockUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(BETA_UX).when(mMockUxStatesManager).getUx();

        doReturn(mNotificationManagerCompat).when(() -> NotificationManagerCompat.from(mContext));
        doReturn(false).when(mNotificationManagerCompat).areNotificationsEnabled();

        ConsentNotificationTrigger.showConsentNotification(mContext, true);

        verify(mAdServicesLogger, times(2)).logUIStats(any());

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        verify(mConsentManager).recordNotificationDisplayed(true);
    }

    @Test
    public void testRowNotification_rvcUxFlagEnabled()
            throws InterruptedException, UiObjectNotFoundException {
        testRvcUxNotification(false);
    }

    @Test
    public void testEuNotification_rvcUxFlagEnabled()
            throws InterruptedException, UiObjectNotFoundException {
        testRvcUxNotification(true);
    }

    private void testRvcUxNotification(boolean isEeaDevice)
            throws InterruptedException, UiObjectNotFoundException {
        doReturn(isEeaDevice).when(mMockFlags).isEeaDevice();
        doReturn(true).when(mMockFlags).getEnableAdServicesSystemApi();
        doReturn(true).when(mMockUxStatesManager).getFlag(KEY_RVC_UX_ENABLED);
        doReturn(RVC_UX).when(mMockUxStatesManager).getUx();
        doReturn(RvcUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL)
                .when(mMockUxStatesManager)
                .getEnrollmentChannel();

        final String expectedTitle =
                mContext.getString(R.string.notificationUI_u18_notification_title);
        final String expectedContent =
                mContext.getString(R.string.notificationUI_u18_notification_content);

        ConsentNotificationTrigger.showConsentNotification(mContext, isEeaDevice);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        ArgumentCaptor<UIStats> argument = ArgumentCaptor.forClass(UIStats.class);
        verify(mAdServicesLogger, times(2)).logUIStats(argument.capture());

        assertThat(argument.getValue().getCode()).isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED);
        if (isEeaDevice) {
            assertThat(argument.getValue().getRegion())
                    .isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU);
            assertThat(argument.getValue().getDefaultConsent())
                    .isEqualTo(
                            AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__MEASUREMENT_DEFAULT_OPT_OUT);
        } else {
            assertThat(argument.getValue().getRegion())
                    .isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW);
            assertThat(argument.getValue().getDefaultConsent())
                    .isEqualTo(
                            AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__MEASUREMENT_DEFAULT_OPT_OUT);
        }
        assertThat(argument.getValue().getDefaultAdIdState())
                .isEqualTo(
                        AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__AD_ID_DISABLED);
        assertThat(argument.getValue().getUx())
                .isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__RVC_UX);
        assertThat(argument.getValue().getEnrollmentChannel())
                .isEqualTo(
                        AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__FIRST_CONSENT_NOTIFICATION_CHANNEL);

        verify(mConsentManager, times(2)).getMeasurementDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        if (isEeaDevice) {
            verify(mConsentManager).recordMeasurementDefaultConsent(false);
            verify(mConsentManager).disable(mContext, AdServicesApiType.MEASUREMENTS);
        } else {
            verify(mConsentManager).recordMeasurementDefaultConsent(true);
            verify(mConsentManager).enable(mContext, AdServicesApiType.MEASUREMENTS);
        }
        verify(mConsentManager).setU18NotificationDisplayed(true);

        assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
        final Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags)
                .isEqualTo(Notification.FLAG_AUTO_CANCEL);

        sDevice.openNotification();
        sDevice.wait(Until.hasObject(By.pkg("com.android.systemui")), LAUNCH_TIMEOUT);

        UiObject scroller =
                sDevice.findObject(
                        new UiSelector()
                                .packageName("com.android.systemui")
                                .resourceId("com.android.systemui:id/notification_stack_scroller"));
        assertThat(scroller.exists()).isTrue();
        UiObject notificationCard =
                scroller.getChild(
                        new UiSelector()
                                .text(getString(R.string.notificationUI_u18_notification_title)));
        assertThat(notificationCard.exists()).isTrue();

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        assertThat(mNotificationManager.getActiveNotifications()).hasLength(0);
    }

    @Test
    public void testRvcPostOtaRowNotification()
            throws InterruptedException, UiObjectNotFoundException {
        testRvcPostOtaNotification(false);
    }

    @Test
    public void testRvcPostOtaEuNotification()
            throws InterruptedException, UiObjectNotFoundException {
        testRvcPostOtaNotification(true);
    }

    private void testRvcPostOtaNotification(boolean isEeaDevice)
            throws InterruptedException, UiObjectNotFoundException {
        doReturn(isEeaDevice).when(mMockFlags).isEeaDevice();
        doReturn(true).when(mMockFlags).getEnableAdServicesSystemApi();
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(true).when(mMockUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        // Rvc users have GA Ux on S+ after OTA
        doReturn(GA_UX).when(mMockUxStatesManager).getUx();
        // Rvc users have RVC_POST_OTA_CHANNEL on S+ after OTA
        doReturn(GaUxEnrollmentChannelCollection.RVC_POST_OTA_CHANNEL)
                .when(mMockUxStatesManager)
                .getEnrollmentChannel();

        final String expectedTitle =
                mContext.getString(
                        isEeaDevice
                                ? R.string.notificationUI_notification_ga_title_eu
                                : R.string.notificationUI_notification_ga_title);
        final String expectedContent =
                mContext.getString(
                        isEeaDevice
                                ? R.string.notificationUI_notification_ga_content_eu
                                : R.string.notificationUI_notification_ga_content);

        ConsentNotificationTrigger.showConsentNotification(mContext, isEeaDevice);
        Thread.sleep(1000); // wait 1s to make sure that Notification is displayed.

        ArgumentCaptor<UIStats> argument = ArgumentCaptor.forClass(UIStats.class);
        verify(mAdServicesLogger, times(2)).logUIStats(argument.capture());

        assertThat(argument.getValue().getCode()).isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED);
        if (isEeaDevice) {
            assertThat(argument.getValue().getRegion())
                    .isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU);
            assertThat(argument.getValue().getDefaultConsent())
                    .isEqualTo(
                            AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__PP_API_DEFAULT_OPT_OUT);
        } else {
            assertThat(argument.getValue().getRegion())
                    .isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW);
            assertThat(argument.getValue().getDefaultConsent())
                    .isEqualTo(
                            AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__PP_API_DEFAULT_OPT_OUT);
        }
        assertThat(argument.getValue().getDefaultAdIdState())
                .isEqualTo(
                        AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__AD_ID_DISABLED);
        assertThat(argument.getValue().getUx())
                .isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__GA_UX);
        assertThat(argument.getValue().getEnrollmentChannel())
                .isEqualTo(
                        AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__RVC_POST_OTA_NOTIFICATION_CHANNEL);

        verify(mConsentManager, times(2)).getDefaultConsent();
        verify(mConsentManager, times(2)).getDefaultAdIdState();
        if (isEeaDevice) {
            verify(mConsentManager).recordTopicsDefaultConsent(false);
            verify(mConsentManager).recordFledgeDefaultConsent(false);
            verify(mConsentManager).recordMeasurementDefaultConsent(false);
            verify(mConsentManager).disable(mContext, AdServicesApiType.FLEDGE);
            verify(mConsentManager).disable(mContext, AdServicesApiType.TOPICS);
            verify(mConsentManager).disable(mContext, AdServicesApiType.MEASUREMENTS);
        } else {
            verify(mConsentManager).recordTopicsDefaultConsent(true);
            verify(mConsentManager).recordFledgeDefaultConsent(true);
            verify(mConsentManager).recordMeasurementDefaultConsent(true);
            verify(mConsentManager).enable(mContext, AdServicesApiType.MEASUREMENTS);
            verify(mConsentManager).enable(mContext, AdServicesApiType.TOPICS);
            verify(mConsentManager).enable(mContext, AdServicesApiType.FLEDGE);
        }
        verify(mConsentManager).recordGaUxNotificationDisplayed(true);

        assertThat(mNotificationManager.getActiveNotifications()).hasLength(1);
        final Notification notification =
                mNotificationManager.getActiveNotifications()[0].getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                .isEqualTo(expectedTitle);
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                .isEqualTo(expectedContent);
        if (isEeaDevice) {
            assertThat(Notification.FLAG_ONGOING_EVENT & notification.flags)
                .isEqualTo(Notification.FLAG_ONGOING_EVENT);
            assertThat(Notification.FLAG_NO_CLEAR & notification.flags)
                .isEqualTo(Notification.FLAG_NO_CLEAR);
        }
        assertThat(Notification.FLAG_AUTO_CANCEL & notification.flags)
                .isEqualTo(Notification.FLAG_AUTO_CANCEL);

        sDevice.openNotification();
        sDevice.wait(Until.hasObject(By.pkg("com.android.systemui")), LAUNCH_TIMEOUT);

        UiObject scroller =
                sDevice.findObject(
                        new UiSelector()
                                .packageName("com.android.systemui")
                                .resourceId("com.android.systemui:id/notification_stack_scroller"));
        assertThat(scroller.exists()).isTrue();
        UiObject notificationCard =
                scroller.getChild(
                        new UiSelector()
                                .text(
                                        getString(
                                                isEeaDevice
                                                        ? R.string
                                                                .notificationUI_notification_ga_title_eu
                                                        : R.string
                                                                .notificationUI_notification_ga_title)));
        assertThat(notificationCard.exists()).isTrue();

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT);
        assertThat(mNotificationManager.getActiveNotifications()).hasLength(0);
    }

    private void verifyControlsAndMoreButtonAreDisplayed(
            UiObject leftControlButton, UiObject rightControlButton, UiObject moreButton)
            throws UiObjectNotFoundException, InterruptedException {
        UiObject scrollView =
                sDevice.findObject(new UiSelector().className("android.widget.ScrollView"));

        if (scrollView.isScrollable()) {
            assertThat(leftControlButton.exists()).isFalse();
            assertThat(rightControlButton.exists()).isFalse();
            assertThat(moreButton.exists()).isTrue();

            while (moreButton.exists()) {
                moreButton.click();
                Thread.sleep(2000);
            }
        }
    }

    private void cancelAllPreviousNotifications() {
        if (mNotificationManager.getActiveNotifications().length > 0) {
            mNotificationManager.cancelAll();
        }
    }
}
