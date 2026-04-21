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

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetFlags;
import static com.android.adservices.mockito.MockitoExpectations.mockBackgroundJobsLoggingKillSwitch;
import static com.android.adservices.mockito.MockitoExpectations.syncLogExecutionStats;
import static com.android.adservices.mockito.MockitoExpectations.syncPersistJobExecutionData;
import static com.android.adservices.mockito.MockitoExpectations.verifyBackgroundJobsSkipLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyJobFinishedLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyLoggingNotHappened;
import static com.android.adservices.spe.AdservicesJobInfo.MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.synccallback.JobServiceLoggingCallback;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.Clock;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.adservices.spe.AdservicesJobServiceLogger;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.CallsRealMethods;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class VerboseDebugReportingFallbackJobServiceTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final int MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID =
            MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB.getJobId();
    private static final long WAIT_IN_MILLIS = 1_000L;
    private static final long JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(4);
    private JobScheduler mMockJobScheduler;
    private VerboseDebugReportingFallbackJobService mSpyService;
    private DatastoreManager mMockDatastoreManager;
    private AdservicesJobServiceLogger mSpyLogger;
    private Flags mMockFlags;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(VerboseDebugReportingFallbackJobService.class)
                    .spyStatic(AdServicesLoggerImpl.class)
                    .spyStatic(DatastoreManagerFactory.class)
                    .spyStatic(EnrollmentDao.class)
                    .spyStatic(FlagsFactory.class)
                    .spyStatic(AdservicesJobServiceLogger.class)
                    .mockStatic(ServiceCompatUtils.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Before
    public void setUp() {
        mSpyService = spy(new VerboseDebugReportingFallbackJobService());
        mMockJobScheduler = mock(JobScheduler.class);

        StatsdAdServicesLogger mockStatsdLogger = mock(StatsdAdServicesLogger.class);
        mSpyLogger =
                spy(new AdservicesJobServiceLogger(CONTEXT, Clock.SYSTEM_CLOCK, mockStatsdLogger));
        mMockFlags = mock(Flags.class);
        when(mMockFlags.getMeasurementVerboseDebugReportingFallbackJobPersisted()).thenReturn(true);
        when(mMockFlags.getMeasurementVerboseDebugReportingJobRequiredNetworkType())
                .thenReturn(JobInfo.NETWORK_TYPE_ANY);
        when(mMockFlags.getMeasurementVerboseDebugReportingFallbackJobPeriodMs())
                .thenReturn(JOB_PERIOD_MS);
    }

    @Test
    public void onStartJob_killSwitchOn_withoutLogging() throws Exception {
        runWithMocks(
                () -> {
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

                    onStartJob_killSwitchOn();

                    verifyLoggingNotHappened(mSpyLogger);
                });
    }

    @Test
    public void onStartJob_killSwitchOn_withLogging() throws Exception {
        runWithMocks(
                () -> {
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
                    JobServiceLoggingCallback callback = syncLogExecutionStats(mSpyLogger);

                    onStartJob_killSwitchOn();

                    verifyBackgroundJobsSkipLogged(mSpyLogger, callback);
                });
    }

    @Test
    public void onStartJob_killSwitchOff_withoutLogging() throws Exception {
        runWithMocks(
                () -> {
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

                    onStartJob_killSwitchOff();

                    verifyLoggingNotHappened(mSpyLogger);
                });
    }

    @Test
    public void onStartJob_killSwitchOff_withLogging() throws Exception {
        runWithMocks(
                () -> {
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
                    JobServiceLoggingCallback onStartJobCallback =
                            syncPersistJobExecutionData(mSpyLogger);
                    JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mSpyLogger);

                    onStartJob_killSwitchOff();

                    verifyJobFinishedLogged(mSpyLogger, onStartJobCallback, onJobDoneCallback);
                });
    }

    @Test
    public void onStartJob_killSwitchOff_unlockingCheck() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    CountDownLatch countDownLatch = createCountDownLatch();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            VerboseDebugReportingFallbackJobService
                                                    .scheduleIfNeeded(any(), anyBoolean()));

                    // Execute
                    mSpyService.onStartJob(Mockito.mock(JobParameters.class));
                    countDownLatch.await();
                    // TODO (b/298021244): replace sleep() with a better approach
                    Thread.sleep(WAIT_IN_MILLIS);
                    countDownLatch = createCountDownLatch();
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));
                    countDownLatch.await();
                    // TODO (b/298021244): replace sleep() with a better approach
                    Thread.sleep(WAIT_IN_MILLIS);

                    // Validate
                    assertTrue(result);

                    // Verify the job ran successfully twice
                    ExtendedMockito.verify(mSpyService, times(2)).jobFinished(any(), anyBoolean());
                    verify(mMockJobScheduler, never())
                            .cancel(eq(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID));
                });
    }

    @Test
    public void onStartJob_shouldDisableJobTrue_withoutLogging() throws Exception {
        runWithMocks(
                () -> {
                    mockGetFlags(mMockFlags);
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

                    onStartJob_shouldDisableJobTrue();

                    verifyLoggingNotHappened(mSpyLogger);
                });
    }

    @Test
    public void onStartJob_shouldDisableJobTrue_withLoggingEnabled() throws Exception {
        runWithMocks(
                () -> {
                    mockGetFlags(mMockFlags);
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);

                    onStartJob_shouldDisableJobTrue();

                    // Verify logging has not happened even though logging is enabled because this
                    // field is not logged
                    verifyLoggingNotHappened(mSpyLogger);
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOn_dontSchedule() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    enableKillSwitch();

                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo = mock(JobInfo.class);
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID));

                    // Execute
                    VerboseDebugReportingFallbackJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> VerboseDebugReportingFallbackJobService.schedule(any(), any()),
                            never());
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_sameJobInfoDontForceSchedule_dontSchedule() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context mockContext = spy(ApplicationProvider.getApplicationContext());
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID,
                                            new ComponentName(
                                                    mockContext,
                                                    VerboseDebugReportingFallbackJobService.class))
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                    .setPeriodic(JOB_PERIOD_MS)
                                    .setPersisted(true)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID));

                    // Execute
                    VerboseDebugReportingFallbackJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> VerboseDebugReportingFallbackJobService.schedule(any(), any()),
                            never());
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_diffJobInfoDontForceSchedule_doesSchedule() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context mockContext = spy(ApplicationProvider.getApplicationContext());
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID,
                                            new ComponentName(
                                                    mockContext,
                                                    VerboseDebugReportingFallbackJobService.class))
                                    // Difference
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR)
                                    .setPeriodic(JOB_PERIOD_MS)
                                    .setPersisted(true)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID));

                    // Execute
                    VerboseDebugReportingFallbackJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> VerboseDebugReportingFallbackJobService.schedule(any(), any()));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyExecuted_forceSchedule_schedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo = mock(JobInfo.class);
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID));

                    // Execute
                    VerboseDebugReportingFallbackJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ true);

                    // Validate
                    ExtendedMockito.verify(
                            () -> VerboseDebugReportingFallbackJobService.schedule(any(), any()),
                            times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyNotExecuted_dontForceSchedule_schedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    // Mock the JobScheduler to have no pending job.
                    doReturn(null)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID));

                    // Execute
                    VerboseDebugReportingFallbackJobService.scheduleIfNeeded(mockContext, false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> VerboseDebugReportingFallbackJobService.schedule(any(), any()),
                            times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID));
                });
    }

    @Test
    public void testOnStopJob_stopsExecutingThread() throws Exception {
        runWithMocks(
                () -> {
                    disableKillSwitch();

                    doAnswer(new AnswersWithDelay(WAIT_IN_MILLIS * 10, new CallsRealMethods()))
                            .when(mSpyService)
                            .sendReports();
                    mSpyService.onStartJob(Mockito.mock(JobParameters.class));
                    Thread.sleep(WAIT_IN_MILLIS);

                    assertNotNull(mSpyService.getFutureForTesting());

                    boolean onStopJobResult =
                            mSpyService.onStopJob(Mockito.mock(JobParameters.class));
                    verify(mSpyService, times(0)).jobFinished(any(), anyBoolean());
                    assertTrue(onStopJobResult);
                    assertTrue(mSpyService.getFutureForTesting().isCancelled());
                });
    }

    private void onStartJob_shouldDisableJobTrue() throws Exception {
        // Setup
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertFalse(result);
        // Allow background thread to execute
        Thread.sleep(WAIT_IN_MILLIS);
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        verify(mMockJobScheduler, times(1))
                .cancel(eq(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID));
    }

    private void onStartJob_killSwitchOn() throws Exception {
        // Setup
        enableKillSwitch();

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertFalse(result);
        // Allow background thread to execute
        Thread.sleep(WAIT_IN_MILLIS);
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        verify(mMockJobScheduler, times(1))
                .cancel(eq(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID));
    }

    private void onStartJob_killSwitchOff() throws Exception {
        // Setup
        disableKillSwitch();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                VerboseDebugReportingFallbackJobService.scheduleIfNeeded(
                                        any(), anyBoolean()));

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertTrue(result);
        // Allow background thread to execute
        Thread.sleep(WAIT_IN_MILLIS);
        ExtendedMockito.verify(mSpyService, times(1)).jobFinished(any(), anyBoolean());
        verify(mMockJobScheduler, never())
                .cancel(eq(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID));
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
            // Setup mock everything in job
            mMockDatastoreManager = mock(DatastoreManager.class);
            doReturn(Optional.empty())
                    .when(mMockDatastoreManager)
                    .runInTransactionWithResult(any());
            doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
            doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
            doReturn(Mockito.mock(Context.class)).when(mSpyService).getApplicationContext();
            ExtendedMockito.doReturn(mock(EnrollmentDao.class))
                    .when(() -> EnrollmentDao.getInstance(any()));
            ExtendedMockito.doReturn(mock(AdServicesLoggerImpl.class))
                    .when(AdServicesLoggerImpl::getInstance);
            ExtendedMockito.doNothing()
                    .when(() -> VerboseDebugReportingFallbackJobService.schedule(any(), any()));
            ExtendedMockito.doReturn(mMockDatastoreManager)
                    .when(() -> DatastoreManagerFactory.getDatastoreManager(any()));
            ExtendedMockito.doReturn(false)
                    .when(
                            () ->
                                    ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                            any(Context.class)));

            // Mock AdservicesJobServiceLogger to not actually log the stats to server
            Mockito.doNothing()
                    .when(mSpyLogger)
                    .logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
            ExtendedMockito.doReturn(mSpyLogger)
                    .when(() -> AdservicesJobServiceLogger.getInstance(any(Context.class)));

            // Execute
            execute.run();
    }

    private void enableKillSwitch() {
        toggleKillSwitch(true);
    }

    private void disableKillSwitch() {
        toggleKillSwitch(false);
    }

    private void toggleKillSwitch(boolean value) {
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(value)
                .when(mMockFlags)
                .getMeasurementVerboseDebugReportingFallbackJobKillSwitch();
    }

    private CountDownLatch createCountDownLatch() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        doAnswer(i -> countDown(countDownLatch)).when(mSpyService).jobFinished(any(), anyBoolean());
        return countDownLatch;
    }

    private Object countDown(CountDownLatch countDownLatch) {
        countDownLatch.countDown();
        return null;
    }
}
