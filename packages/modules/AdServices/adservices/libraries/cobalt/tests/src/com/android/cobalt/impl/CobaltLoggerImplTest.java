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

package com.android.cobalt.impl;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.cobalt.CobaltLogger;
import com.android.cobalt.data.CobaltDatabase;
import com.android.cobalt.data.DataService;
import com.android.cobalt.data.EventVector;
import com.android.cobalt.data.ReportKey;
import com.android.cobalt.data.TestOnlyDao;
import com.android.cobalt.data.TestOnlyDao.AggregateStoreTableRow;
import com.android.cobalt.domain.Project;
import com.android.cobalt.system.SystemData;
import com.android.cobalt.system.testing.FakeSystemClock;

import com.google.cobalt.AggregateValue;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.Metadata;
import com.google.cobalt.MetricDefinition.MetricType;
import com.google.cobalt.ReleaseStage;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.ReportType;
import com.google.cobalt.SystemProfile;
import com.google.cobalt.SystemProfileField;
import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class CobaltLoggerImplTest {

    private static final ExecutorService sExecutor = Executors.newCachedThreadPool();
    private static final Context sContext = ApplicationProvider.getApplicationContext();

    private static final long CURRENT_TIME =
            Instant.parse("2022-07-29T04:15:30.00Z").toEpochMilli();
    private static final Instant WORKER_TIME =
            Instant.ofEpochMilli(CURRENT_TIME).plus(Duration.ofMinutes(1));
    private static final int DAY_INDEX = 19202;
    private static final ReportKey ONE_REPORT = ReportKey.create(1, 1, 1, 1);
    private static final ReportKey MULTIPLE_REPORTS_1 =
            ReportKey.create(ONE_REPORT.customerId(), ONE_REPORT.projectId(), 2, 2);
    private static final ReportKey MULTIPLE_REPORTS_2 =
            ReportKey.create(
                    ONE_REPORT.customerId(),
                    ONE_REPORT.projectId(),
                    MULTIPLE_REPORTS_1.metricId(),
                    3);
    private static final int WRONG_TYPE_METRIC_ID = 3;
    private static final String APP_VERSION = "0.1.2";
    private static final SystemProfile SYSTEM_PROFILE =
            SystemProfile.newBuilder().setAppVersion(APP_VERSION).build();
    private static final MetricDefinition ONE_REPORT_METRIC =
            MetricDefinition.newBuilder()
                    .setId((int) ONE_REPORT.metricId())
                    .setMetricType(MetricType.OCCURRENCE)
                    .addReports(
                            ReportDefinition.newBuilder()
                                    .setId((int) ONE_REPORT.reportId())
                                    .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                    .addSystemProfileField(SystemProfileField.APP_VERSION))
                    .setMetaData(Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.DEBUG))
                    .build();
    private static final MetricDefinition MULTIPLE_REPORT_METRIC =
            MetricDefinition.newBuilder()
                    .setId((int) MULTIPLE_REPORTS_1.metricId())
                    .setMetricType(MetricType.OCCURRENCE)
                    .addReports(
                            ReportDefinition.newBuilder()
                                    .setId((int) MULTIPLE_REPORTS_1.reportId())
                                    .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                    .addSystemProfileField(SystemProfileField.APP_VERSION))
                    .addReports(
                            ReportDefinition.newBuilder()
                                    .setId((int) MULTIPLE_REPORTS_2.reportId())
                                    .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                    .addSystemProfileField(SystemProfileField.APP_VERSION))
                    .setMetaData(Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.DEBUG))
                    .build();
    private static final MetricDefinition WRONG_TYPE_METRIC =
            MetricDefinition.newBuilder()
                    .setId(WRONG_TYPE_METRIC_ID)
                    .setMetricType(MetricType.INTEGER)
                    .setMetaData(Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.DEBUG))
                    .build();
    private static final Project COBALT_REGISTRY =
            Project.create(
                    (int) ONE_REPORT.customerId(),
                    (int) ONE_REPORT.projectId(),
                    List.of(ONE_REPORT_METRIC, MULTIPLE_REPORT_METRIC, WRONG_TYPE_METRIC));

    private CobaltDatabase mCobaltDatabase;
    private TestOnlyDao mTestOnlyDao;
    private DataService mDataService;
    private SystemData mSystemData;
    private FakeSystemClock mClock;
    private CobaltLogger mLogger;

    @Before
    public void createDb() {
        mCobaltDatabase = Room.inMemoryDatabaseBuilder(sContext, CobaltDatabase.class).build();
        mTestOnlyDao = mCobaltDatabase.testOnlyDao();
        mDataService = new DataService(sExecutor, mCobaltDatabase);
        mSystemData = new SystemData(APP_VERSION);
        mClock = new FakeSystemClock();
        mClock.set(WORKER_TIME);
        mLogger =
                new CobaltLoggerImpl(
                        COBALT_REGISTRY,
                        ReleaseStage.DEBUG,
                        mDataService,
                        mSystemData,
                        sExecutor,
                        mClock,
                        /* enabled= */ true);
    }

    @After
    public void closeDb() throws IOException {
        mCobaltDatabase.close();
    }

    @Test
    public void logOccurrence_oneLog_storedInDb() throws Exception {
        // Log some data.
        mLogger.logOccurrence(
                        ONE_REPORT.metricId(),
                        /* count= */ 100,
                        /* eventCodes= */ ImmutableList.of(1, 2))
                .get();

        // Check that only the one report has data in the DB.
        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(ONE_REPORT)
                                .setDayIndex(DAY_INDEX)
                                .setEventVector(EventVector.create(1, 2))
                                .setSystemProfile(SYSTEM_PROFILE)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(100).build())
                                .build());
    }

    @Test
    public void logOccurrence_multipleLogCalls_aggregatedInDb() throws Exception {
        // Log three independent calls for 2 different event vectors.
        mLogger.logOccurrence(
                        ONE_REPORT.metricId(),
                        /* count= */ 1,
                        /* eventCodes= */ ImmutableList.of(1, 2))
                .get();
        mLogger.logOccurrence(
                        ONE_REPORT.metricId(),
                        /* count= */ 2,
                        /* eventCodes= */ ImmutableList.of(1, 3))
                .get();
        mLogger.logOccurrence(
                        ONE_REPORT.metricId(),
                        /* count= */ 4,
                        /* eventCodes= */ ImmutableList.of(1, 2))
                .get();

        // Check that the EventVectors were aggregated separately.
        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(ONE_REPORT)
                                .setDayIndex(DAY_INDEX)
                                .setEventVector(EventVector.create(1, 2))
                                .setSystemProfile(SYSTEM_PROFILE)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(5).build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(ONE_REPORT)
                                .setDayIndex(DAY_INDEX)
                                .setEventVector(EventVector.create(1, 3))
                                .setSystemProfile(SYSTEM_PROFILE)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(2).build())
                                .build());
    }

    @Test
    public void logOccurrence_multipleReports_storedInDb() throws Exception {
        // Log some data for a metric that has multiple reports.
        mLogger.logOccurrence(
                        MULTIPLE_REPORTS_1.metricId(),
                        /* count= */ 123,
                        /* eventCodes= */ ImmutableList.of(1, 2))
                .get();

        // Check that all reports for the metric have data in the DB.
        // Check that they all have the same EventVector marked as occurred.
        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(MULTIPLE_REPORTS_1)
                                .setDayIndex(DAY_INDEX)
                                .setEventVector(EventVector.create(1, 2))
                                .setSystemProfile(
                                        SystemProfile.newBuilder()
                                                .setAppVersion(APP_VERSION)
                                                .build())
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(123).build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(MULTIPLE_REPORTS_2)
                                .setDayIndex(DAY_INDEX)
                                .setEventVector(EventVector.create(1, 2))
                                .setSystemProfile(SYSTEM_PROFILE)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(123).build())
                                .build());
    }

    @Test
    public void logOccurrence_emptyEventVector_storedInDb() throws Exception {
        // Log some data.
        mLogger.logOccurrence(
                        ONE_REPORT.metricId(),
                        /* count= */ 100,
                        /* eventCodes= */ ImmutableList.of())
                .get();

        // Check that only the one report has data in the DB.
        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(ONE_REPORT)
                                .setDayIndex(DAY_INDEX)
                                .setEventVector(EventVector.create())
                                .setSystemProfile(SYSTEM_PROFILE)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(100).build())
                                .build());
    }

    @Test
    public void logOccurrence_reportAllMultipleSystemProfiles_storedInDb() throws Exception {
        // Create an aggregate for a different system profile.
        mTestOnlyDao.insertAggregateValue(
                AggregateStoreTableRow.builder()
                        .setReportKey(ONE_REPORT)
                        .setDayIndex(DAY_INDEX)
                        .setEventVector(EventVector.create(1, 2))
                        .setSystemProfile(
                                SYSTEM_PROFILE.toBuilder().setAppVersion("old_version").build())
                        .setAggregateValue(AggregateValue.newBuilder().setIntegerValue(100).build())
                        .build());

        // Log some data with the current system profile.
        mLogger.logOccurrence(
                        ONE_REPORT.metricId(),
                        /* count= */ 200,
                        /* eventCodes= */ ImmutableList.of(1, 2))
                .get();

        // Check that both system profiles have aggregates in the DB.
        assertThat(mTestOnlyDao.getAllAggregates())
                .containsExactly(
                        AggregateStoreTableRow.builder()
                                .setReportKey(ONE_REPORT)
                                .setDayIndex(DAY_INDEX)
                                .setEventVector(EventVector.create(1, 2))
                                .setSystemProfile(
                                        SystemProfile.newBuilder()
                                                .setAppVersion("old_version")
                                                .build())
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(100).build())
                                .build(),
                        AggregateStoreTableRow.builder()
                                .setReportKey(ONE_REPORT)
                                .setDayIndex(DAY_INDEX)
                                .setEventVector(EventVector.create(1, 2))
                                .setSystemProfile(SYSTEM_PROFILE)
                                .setAggregateValue(
                                        AggregateValue.newBuilder().setIntegerValue(200).build())
                                .build());
    }

    @Test
    public void logOccurrence_unsupportedMetricType_notStoredWithError() throws Exception {
        // Log data for an INTEGER metric, and check it completed with the expected error.
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mLogger.logOccurrence(
                                                WRONG_TYPE_METRIC_ID,
                                                /* count= */ 1,
                                                /* eventCodes= */ ImmutableList.of(1, 2))
                                        .get());
        assertThat(exception).hasCauseThat().hasMessageThat().contains("wrong metric type");

        // Check that no report data was added to the DB.
        assertThat(mTestOnlyDao.getAllAggregates()).isEmpty();
    }

    @Test
    public void logOccurrence_missingMetric_notStoredWithError() throws Exception {
        // Log data for a metric that doesn't exist, and check it completed with the expected error.
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mLogger.logOccurrence(
                                                /* metricId= */ 333,
                                                /* count= */ 1,
                                                /* eventCodes= */ ImmutableList.of(1, 2))
                                        .get());
        assertThat(exception)
                .hasCauseThat()
                .hasMessageThat()
                .contains("failed to find metric with ID: 333");

        // Check that no report data was added to the DB.
        assertThat(mTestOnlyDao.getAllAggregates()).isEmpty();
    }

    @Test
    public void logOccurrence_negativeCount_notStoredWithError() throws Exception {
        // Log a negative count, and check it completed with the expected error.
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mLogger.logOccurrence(
                                                ONE_REPORT.metricId(),
                                                /* count= */ -1,
                                                /* eventCodes= */ ImmutableList.of(1, 2))
                                        .get());
        assertThat(exception)
                .hasCauseThat()
                .hasMessageThat()
                .contains("occurrence count can't be negative");

        // Check that no report data was added to the DB.
        assertThat(mTestOnlyDao.getAllAggregates()).isEmpty();
    }

    @Test
    public void logOccurrence_negativeEventCode_notStoredWithError() throws Exception {
        // Log a negative event code, and check it completed with the expected error.
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mLogger.logOccurrence(
                                                ONE_REPORT.metricId(),
                                                /* count= */ 1,
                                                /* eventCodes= */ ImmutableList.of(1, -2))
                                        .get());
        assertThat(exception)
                .hasCauseThat()
                .hasMessageThat()
                .contains("event vectors can't contain negative event codes");

        // Check that no report data was added to the DB.
        assertThat(mTestOnlyDao.getAllAggregates()).isEmpty();
    }

    @Test
    public void logOccurrence_loggerDisabled_loggedDataNotStoredInDb() throws Exception {
        CobaltLogger logger =
                new CobaltLoggerImpl(
                        COBALT_REGISTRY,
                        ReleaseStage.DEBUG,
                        mDataService,
                        mSystemData,
                        sExecutor,
                        mClock,
                        /* enabled= */ false);

        // Log some data.
        logger.logOccurrence(
                        ONE_REPORT.metricId(),
                        /* count= */ 1,
                        /* eventCodes= */ ImmutableList.of(1, 2))
                .get();

        // Check that no reports have data in the DB.
        assertThat(mTestOnlyDao.getAllAggregates()).isEmpty();
        assertThat(mTestOnlyDao.getStartDisabledTime().get()).isEqualTo(WORKER_TIME);
    }

    @Test
    public void logOccurrence_oneLogForMetricInLaterReleaseStage_dropped() throws Exception {
        // Create a metric for FISHFOOD, and current release is DOGFOOD.
        MetricDefinition metric =
                MetricDefinition.newBuilder()
                        .setId((int) ONE_REPORT.metricId())
                        .setMetricType(MetricType.OCCURRENCE)
                        .addReports(
                                ReportDefinition.newBuilder()
                                        .setId((int) ONE_REPORT.reportId())
                                        .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                        .addSystemProfileField(SystemProfileField.APP_VERSION))
                        .setMetaData(
                                Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.FISHFOOD))
                        .build();
        Project project =
                Project.create(
                        (int) ONE_REPORT.customerId(),
                        (int) ONE_REPORT.projectId(),
                        List.of(metric));
        CobaltLogger logger =
                new CobaltLoggerImpl(
                        project,
                        ReleaseStage.DOGFOOD,
                        mDataService,
                        mSystemData,
                        sExecutor,
                        mClock,
                        /* enabled= */ true);

        // Log some data.
        logger.logOccurrence(
                        ONE_REPORT.metricId(),
                        /* count= */ 1,
                        /* eventCodes= */ ImmutableList.of(1, 2))
                .get();

        // Check that no report data was added to the DB.
        assertThat(mTestOnlyDao.getAllAggregates()).isEmpty();
    }

    @Test
    public void logOccurrence_oneLogForReportInLaterReleaseStage_dropped() throws Exception {
        // Create a report for FISHFOOD, and current release is DOGFOOD.
        MetricDefinition metric =
                MetricDefinition.newBuilder()
                        .setId((int) ONE_REPORT.metricId())
                        .setMetricType(MetricType.OCCURRENCE)
                        .addReports(
                                ReportDefinition.newBuilder()
                                        .setId((int) ONE_REPORT.reportId())
                                        .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                        .addSystemProfileField(SystemProfileField.APP_VERSION)
                                        .setMaxReleaseStage(ReleaseStage.FISHFOOD))
                        .setMetaData(Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.DOGFOOD))
                        .build();
        Project project =
                Project.create(
                        (int) ONE_REPORT.customerId(),
                        (int) ONE_REPORT.projectId(),
                        List.of(metric));
        CobaltLogger logger =
                new CobaltLoggerImpl(
                        project,
                        ReleaseStage.DOGFOOD,
                        mDataService,
                        mSystemData,
                        sExecutor,
                        mClock,
                        /* enabled= */ true);

        // Log some data.
        logger.logOccurrence(
                        ONE_REPORT.metricId(),
                        /* count= */ 1,
                        /* eventCodes= */ ImmutableList.of(1, 2))
                .get();

        // Check that no report data was added to the DB.
        assertThat(mTestOnlyDao.getAllAggregates()).isEmpty();
    }

    @Test
    public void noOpLogString_doesNothing() throws Exception {
        // Log some data with the expectation nothing will be logged.
        mLogger.noOpLogString(
                        ONE_REPORT.metricId(),
                        /* value= */ "Test",
                        /* eventCodes= */ ImmutableList.of(1, 2))
                .get();

        // Check that no data was added to the DB.
        assertThat(mTestOnlyDao.getAllAggregates()).isEmpty();
        assertThat(mTestOnlyDao.getInitialEnabledTime().isPresent()).isFalse();
        assertThat(mTestOnlyDao.getStartDisabledTime().isPresent()).isFalse();
    }
}
