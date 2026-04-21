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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats.DbTransactionStatus.INSERT_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats.DbTransactionType.WRITE_TRANSACTION_TYPE;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats.MethodName.INSERT_KEY;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats.FetchJobType.ENCRYPTION_KEY_DAILY_FETCH_JOB;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats.FetchStatus.IO_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_CONSENT_MIGRATED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENCRYPTION_KEY_DB_TRANSACTION_ENDED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENCRYPTION_KEY_FETCHED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_DATA_STORED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_FAILED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_FILE_DOWNLOADED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_MATCHED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_AD_ID_MATCH_FOR_DEBUG_KEYS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_ATTRIBUTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DEBUG_KEYS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_WIPEOUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_CLICK_VERIFICATION;
import static com.android.adservices.service.stats.EpochComputationClassifierStats.ClassifierType;
import static com.android.adservices.service.stats.EpochComputationClassifierStats.OnDeviceClassifierStatus;
import static com.android.adservices.service.stats.EpochComputationClassifierStats.PrecomputedClassifierStatus;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.android.adservices.service.Flags;
import com.android.adservices.service.enrollment.EnrollmentStatus;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.WipeoutStatus;
import com.android.adservices.service.measurement.attribution.AttributionStatus;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.MockedVoidMethod;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

public class StatsdAdServicesLoggerTest {
    // Atom IDs
    private static final int TOPICS_REPORTED_ATOM_ID = 535;
    private static final int EPOCH_COMPUTATION_CLASSIFIER_ATOM_ID = 537;
    private static final int TOPICS_REPORTED_COMPAT_ATOM_ID = 598;
    private static final int EPOCH_COMPUTATION_CLASSIFIER_COMPAT_ATOM_ID = 599;
    private static final ImmutableList<Integer> TOPIC_IDS = ImmutableList.of(10230, 10227);

    // Test params for GetTopicsReportedStats
    private static final int FILTERED_BLOCKED_TOPIC_COUNT = 0;
    private static final int DUPLICATE_TOPIC_COUNT = 0;
    private static final int TOPIC_IDS_COUNT = 1;
    private static final String SOURCE_REGISTRANT = "android-app://com.registrant";

    private static final GetTopicsReportedStats TOPICS_REPORTED_STATS_DATA =
            GetTopicsReportedStats.builder()
                    .setFilteredBlockedTopicCount(FILTERED_BLOCKED_TOPIC_COUNT)
                    .setDuplicateTopicCount(DUPLICATE_TOPIC_COUNT)
                    .setTopicIdsCount(TOPIC_IDS_COUNT)
                    .setTopicIds(TOPIC_IDS)
                    .build();

    // Test params for EpochComputationClassifierStats
    private static final int BUILD_ID = 8;
    private static final String ASSET_VERSION = "2";

    private static final EpochComputationClassifierStats EPOCH_COMPUTATION_CLASSIFIER_STATS_DATA =
            EpochComputationClassifierStats.builder()
                    .setTopicIds(TOPIC_IDS)
                    .setBuildId(BUILD_ID)
                    .setAssetVersion(ASSET_VERSION)
                    .setClassifierType(ClassifierType.ON_DEVICE_CLASSIFIER)
                    .setOnDeviceClassifierStatus(
                            OnDeviceClassifierStatus.ON_DEVICE_CLASSIFIER_STATUS_SUCCESS)
                    .setPrecomputedClassifierStatus(
                            PrecomputedClassifierStatus.PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED)
                    .build();

    private MockitoSession mMockitoSession;
    private StatsdAdServicesLogger mLogger;
    @Mock private Flags mFlags;

