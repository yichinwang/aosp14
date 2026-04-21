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

package com.android.adservices.service.measurement.reporting;

import static com.android.adservices.service.Flags.MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;

import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.TriggerSpec;
import com.android.adservices.service.measurement.TriggerSpecs;
import com.android.adservices.service.measurement.TriggerSpecsUtil;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public class EventReportWindowCalcDelegateTest {
    private static final String MALFORMED_WINDOW_CONFIG = "malformedCfg1,malformedCfg2";
    private static final String VALID_1H_1D_WINDOW_CONFIG = "3600,86400";
    private static final String VALID_2H_2D_WINDOW_CONFIG = "7200,172800";
    /** It's invalid because only 2 early reporting windows are permitted. */
    private static final String INVALID_1H_1D_2D_WINDOW_CONFIG = "3600,86400,172800";

    private static final String EVENT_REPORT_WINDOWS_1_WINDOW_NO_START =
            "{'end_times': [172800000]}";

    private static final String EVENT_REPORT_WINDOWS_1_WINDOW_WITH_START =
            "{ 'start_time': 86400000, 'end_times': [172800000]}";

    private static final String EVENT_REPORT_WINDOWS_2_WINDOWS_NO_START =
            "{'end_times': [172800000, 432000000]}";

    private static final String EVENT_REPORT_WINDOWS_5_WINDOWS_WITH_START =
            "{'start_time': 86400000, 'end_times': [172800000, 432000000, 604800000, 864000000,"
                    + " 1728000000]}";

    @Mock private Flags mFlags;

    EventReportWindowCalcDelegate mEventReportWindowCalcDelegate;

    @Before
    public void setup() {
        doReturn(false).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                .when(mFlags).getMeasurementMinEventReportDelayMillis();
        mEventReportWindowCalcDelegate = new EventReportWindowCalcDelegate(mFlags);
    }

    @Test
    public void getReportingTime_eventSourceAppDestination() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_overridesMinEventReportDelay() {
        long minDelayMillis = 2300L;
        doReturn(minDelayMillis).when(mFlags).getMeasurementMinEventReportDelayMillis();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                expiryTime + minDelayMillis,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_eventSrcInstallAttributedAppDestinationTrigger1stWindow() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_eventSrcInstallAttributedAppDestinationTrigger2ndWindow() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_eventSrcInstallAttributedWebDestinationTrigger1stWindow() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.WEB));
    }

    @Test
    public void getReportingTime_eventSrcInstallAttributedWebDestinationTrigger2ndWindow() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.WEB));
    }

    @Test
    public void getReportingTime_eventSourceWebDestination() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.WEB));
    }

    @Test
    public void getReportingTime_navigationSourceTriggerInFirstWindow() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_navigationSourceTriggerInSecondWindow() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[1]
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_navigationSecondExpiry() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(2);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceExpiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_navigationLast() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(1);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(20);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceExpiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void testMaxReportCount() {
        Source eventSourceInstallNotAttributed =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallAttributed(false)
                        .build();
        assertEquals(
                PrivacyParams.EVENT_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        eventSourceInstallNotAttributed, false));
        assertEquals(
                PrivacyParams.EVENT_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        eventSourceInstallNotAttributed, false));

        Source navigationSourceInstallNotAttributed =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallAttributed(false)
                        .build();
        assertEquals(
                PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        navigationSourceInstallNotAttributed, false));
        assertEquals(
                PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        navigationSourceInstallNotAttributed, false));

        Source eventSourceInstallAttributed =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                PrivacyParams.INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        eventSourceInstallAttributed, true));
        // Install attribution state does not matter for web destination
        assertEquals(
                PrivacyParams.EVENT_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        eventSourceInstallAttributed, false));

        Source navigationSourceInstallAttributed =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        navigationSourceInstallAttributed, true));
        assertEquals(
                PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        navigationSourceInstallAttributed, true));
    }

    @Test
    public void getMaxReportCount_configuredConversionsNonInstall_returnsConfiguredCount() {
        // Setup
        doReturn(true).when(mFlags).getMeasurementEnableVtcConfigurableMaxEventReports();
        doReturn(3).when(mFlags).getMeasurementVtcConfigurableMaxEventReportsCount();
        Source nonInstallEventSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallAttributed(false)
                        .build();

        // Execution & assertion
        Assert.assertEquals(
                3, mEventReportWindowCalcDelegate.getMaxReportCount(nonInstallEventSource, false));
    }

    @Test
    public void getMaxReportCount_configuredConversionsInstallCase_returnsConfiguredCount() {
        // Setup
        doReturn(true).when(mFlags).getMeasurementEnableVtcConfigurableMaxEventReports();
        doReturn(2).when(mFlags).getMeasurementVtcConfigurableMaxEventReportsCount();
        Source installEventSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallAttributed(true)
                        .build();

        // Execution & assertion
        Assert.assertEquals(
                2, mEventReportWindowCalcDelegate.getMaxReportCount(installEventSource, true));
    }

    @Test
    public void getMaxReportCount_configuredConversionsToOneInstallCase_incrementConfiguredCount() {
        // Setup
        doReturn(true).when(mFlags).getMeasurementEnableVtcConfigurableMaxEventReports();
        doReturn(1).when(mFlags).getMeasurementVtcConfigurableMaxEventReportsCount();
        Source installEventSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallAttributed(true)
                        .build();

        // Execution & assertion
        Assert.assertEquals(
                2, mEventReportWindowCalcDelegate.getMaxReportCount(installEventSource, true));
    }

    @Test
    public void getMaxReportCount_configuredConversionsToOneInstallCase_noEffectOnCtc() {
        // Setup
        doReturn(true).when(mFlags).getMeasurementEnableVtcConfigurableMaxEventReports();
        doReturn(2).when(mFlags).getMeasurementVtcConfigurableMaxEventReportsCount();
        Source navigationSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallAttributed(false)
                        .build();

        // Execution & assertion
        Assert.assertEquals(
                3, mEventReportWindowCalcDelegate.getMaxReportCount(navigationSource, false));
    }

    @Test
    public void noiseReportingTimeByIndex_event() {
        long eventTime = System.currentTimeMillis();

        // Expected: 1 window at expiry
        Source eventSource10d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(10))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(10) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(10) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 1, /* isInstallCase */ false));

        // Expected: 1 window at expiry
        Source eventSource7d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(7))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource7d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource7d, /* windowIndex= */ 1, /* isInstallCase */ false));

        // Expected: 1 window at expiry
        Source eventSource2d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(2))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource2d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource2d, /* windowIndex= */ 1, /* isInstallCase */ false));
    }

    @Test
    public void getReportingTimeForNoising_overridesMinEventReportDelay() {
        long minDelayMillis = 2300L;
        long eventTime = System.currentTimeMillis();

        doReturn(minDelayMillis).when(mFlags).getMeasurementMinEventReportDelayMillis();

        // Expected: 1 window at expiry
        Source eventSource10d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(10))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(10) + minDelayMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(10) + minDelayMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 1, /* isInstallCase */ false));
    }

    @Test
    public void getReportingTimeForNoising_eventSrcWithConfiguredReportingWindows() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(VALID_2H_2D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long eventTime = System.currentTimeMillis();

        // Expected: 1 window at expiry
        Source eventSource10d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(10))
                        .build();
        assertEquals(
                eventTime + TimeUnit.HOURS.toMillis(1) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(1) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 1, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(10) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 2, /* isInstallCase */ false));
    }

    @Test
    public void getReportingTimeForNoising_eventSrcInstallAttWithConfiguredReportingWindows() {
        // Addition another window for install attribution is ignored when configurable windows
        // are applied.
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(VALID_2H_2D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long eventTime = System.currentTimeMillis();

        // Expected: 1 window at expiry
        long expiry = eventTime + TimeUnit.DAYS.toMillis(10);
        Source eventSource10d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(expiry)
                        .setInstallCooldownWindow(expiry)
                        .build();
        assertEquals(
                eventTime + TimeUnit.HOURS.toMillis(1) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 0, /* isInstallCase */ true));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(1) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 1, /* isInstallCase */ true));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(10) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 2, /* isInstallCase */ true));
    }

    @Test
    public void getReportingTimeForNoising_navigationSrcWithConfiguredReportingWindows() {
        // Addition another window for install attribution is ignored when configurable windows
        // are applied.
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(VALID_2H_2D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long eventTime = System.currentTimeMillis();

        // Expected: 1 window at expiry
        Source eventSource10d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(10))
                        .build();
        assertEquals(
                eventTime + TimeUnit.HOURS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 1, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(10) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 2, /* isInstallCase */ false));
    }

    @Test
    public void getReportingTimeForNoising_navigationSrcInstallAttWithConfiguredReportingWindows() {
        // Addition another window for install attribution is ignored when configurable windows
        // are applied.
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(VALID_2H_2D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long eventTime = System.currentTimeMillis();

        // Expected: 1 window at expiry
        long expiry = eventTime + TimeUnit.DAYS.toMillis(10);
        Source eventSource10d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(eventTime)
                        .setEventReportWindow(expiry)
                        .setInstallCooldownWindow(expiry)
                        .build();
        assertEquals(
                eventTime + TimeUnit.HOURS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 0, /* isInstallCase */ true));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 1, /* isInstallCase */ true));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(10) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 2, /* isInstallCase */ true));
    }

    @Test
    public void reportingTimeByIndex_eventWithInstallAttribution() {
        long eventTime = System.currentTimeMillis();

        // Expected: 2 windows at 2d, expiry(10d)
        Source eventSource10d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(10))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 0, /* isInstallCase */ true));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(10) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 1, /* isInstallCase */ true));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(10) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 2, /* isInstallCase */ true));

        // Expected: 1 window at 2d(expiry)
        Source eventSource2d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(2))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource2d, /* windowIndex= */ 0, /* isInstallCase */ true));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource2d, /* windowIndex= */ 1, /* isInstallCase */ true));
    }

    @Test
    public void reportingTimeByIndex_navigation() {
        long eventTime = System.currentTimeMillis();

        // Expected: 3 windows at 2d, 7d & expiry(20d)
        Source navigationSource20d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(20))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource20d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource20d, /* windowIndex= */ 1, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(20) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource20d, /* windowIndex= */ 2, /* isInstallCase */ false));

        // Expected: 2 windows at 2d & expiry(7d)
        Source navigationSource7d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(7))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource7d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource7d, /* windowIndex= */ 1, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource7d, /* windowIndex= */ 2, /* isInstallCase */ false));

        // Expected: 1 window at 2d(expiry)
        Source navigationSource2d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(2))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource2d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource2d, /* windowIndex= */ 1, /* isInstallCase */ false));
    }

    @Test
    public void reportingTimeByIndex_navigationWithInstallAttribution() {
        long eventTime = System.currentTimeMillis();

        // Expected: 3 windows at 2d, 7d & expiry(20d)
        Source navigationSource20d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(20))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource20d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource20d, /* windowIndex= */ 1, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(20) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource20d, /* windowIndex= */ 2, /* isInstallCase */ false));

        // Expected: 2 windows at 2d & expiry(7d)
        Source navigationSource7d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(7))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource7d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource7d, /* windowIndex= */ 1, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource7d, /* windowIndex= */ 2, /* isInstallCase */ false));

        // Expected: 1 window at 2d(expiry)
        Source navigationSource2d =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(2))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource2d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource2d, /* windowIndex= */ 1, /* isInstallCase */ false));
    }

    @Test
    public void getReportingTime_nullWindowConfigEventSrcAppDest_fallbackToDefault() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_nullWindowConfigEventSrcInstallAttAppDestTrigger1stWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_nullWindowConfigEventSrcInstallAttAppDestTrigger2ndWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_nullWindowConfigEventSrcInstallAttWebDestTrigger1stWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.WEB));
    }

    @Test
    public void getReportingTime_nullWindowConfigEventSrcInstallAttWebDestTrigger2ndWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.WEB));
    }

    @Test
    public void getReportingTime_nullWindowConfigEventSourceWebDestination_fallbackToDefault() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.WEB));
    }

    @Test
    public void getReportingTime_nullWindowConfigNavigationSourceTriggerInFirstWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_emptyWindowConfigNavigationSourceTriggerNextHour() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn("").when(mFlags).getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn("").when(mFlags).getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.HOURS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceExpiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_emptyWindowConfigEventSrcTriggerNextHour() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn("").when(mFlags).getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn("").when(mFlags).getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.HOURS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceExpiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_emptyWindowConfigEventSrcInstallAttTriggerNextHour() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn("").when(mFlags).getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn("").when(mFlags).getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.HOURS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                sourceEventTime
                        + TimeUnit.DAYS.toMillis(2)
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_emptyWindowConfigNavigationSrcInstallAttTriggerNextHour() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn("").when(mFlags).getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn("").when(mFlags).getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.HOURS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                sourceExpiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_nullWindowConfigNavigationSourceTriggerInSecondWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[1]
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_nullWindowConfigNavigationSecondExpiry() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(2);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceExpiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_nullWindowConfigNavigationLast_fallbackToDefault() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(null).when(mFlags).getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(1);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(20);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceExpiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_MalformedWindowConfigEventSourceAppDestination_returnsDefault() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(MALFORMED_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(MALFORMED_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_MalformedWindowConfigEventSrcInstallAttAppDestTrigger1stWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(MALFORMED_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_MalformedWindowConfigEventSrcInstallAttAppDestTrigger2ndWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(MALFORMED_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_MalformedWindowConfigEventSrcInstallAttWebDestTrigger1stWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(MALFORMED_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.WEB));
    }

    @Test
    public void getReportingTime_MalformedWindowConfigEventSrcInstallAttWebDestTrigger2ndWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(MALFORMED_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.WEB));
    }

    @Test
    public void getReportingTime_MalformedWindowConfigEventSourceWebDestination() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(MALFORMED_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.WEB));
    }

    @Test
    public void getReportingTime_MalformedWindowConfigNavigationSourceTriggerInFirstWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(MALFORMED_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_MalformedWindowConfigNavigationSourceTriggerInSecondWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(MALFORMED_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[1]
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_MalformedWindowConfigNavigationSecondExpiry_fallbackToDefault() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(MALFORMED_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(2);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceExpiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_MalformedWindowConfigNavigationLast_fallbackToDefault() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(MALFORMED_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(1);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(20);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceExpiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_validWindowConfigEventSourceTriggerIn1stWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(VALID_2H_2D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.MINUTES.toMillis(30);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceEventTime + TimeUnit.HOURS.toMillis(2),
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_validWindowConfigEventSourceTriggerIn2ndWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(VALID_2H_2D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.HOURS.toMillis(2);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceEventTime + TimeUnit.DAYS.toMillis(1)
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_validWindowConfigEventSourceTriggerInLastWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(MALFORMED_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(20);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(2);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_validWindowConfigNavigationSourceTriggerIn1stWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(VALID_2H_2D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.MINUTES.toMillis(30);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceEventTime + TimeUnit.HOURS.toMillis(3),
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_validWindowConfigNavigationSourceTriggerIn2ndWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(VALID_2H_2D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(20);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceEventTime + TimeUnit.DAYS.toMillis(2)
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_validWindowConfigNavigationSourceTriggerInLastWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(MALFORMED_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(VALID_2H_2D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(20);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(5);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_invalidWindowConfigEventSourceTriggerIn1stWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(INVALID_1H_1D_2D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(VALID_2H_2D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(10);
        long sourceEventTime = triggerTime - TimeUnit.MINUTES.toMillis(30);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                expiryTime + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_invalidWindowConfigNavigationSourceTriggerIn1stWindow() {
        doReturn(true).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        doReturn(VALID_1H_1D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsVtcEarlyReportingWindows();
        doReturn(INVALID_1H_1D_2D_WINDOW_CONFIG)
                .when(mFlags)
                .getMeasurementEventReportsCtcEarlyReportingWindows();
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(10);
        long sourceEventTime = triggerTime - TimeUnit.MINUTES.toMillis(30);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceEventTime + TimeUnit.DAYS.toMillis(2)
                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTimeForNoisingFlexEventApi_validTime_equal() throws JSONException {
        TriggerSpecs testObject1 = new TriggerSpecs(
                SourceFixture.getTriggerSpecValueCountJSONTwoTriggerSpecs(), 3, null);
        // Assertion
        assertEquals(new UnsignedLong(1L), testObject1.getTriggerDataFromIndex(0));
        assertEquals(new UnsignedLong(3L), testObject1.getTriggerDataFromIndex(2));
        assertEquals(new UnsignedLong(5L), testObject1.getTriggerDataFromIndex(4));
        assertEquals(new UnsignedLong(7L), testObject1.getTriggerDataFromIndex(6));
        assertEquals(
                TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoisingFlexEventApi(
                        0, 0, testObject1));
        assertEquals(
                TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoisingFlexEventApi(
                        0, 1, testObject1));
        assertEquals(
                TimeUnit.DAYS.toMillis(3) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoisingFlexEventApi(
                        0, 4, testObject1));
        assertEquals(
                TimeUnit.DAYS.toMillis(30) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoisingFlexEventApi(
                        2, 0, testObject1));
        assertEquals(
                TimeUnit.DAYS.toMillis(7) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoisingFlexEventApi(
                        1, 0, testObject1));
        assertThrows(
                IndexOutOfBoundsException.class,
                () ->
                        mEventReportWindowCalcDelegate.getReportingTimeForNoisingFlexEventApi(
                                1, 5, testObject1));
    }

    @Test
    public void getFlexEventReportingTime_triggerTimeEarlierThanSourceTime_signalsInvalid()
            throws JSONException {
        TriggerSpecs testTriggerSpecs =
                new TriggerSpecs(
                        SourceFixture.getTriggerSpecArrayCountValidBaseline(), 3, null);
        assertEquals(
                -1,
                mEventReportWindowCalcDelegate.getFlexEventReportingTime(
                        testTriggerSpecs, 10000, 9999, new UnsignedLong(1L)));
    }

    @Test
    public void getFlexEventReportingTime_triggerTimeEarlierThanReportWindowStart_signalsInvalid()
            throws JSONException {
        JSONObject jsonTriggerSpec = new JSONObject();
        jsonTriggerSpec.put("trigger_data", new JSONArray(new int[] {1, 2, 3, 4}));
        JSONObject windows = new JSONObject();
        windows.put("start_time", 1000);
        windows.put("end_times", new JSONArray(new int[] {10000, 20000, 30000, 40000}));
        jsonTriggerSpec.put("event_report_windows", windows);
        jsonTriggerSpec.put("summary_buckets", new JSONArray(new int[] {1, 10, 100}));
        TriggerSpec[] triggerSpecArray = TriggerSpecsUtil.triggerSpecArrayFrom(
                new JSONArray(new JSONObject[] {jsonTriggerSpec}).toString());
        TriggerSpecs testTriggerSpecs = new TriggerSpecs(triggerSpecArray, 3, null);

        // Assertion
        assertEquals(
                -1,
                mEventReportWindowCalcDelegate.getFlexEventReportingTime(
                        testTriggerSpecs, 10000, 10999, new UnsignedLong(1L)));
    }

    @Test
    public void getFlexEventReportingTime_variousReportWindows_calculatesCorrectly()
            throws JSONException {
        JSONObject jsonTriggerSpec = new JSONObject();
        jsonTriggerSpec.put("trigger_data", new JSONArray(new int[] {1, 2, 3, 4}));
        JSONObject windows = new JSONObject();
        windows.put("start_time", 1000);
        windows.put("end_times", new JSONArray(new int[] {10000, 20000, 30000, 40000}));
        jsonTriggerSpec.put("event_report_windows", windows);
        jsonTriggerSpec.put("summary_buckets", new JSONArray(new int[] {1, 10, 100}));
        TriggerSpec[] triggerSpecArray = TriggerSpecsUtil.triggerSpecArrayFrom(
                new JSONArray(new JSONObject[] {jsonTriggerSpec}).toString());
        TriggerSpecs testTriggerSpecs = new TriggerSpecs(triggerSpecArray, 3, null);

        // Assertion
        assertEquals(
                110000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getFlexEventReportingTime(
                        testTriggerSpecs, 100000, 109999, new UnsignedLong(1L)));
        assertEquals(
                120000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getFlexEventReportingTime(
                        testTriggerSpecs, 100000, 119999, new UnsignedLong(1L)));
        assertEquals(
                130000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getFlexEventReportingTime(
                        testTriggerSpecs, 100000, 129999, new UnsignedLong(1L)));
        assertEquals(
                140000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getFlexEventReportingTime(
                        testTriggerSpecs, 100000, 139999, new UnsignedLong(1L)));
        assertEquals(
                110000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getFlexEventReportingTime(
                        testTriggerSpecs, 100000, 109999, new UnsignedLong(2L)));
        assertEquals(
                120000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getFlexEventReportingTime(
                        testTriggerSpecs, 100000, 119999, new UnsignedLong(2L)));
        assertEquals(
                130000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getFlexEventReportingTime(
                        testTriggerSpecs, 100000, 129999, new UnsignedLong(3L)));
        assertEquals(
                140000 + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getFlexEventReportingTime(
                        testTriggerSpecs, 100000, 139999, new UnsignedLong(4L)));
        assertEquals(
                -1,
                mEventReportWindowCalcDelegate.getFlexEventReportingTime(
                        testTriggerSpecs, 100000, 149999, new UnsignedLong(1L)));
    }

    @Test
    public void getFlexEventReportingTime_overridesMinEventReportDelay() throws JSONException {
        JSONObject jsonTriggerSpec = new JSONObject();
        jsonTriggerSpec.put("trigger_data", new JSONArray(new int[] {1, 2, 3, 4}));
        JSONObject windows = new JSONObject();
        windows.put("start_time", 1000);
        windows.put("end_times", new JSONArray(new int[] {10000, 20000, 30000, 40000}));
        jsonTriggerSpec.put("event_report_windows", windows);
        jsonTriggerSpec.put("summary_buckets", new JSONArray(new int[] {1, 10, 100}));
        TriggerSpec[] triggerSpecArray = TriggerSpecsUtil.triggerSpecArrayFrom(
                new JSONArray(new JSONObject[] {jsonTriggerSpec}).toString());
        TriggerSpecs testTriggerSpecs = new TriggerSpecs(triggerSpecArray, 3, null);

        long minReportDelay = 23000L;
        doReturn(minReportDelay).when(mFlags).getMeasurementMinEventReportDelayMillis();

        long expectedReportTimeWithoutDelay = 110000L;
        long sourceRegistrationTime = 100000L;
        long triggerTime = 109999L;

        // Assertion
        assertEquals(
                expectedReportTimeWithoutDelay + minReportDelay,
                mEventReportWindowCalcDelegate.getFlexEventReportingTime(
                        testTriggerSpecs,
                        sourceRegistrationTime,
                        triggerTime,
                        new UnsignedLong(1L)));
    }

    @Test
    public void getReportingTimeForNoising_flexLiteApi() {
        doReturn(true).when(mFlags).getMeasurementFlexLiteApiEnabled();
        long sourceTime = System.currentTimeMillis();
        Source oneWindowNoStart =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportWindows(EVENT_REPORT_WINDOWS_1_WINDOW_NO_START)
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .build();
        Source oneWindowWithStart =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportWindows(EVENT_REPORT_WINDOWS_1_WINDOW_WITH_START)
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .build();
        Source twoWindowsNoStart =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportWindows(EVENT_REPORT_WINDOWS_2_WINDOWS_NO_START)
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .build();
        Source fiveWindowsWithStart =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportWindows(EVENT_REPORT_WINDOWS_5_WINDOWS_WITH_START)
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .build();

        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        oneWindowNoStart, 0, false));
        // InstallCase doesn't affect the report time
        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        oneWindowNoStart, 0, true));

        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        oneWindowWithStart, 0, false));

        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        twoWindowsNoStart, 0, false));
        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(5) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        twoWindowsNoStart, 1, false));

        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        fiveWindowsWithStart, 0, false));
        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(5) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        fiveWindowsWithStart, 1, false));

        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(7) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        fiveWindowsWithStart, 2, false));
        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(10) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        fiveWindowsWithStart, 3, false));

        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(20) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        fiveWindowsWithStart, 4, false));
    }

    @Test
    public void getReportingTime_flexLiteApi() {
        doReturn(true).when(mFlags).getMeasurementFlexLiteApiEnabled();
        long sourceTime = System.currentTimeMillis();
        Source oneWindowNoStart =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportWindows(EVENT_REPORT_WINDOWS_1_WINDOW_NO_START)
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .build();
        Source oneWindowWithStart =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportWindows(EVENT_REPORT_WINDOWS_1_WINDOW_WITH_START)
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .build();
        Source twoWindowsNoStart =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportWindows(EVENT_REPORT_WINDOWS_2_WINDOWS_NO_START)
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .build();
        Source fiveWindowsWithStart =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportWindows(EVENT_REPORT_WINDOWS_5_WINDOWS_WITH_START)
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .build();

        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        oneWindowNoStart, sourceTime + 1, EventSurfaceType.APP));
        // InstallCase doesn't affect the report time
        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        oneWindowNoStart, sourceTime + 1, EventSurfaceType.APP));

        // Trigger before start time
        assertEquals(
                -1,
                mEventReportWindowCalcDelegate.getReportingTime(
                        oneWindowWithStart, sourceTime + 1, EventSurfaceType.APP));
        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        oneWindowWithStart,
                        sourceTime + TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(6),
                        EventSurfaceType.APP));

        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        twoWindowsNoStart, sourceTime + 1, EventSurfaceType.APP));
        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(5) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        twoWindowsNoStart,
                        sourceTime + TimeUnit.DAYS.toMillis(3),
                        EventSurfaceType.APP));

        // Trigger before start time
        assertEquals(
                -1,
                mEventReportWindowCalcDelegate.getReportingTime(
                        fiveWindowsWithStart, sourceTime + 1, EventSurfaceType.APP));

        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        fiveWindowsWithStart,
                        sourceTime + TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(6),
                        EventSurfaceType.APP));
        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(7) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        fiveWindowsWithStart,
                        sourceTime + TimeUnit.DAYS.toMillis(5),
                        EventSurfaceType.APP));

        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(7) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        fiveWindowsWithStart,
                        sourceTime + TimeUnit.DAYS.toMillis(6),
                        EventSurfaceType.APP));
        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(10) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        fiveWindowsWithStart,
                        sourceTime + TimeUnit.DAYS.toMillis(9),
                        EventSurfaceType.APP));

        assertEquals(
                sourceTime + TimeUnit.DAYS.toMillis(20) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                mEventReportWindowCalcDelegate.getReportingTime(
                        fiveWindowsWithStart,
                        sourceTime + TimeUnit.DAYS.toMillis(15),
                        EventSurfaceType.APP));
    }

    @Test
    public void getMaxReportCount_flexLiteApi() {
        doReturn(true).when(mFlags).getMeasurementFlexLiteApiEnabled();
        doReturn(false).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        long sourceTime = System.currentTimeMillis();
        Source source10Reports =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setMaxEventLevelReports(10)
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .build();
        assertEquals(10, mEventReportWindowCalcDelegate.getMaxReportCount(source10Reports, true));
        assertEquals(10, mEventReportWindowCalcDelegate.getMaxReportCount(source10Reports, false));

        Source sourceDefaultEvent =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportWindows(EVENT_REPORT_WINDOWS_5_WINDOWS_WITH_START)
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .setSourceType(Source.SourceType.EVENT)
                        .build();

        assertEquals(
                PrivacyParams.INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(sourceDefaultEvent, true));
        assertEquals(
                PrivacyParams.EVENT_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(sourceDefaultEvent, false));

        Source sourceDefaultNavigation =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportWindows(EVENT_REPORT_WINDOWS_5_WINDOWS_WITH_START)
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .build();

        assertEquals(
                PrivacyParams.INSTALL_ATTR_NAVIGATION_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(sourceDefaultNavigation, true));
        assertEquals(
                PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(sourceDefaultNavigation, false));
    }

    @Test
    public void getReportingWindowCountForNoising_flexLiteApi() {
        doReturn(true).when(mFlags).getMeasurementFlexLiteApiEnabled();
        doReturn(false).when(mFlags).getMeasurementEnableConfigurableEventReportingWindows();
        long sourceTime = System.currentTimeMillis();
        Source defaultSourceEvent =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .setSourceType(Source.SourceType.EVENT)
                        .build();

        Source defaultSourceNavigation =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .build();

        Source oneWindowNoStart =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportWindows(EVENT_REPORT_WINDOWS_1_WINDOW_NO_START)
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .build();
        Source oneWindowWithStart =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportWindows(EVENT_REPORT_WINDOWS_1_WINDOW_WITH_START)
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .build();
        Source twoWindowsNoStart =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportWindows(EVENT_REPORT_WINDOWS_2_WINDOWS_NO_START)
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .build();
        Source fiveWindowsWithStart =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportWindows(EVENT_REPORT_WINDOWS_5_WINDOWS_WITH_START)
                        .setExpiryTime(sourceTime + TimeUnit.DAYS.toMillis(30))
                        .setEventTime(sourceTime)
                        .build();

        assertEquals(
                2,
                mEventReportWindowCalcDelegate.getReportingWindowCountForNoising(
                        defaultSourceEvent, true));
        assertEquals(
                1,
                mEventReportWindowCalcDelegate.getReportingWindowCountForNoising(
                        defaultSourceEvent, false));

        assertEquals(
                3,
                mEventReportWindowCalcDelegate.getReportingWindowCountForNoising(
                        defaultSourceNavigation, true));
        assertEquals(
                3,
                mEventReportWindowCalcDelegate.getReportingWindowCountForNoising(
                        defaultSourceNavigation, false));

        // InstallCase doesn't affect the report count
        assertEquals(
                1,
                mEventReportWindowCalcDelegate.getReportingWindowCountForNoising(
                        oneWindowNoStart, true));
        assertEquals(
                1,
                mEventReportWindowCalcDelegate.getReportingWindowCountForNoising(
                        oneWindowNoStart, false));

        assertEquals(
                1,
                mEventReportWindowCalcDelegate.getReportingWindowCountForNoising(
                        oneWindowWithStart, true));
        assertEquals(
                1,
                mEventReportWindowCalcDelegate.getReportingWindowCountForNoising(
                        oneWindowWithStart, false));

        assertEquals(
                2,
                mEventReportWindowCalcDelegate.getReportingWindowCountForNoising(
                        twoWindowsNoStart, true));
        assertEquals(
                2,
                mEventReportWindowCalcDelegate.getReportingWindowCountForNoising(
                        twoWindowsNoStart, false));

        assertEquals(
                5,
                mEventReportWindowCalcDelegate.getReportingWindowCountForNoising(
                        fiveWindowsWithStart, true));
        assertEquals(
                5,
                mEventReportWindowCalcDelegate.getReportingWindowCountForNoising(
                        fiveWindowsWithStart, false));
    }
}