    @Before
    public void setUp() {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(SdkLevel.class)
                        .mockStatic(AdServicesStatsLog.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();

        mLogger = new StatsdAdServicesLogger(mFlags);
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void testLogGetTopicsReportedStats_tPlus() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(false);
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), anyInt(), anyInt(), anyInt(), any(byte[].class)));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), any(int[].class), anyInt(), anyInt(), anyInt()));

        // Invoke logging call
        mLogger.logGetTopicsReportedStats(TOPICS_REPORTED_STATS_DATA);

        // Verify compat logging
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(TOPICS_REPORTED_COMPAT_ATOM_ID),
                                eq(FILTERED_BLOCKED_TOPIC_COUNT),
                                eq(DUPLICATE_TOPIC_COUNT),
                                eq(TOPIC_IDS_COUNT),
                                any(byte[].class)));
        // Verify T+ logging
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                TOPICS_REPORTED_ATOM_ID,
                                TOPIC_IDS.stream().mapToInt(Integer::intValue).toArray(),
                                FILTERED_BLOCKED_TOPIC_COUNT,
                                DUPLICATE_TOPIC_COUNT,
                                TOPIC_IDS_COUNT));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogGetTopicsReportedStats_tPlus_noCompatLoggingDueToKillSwitch() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(true);
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), any(int[].class), anyInt(), anyInt(), anyInt()));

        // Invoke logging call
        mLogger.logGetTopicsReportedStats(TOPICS_REPORTED_STATS_DATA);

        // Verify T+ logging only
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                TOPICS_REPORTED_ATOM_ID,
                                TOPIC_IDS.stream().mapToInt(Integer::intValue).toArray(),
                                FILTERED_BLOCKED_TOPIC_COUNT,
                                DUPLICATE_TOPIC_COUNT,
                                TOPIC_IDS_COUNT));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogGetTopicsReportedStats_sMinus() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(false);
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), anyInt(), anyInt(), anyInt(), any(byte[].class)));

        // Invoke logging call
        mLogger.logGetTopicsReportedStats(TOPICS_REPORTED_STATS_DATA);

        // Verify only compat logging took place
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(TOPICS_REPORTED_COMPAT_ATOM_ID),
                                eq(FILTERED_BLOCKED_TOPIC_COUNT),
                                eq(DUPLICATE_TOPIC_COUNT),
                                eq(TOPIC_IDS_COUNT),
                                any(byte[].class)));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogGetTopicsReportedStats_sMinus_noLoggingDueToKillSwitch() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(true);
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastT);

        // Invoke logging call
        mLogger.logGetTopicsReportedStats(TOPICS_REPORTED_STATS_DATA);

        // No compat (and T+) logging should happen
        verifyZeroInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogEpochComputationClassifierStats_tPlus() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(false);
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        any(byte[].class),
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        any(int[].class),
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));

        // Invoke logging call
        mLogger.logEpochComputationClassifierStats(EPOCH_COMPUTATION_CLASSIFIER_STATS_DATA);

        // Verify compat logging
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(EPOCH_COMPUTATION_CLASSIFIER_COMPAT_ATOM_ID),
                                any(byte[].class), // topic ids converted into byte[]
                                eq(BUILD_ID),
                                eq(ASSET_VERSION),
                                eq(ClassifierType.ON_DEVICE_CLASSIFIER.getCompatLoggingValue()),
                                eq(
                                        OnDeviceClassifierStatus.ON_DEVICE_CLASSIFIER_STATUS_SUCCESS
                                                .getCompatLoggingValue()),
                                eq(
                                        PrecomputedClassifierStatus
                                                .PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED
                                                .getCompatLoggingValue())));
        // Verify T+ logging
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                EPOCH_COMPUTATION_CLASSIFIER_ATOM_ID,
                                TOPIC_IDS.stream().mapToInt(Integer::intValue).toArray(),
                                BUILD_ID,
                                ASSET_VERSION,
                                ClassifierType.ON_DEVICE_CLASSIFIER.getLoggingValue(),
                                OnDeviceClassifierStatus.ON_DEVICE_CLASSIFIER_STATUS_SUCCESS
                                        .getLoggingValue(),
                                PrecomputedClassifierStatus
                                        .PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED
                                        .getLoggingValue()));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogEpochComputationClassifierStats_tPlus_noCompatLoggingDueToKillSwitch() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(true);
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        any(int[].class),
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));

        // Invoke logging call
        mLogger.logEpochComputationClassifierStats(EPOCH_COMPUTATION_CLASSIFIER_STATS_DATA);

        // Verify T+ logging
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                EPOCH_COMPUTATION_CLASSIFIER_ATOM_ID,
                                TOPIC_IDS.stream().mapToInt(Integer::intValue).toArray(),
                                BUILD_ID,
                                ASSET_VERSION,
                                ClassifierType.ON_DEVICE_CLASSIFIER.getLoggingValue(),
                                OnDeviceClassifierStatus.ON_DEVICE_CLASSIFIER_STATUS_SUCCESS
                                        .getLoggingValue(),
                                PrecomputedClassifierStatus
                                        .PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED
                                        .getLoggingValue()));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogEpochComputationClassifierStats_sMinus() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(false);
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        any(byte[].class),
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));

        // Invoke logging call
        mLogger.logEpochComputationClassifierStats(EPOCH_COMPUTATION_CLASSIFIER_STATS_DATA);

        // Verify only compat logging took place
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(EPOCH_COMPUTATION_CLASSIFIER_COMPAT_ATOM_ID),
                                any(byte[].class), // topic ids converted into byte[]
                                eq(BUILD_ID),
                                eq(ASSET_VERSION),
                                eq(ClassifierType.ON_DEVICE_CLASSIFIER.getCompatLoggingValue()),
                                eq(
                                        OnDeviceClassifierStatus.ON_DEVICE_CLASSIFIER_STATUS_SUCCESS
                                                .getCompatLoggingValue()),
                                eq(
                                        PrecomputedClassifierStatus
                                                .PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED
                                                .getCompatLoggingValue())));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogEpochComputationClassifierStats_sMinus_noLoggingDueToKillSwitch() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(true);
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastT);

        // Invoke logging call
        mLogger.logEpochComputationClassifierStats(EPOCH_COMPUTATION_CLASSIFIER_STATS_DATA);

        // No compat (and T+) logging should happen
        verifyZeroInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logMeasurementDebugKeysMatch_success() {
        when(mFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        when(mFlags.getMeasurementAppPackageNameLoggingAllowlist()).thenReturn(SOURCE_REGISTRANT);
        final String enrollmentId = "EnrollmentId";
        long hashedValue = 5000L;
        long hashLimit = 10000L;
        int attributionType = AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB;
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(enrollmentId)
                        .setMatched(true)
                        .setAttributionType(attributionType)
                        .setDebugJoinKeyHashedValue(hashedValue)
                        .setDebugJoinKeyHashLimit(hashLimit)
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyBoolean(),
                                        anyLong(),
                                        anyLong(),
                                        anyString()));

        // Invoke logging call
        mLogger.logMeasurementDebugKeysMatch(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_DEBUG_KEYS),
                                eq(enrollmentId),
                                // topic ids converted into byte[]
                                eq(attributionType),
                                eq(true),
                                eq(hashedValue),
                                eq(hashLimit),
                                eq(SOURCE_REGISTRANT));
        ExtendedMockito.verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logMeasurementAttribution_success() {
        when(mFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        when(mFlags.getMeasurementAppPackageNameLoggingAllowlist()).thenReturn(SOURCE_REGISTRANT);
        MeasurementAttributionStats stats =
                new MeasurementAttributionStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_ATTRIBUTION)
                        .setSourceType(AttributionStatus.SourceType.VIEW.getValue())
                        .setSurfaceType(AttributionStatus.AttributionSurface.APP_WEB.getValue())
                        .setResult(AttributionStatus.AttributionResult.SUCCESS.getValue())
                        .setFailureType(AttributionStatus.FailureType.UNKNOWN.getValue())
                        .setSourceDerived(false)
                        .setInstallAttribution(true)
                        .setAttributionDelay(100L)
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .setAggregateReportCount(1)
                        .setAggregateDebugReportCount(1)
                        .setEventReportCount(3)
                        .setEventDebugReportCount(1)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyBoolean(),
                                        anyBoolean(),
                                        anyLong(),
                                        anyString(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));

        // Invoke logging call
        mLogger.logMeasurementAttributionStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_ATTRIBUTION),
                                eq(AttributionStatus.SourceType.VIEW.getValue()),
                                eq(AttributionStatus.AttributionSurface.APP_WEB.getValue()),
                                eq(AttributionStatus.AttributionResult.SUCCESS.getValue()),
                                eq(AttributionStatus.FailureType.UNKNOWN.getValue()),
                                eq(false),
                                eq(true),
                                eq(100L),
                                eq(SOURCE_REGISTRANT),
                                eq(1),
                                eq(1),
                                eq(3),
                                eq(1),
                                eq(0));

        ExtendedMockito.verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logMeasurementWipeout_success() {
        when(mFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        when(mFlags.getMeasurementAppPackageNameLoggingAllowlist()).thenReturn(SOURCE_REGISTRANT);
        MeasurementWipeoutStats stats =
                new MeasurementWipeoutStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_WIPEOUT)
                        .setWipeoutType(WipeoutStatus.WipeoutType.CONSENT_FLIP.ordinal())
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyString()));

        // Invoke logging call
        mLogger.logMeasurementWipeoutStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_WIPEOUT),
                                eq(WipeoutStatus.WipeoutType.CONSENT_FLIP.ordinal()),
                                eq(SOURCE_REGISTRANT));

        ExtendedMockito.verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logMeasurementDelayedSourceRegistration_success() {
        when(mFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        when(mFlags.getMeasurementAppPackageNameLoggingAllowlist()).thenReturn(SOURCE_REGISTRANT);
        int UnknownEnumValue = 0;
        long registrationDelay = 500L;
        MeasurementDelayedSourceRegistrationStats stats =
                new MeasurementDelayedSourceRegistrationStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION)
                        .setRegistrationStatus(UnknownEnumValue)
                        .setRegistrationDelay(registrationDelay)
                        .setRegistrant(SOURCE_REGISTRANT)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyLong(), anyString()));

        // Invoke logging call
        mLogger.logMeasurementDelayedSourceRegistrationStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION),
                                eq(UnknownEnumValue),
                                eq(registrationDelay),
                                eq(SOURCE_REGISTRANT));

        ExtendedMockito.verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logConsentMigrationStats_success() {
        when(mFlags.getAdservicesConsentMigrationLoggingEnabled()).thenReturn(true);
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyBoolean(),
                                        anyBoolean(),
                                        anyBoolean(),
                                        anyBoolean(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(true)
                        .setFledgeConsent(true)
                        .setMsmtConsent(true)
                        .setDefaultConsent(true)
                        .setMigrationStatus(
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_UPDATED)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.APPSEARCH_TO_SYSTEM_SERVICE)
                        .setRegion(2)
                        .build();

        // Invoke logging call
        mLogger.logConsentMigrationStats(consentMigrationStats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_CONSENT_MIGRATED),
                                eq(true),
                                eq(true),
                                eq(true),
                                eq(true),
                                eq(2),
                                eq(2),
                                eq(2));
        ExtendedMockito.verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logConsentMigrationStats_disabled() {
        when(mFlags.getAdservicesConsentMigrationLoggingEnabled()).thenReturn(false);

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(true)
                        .setFledgeConsent(true)
                        .setMsmtConsent(true)
                        .setDefaultConsent(true)
                        .setMigrationStatus(
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_UPDATED)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.APPSEARCH_TO_SYSTEM_SERVICE)
                        .setRegion(2)
                        .build();

        // Invoke logging call
        mLogger.logConsentMigrationStats(consentMigrationStats);

        verifyZeroInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logMeasurementAdIdMatchForDebugKeys_success() {
        when(mFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        when(mFlags.getMeasurementAppPackageNameLoggingAllowlist()).thenReturn(SOURCE_REGISTRANT);
        final String enrollmentId = "EnrollmentId";
        long uniqueAdIdValue = 1L;
        long uniqueAdIdLimit = 5L;
        int attributionType = AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB;
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(enrollmentId)
                        .setMatched(true)
                        .setAttributionType(attributionType)
                        .setNumUniqueAdIds(uniqueAdIdValue)
                        .setNumUniqueAdIdsLimit(uniqueAdIdLimit)
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyBoolean(),
                                        anyLong(),
                                        anyLong(),
                                        anyString()));

        // Invoke logging call
        mLogger.logMeasurementAdIdMatchForDebugKeysStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_AD_ID_MATCH_FOR_DEBUG_KEYS),
                                eq(enrollmentId),
                                eq(attributionType),
                                eq(true),
                                eq(uniqueAdIdValue),
                                eq(uniqueAdIdLimit),
                                eq(SOURCE_REGISTRANT));

        ExtendedMockito.verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logMeasurementAdIdMatchForDebugKeys_appLoggingDisabled_emptyString() {
        when(mFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(false);
        final String enrollmentId = "EnrollmentId";
        long uniqueAdIdValue = 1L;
        long uniqueAdIdLimit = 5L;
        int attributionType = AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB;
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(enrollmentId)
                        .setMatched(true)
                        .setAttributionType(attributionType)
                        .setNumUniqueAdIds(uniqueAdIdValue)
                        .setNumUniqueAdIdsLimit(uniqueAdIdLimit)
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyBoolean(),
                                        anyLong(),
                                        anyLong(),
                                        anyString()));

        // Invoke logging call
        mLogger.logMeasurementAdIdMatchForDebugKeysStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_AD_ID_MATCH_FOR_DEBUG_KEYS),
                                eq(enrollmentId),
                                eq(attributionType),
                                eq(true),
                                eq(uniqueAdIdValue),
                                eq(uniqueAdIdLimit),
                                eq(""));

        ExtendedMockito.verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logMeasurementAdIdMatchForDebugKeys_appNotAllowlisted_emptyString() {
        when(mFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        when(mFlags.getMeasurementAppPackageNameLoggingAllowlist()).thenReturn("");
        final String enrollmentId = "EnrollmentId";
        long uniqueAdIdValue = 1L;
        long uniqueAdIdLimit = 5L;
        int attributionType = AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB;
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(enrollmentId)
                        .setMatched(true)
                        .setAttributionType(attributionType)
                        .setNumUniqueAdIds(uniqueAdIdValue)
                        .setNumUniqueAdIdsLimit(uniqueAdIdLimit)
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyBoolean(),
                                        anyLong(),
                                        anyLong(),
                                        anyString()));

        // Invoke logging call
        mLogger.logMeasurementAdIdMatchForDebugKeysStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_AD_ID_MATCH_FOR_DEBUG_KEYS),
                                eq(enrollmentId),
                                eq(attributionType),
                                eq(true),
                                eq(uniqueAdIdValue),
                                eq(uniqueAdIdLimit),
                                eq(""));

        ExtendedMockito.verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logEnrollmentData_success() {
        int transactionTypeEnumValue =
                EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.ordinal();
        ExtendedMockito.doNothing()
                .when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyBoolean(), anyInt()));

        // Invoke logging call
        mLogger.logEnrollmentDataStats(transactionTypeEnumValue, true, 100);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_ENROLLMENT_DATA_STORED),
                                eq(transactionTypeEnumValue),
                                eq(true),
                                eq(100));

        ExtendedMockito.verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logEnrollmentMatch_success() {
        ExtendedMockito.doNothing()
                .when(() -> AdServicesStatsLog.write(anyInt(), anyBoolean(), anyInt()));

        // Invoke logging call
        mLogger.logEnrollmentMatchStats(true, 100);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_ENROLLMENT_MATCHED), eq(true), eq(100));

        ExtendedMockito.verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logEnrollmentFileDownload_success() {
        ExtendedMockito.doNothing()
                .when(() -> AdServicesStatsLog.write(anyInt(), anyBoolean(), anyInt()));

        // Invoke logging call
        mLogger.logEnrollmentFileDownloadStats(true, 100);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_ENROLLMENT_FILE_DOWNLOADED), eq(true), eq(100));

        ExtendedMockito.verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logEnrollmentFailed_success() {
        int dataFileGroupStatusEnumValue =
                EnrollmentStatus.DataFileGroupStatus.PENDING_CUSTOM_VALIDATION.ordinal();
        int errorCauseEnumValue =
                EnrollmentStatus.ErrorCause.ENROLLMENT_BLOCKLISTED_ERROR_CAUSE.ordinal();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyString(),
                                        anyInt()));

        // Invoke logging call
        mLogger.logEnrollmentFailedStats(
                100, dataFileGroupStatusEnumValue, 10, "SomeSdkName", errorCauseEnumValue);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_ENROLLMENT_FAILED),
                                eq(100),
                                eq(dataFileGroupStatusEnumValue),
                                eq(10),
                                eq("SomeSdkName"),
                                eq(errorCauseEnumValue));

        ExtendedMockito.verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logMeasurementClickVerificationStats_success() {
        int sourceType = Source.SourceType.NAVIGATION.getIntValue();
        boolean inputEventPresent = true;
        boolean systemClickVerificationSuccessful = true;
        boolean systemClickVerificationEnabled = true;
        long inputEventDelayMs = 200L;
        long validDelayWindowMs = 1000L;
        String sourceRegistrant = "test_source_registrant";

        MeasurementClickVerificationStats stats =
                MeasurementClickVerificationStats.builder()
                        .setSourceType(sourceType)
                        .setInputEventPresent(inputEventPresent)
                        .setSystemClickVerificationSuccessful(systemClickVerificationSuccessful)
                        .setSystemClickVerificationEnabled(systemClickVerificationEnabled)
                        .setInputEventDelayMillis(inputEventDelayMs)
                        .setValidDelayWindowMillis(validDelayWindowMs)
                        .setSourceRegistrant(sourceRegistrant)
                        .build();

        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyInt(),
                                        anyBoolean(),
                                        anyBoolean(),
                                        anyBoolean(),
                                        anyLong(),
                                        anyLong(),
                                        anyString()));

        // Invoke logging call.
        mLogger.logMeasurementClickVerificationStats(stats);

        // Verify only compat logging took place.
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_CLICK_VERIFICATION),
                                eq(sourceType),
                                eq(inputEventPresent),
                                eq(systemClickVerificationSuccessful),
                                eq(systemClickVerificationEnabled),
                                eq(inputEventDelayMs),
                                eq(validDelayWindowMs),
                                eq("")); // App package name not in allow list.

        ExtendedMockito.verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logEncryptionKeyFetchedStats_success() {
        final String enrollmentId = "enrollmentId";
        final String companyId = "companyId";
        final String encryptionKeyUrl = "https://www.adtech1.com/.well-known/encryption-keys";

        AdServicesEncryptionKeyFetchedStats stats =
                AdServicesEncryptionKeyFetchedStats.builder()
                        .setFetchJobType(ENCRYPTION_KEY_DAILY_FETCH_JOB)
                        .setFetchStatus(IO_EXCEPTION)
                        .setIsFirstTimeFetch(false)
                        .setAdtechEnrollmentId(enrollmentId)
                        .setCompanyId(companyId)
                        .setEncryptionKeyUrl(encryptionKeyUrl)
                        .build();

        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyBoolean(),
                                        anyString(),
                                        anyString(),
                                        anyString()));

        // Invoke logging call.
        mLogger.logEncryptionKeyFetchedStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_ENCRYPTION_KEY_FETCHED),
                                eq(ENCRYPTION_KEY_DAILY_FETCH_JOB.getValue()),
                                eq(IO_EXCEPTION.getValue()),
                                eq(false),
                                eq(enrollmentId),
                                eq(companyId),
                                eq(encryptionKeyUrl));

        ExtendedMockito.verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logEncryptionKeyDbTransactionEndedStats_success() {
        AdServicesEncryptionKeyDbTransactionEndedStats stats =
                AdServicesEncryptionKeyDbTransactionEndedStats.builder()
                        .setDbTransactionType(WRITE_TRANSACTION_TYPE)
                        .setDbTransactionStatus(INSERT_EXCEPTION)
                        .setMethodName(INSERT_KEY)
                        .build();

        ExtendedMockito.doNothing()
                .when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyInt(), anyInt()));

        // Invoke logging call.
        mLogger.logEncryptionKeyDbTransactionEndedStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_ENCRYPTION_KEY_DB_TRANSACTION_ENDED),
                                eq(WRITE_TRANSACTION_TYPE.getValue()),
                                eq(INSERT_EXCEPTION.getValue()),
                                eq(INSERT_KEY.getValue()));

        ExtendedMockito.verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }
}
