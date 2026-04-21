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

package com.android.adservices.service.measurement.reporting;

import static com.android.adservices.service.measurement.reporting.DebugReportingJobService.DEBUG_REPORT_JOB_ID;
import static com.android.adservices.spe.AdservicesJobInfo.MEASUREMENT_DEBUG_REPORT_JOB;

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

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.stats.Clock;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.adservices.spe.AdservicesJobServiceLogger;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.CallsRealMethods;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/** Unit test for {@link DebugReportingJobService} */
public class DebugReportingJobServiceTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private static final int MEASUREMENT_DEBUG_REPORT_JOB_ID =
            MEASUREMENT_DEBUG_REPORT_JOB.getJobId();

    private static final long WAIT_IN_MILLIS = 1000L;

    private DatastoreManager mMockDatastoreManager;
    private JobScheduler mMockJobScheduler;
    private JobParameters mJobParameters;
    private AdservicesJobServiceLogger mSpyLogger;
    private DebugReportingJobService mSpyService;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(AdServicesConfig.class)
                    .spyStatic(DatastoreManagerFactory.class)
                    .spyStatic(EnrollmentDao.class)
                    .spyStatic(DebugReportingJobService.class)
                    .spyStatic(FlagsFactory.class)
                    .spyStatic(AdservicesJobServiceLogger.class)
                    .mockStatic(ServiceCompatUtils.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Before
    public void setUp() {
        mSpyService = spy(new DebugReportingJobService());
        mMockDatastoreManager = mock(DatastoreManager.class);
        mMockJobScheduler = mock(JobScheduler.class);
        mJobParameters = mock(JobParameters.class);
        StatsdAdServicesLogger mockStatsdLogger = mock(StatsdAdServicesLogger.class);
        mSpyLogger =
                spy(new AdservicesJobServiceLogger(CONTEXT, Clock.SYSTEM_CLOCK, mockStatsdLogger));
    }

    @Test
    public void onStartJob_killSwitchOn() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    enableKillSwitch();

                    // Execute
                    boolean result = mSpyService.onStartJob(mJobParameters);

                    // Validate
                    assertFalse(result);
                    // Allow background thread to execute
                    // TODO (b/298021244): replace sleep() with a better approach
                    Thread.sleep(WAIT_IN_MILLIS);
                    if (!SdkLevel.isAtLeastT()) {
                        // Additional sleep for S- test flakiness
                        Thread.sleep(WAIT_IN_MILLIS);
                    }
                    verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), eq(false));
                    verify(mMockJobScheduler, times(1)).cancel(eq(MEASUREMENT_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void onStartJob_killSwitchOff() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));

                    // Execute
                    boolean result = mSpyService.onStartJob(mJobParameters);

                    // Validate
                    assertTrue(result);
                    // Allow background thread to execute
                    // TODO (b/298021244): replace sleep() with a better approach
                    Thread.sleep(WAIT_IN_MILLIS);
                    if (!SdkLevel.isAtLeastT()) {
                        // Additional sleep for S- test flakiness
                        Thread.sleep(WAIT_IN_MILLIS);
                    }

                    verify(mMockDatastoreManager, times(2)).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), anyBoolean());
                    verify(mMockJobScheduler, never()).cancel(eq(MEASUREMENT_DEBUG_REPORT_JOB_ID));
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
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));

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
                    verify(mMockDatastoreManager, times(4)).runInTransactionWithResult(any());
                    verify(mSpyService, times(2)).jobFinished(any(), anyBoolean());
                    verify(mMockJobScheduler, never()).cancel(eq(MEASUREMENT_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void onStartJob_shouldDisableJobTrue() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    ExtendedMockito.doReturn(true)
                            .when(
                                    () ->
                                            ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                                    any(Context.class)));

                    // Execute
                    boolean result = mSpyService.onStartJob(mJobParameters);

                    // Validate
                    assertFalse(result);
                    // Allow background thread to execute
                    Thread.sleep(WAIT_IN_MILLIS);
                    verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), eq(false));
                    verify(mMockJobScheduler, times(1)).cancel(eq(MEASUREMENT_DEBUG_REPORT_JOB_ID));
                    ExtendedMockito.verifyZeroInteractions(
                            ExtendedMockito.staticMockMarker(FlagsFactory.class));
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
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORT_JOB_ID));

                    // Execute
                    DebugReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule = */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> DebugReportingJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyExecuted_dontForceSchedule_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    final Context spyContext = spy(ApplicationProvider.getApplicationContext());
                    doReturn(mMockJobScheduler)
                            .when(spyContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            DEBUG_REPORT_JOB_ID,
                                            new ComponentName(
                                                    spyContext, DebugReportingJobService.class))
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORT_JOB_ID));

                    // Execute
                    DebugReportingJobService.scheduleIfNeeded(
                            spyContext, /* forceSchedule = */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> DebugReportingJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_withDifferentJobInfo_doesSchedule() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    final Context spyContext = spy(ApplicationProvider.getApplicationContext());
                    doReturn(mMockJobScheduler)
                            .when(spyContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            DEBUG_REPORT_JOB_ID,
                                            new ComponentName(
                                                    spyContext, DebugReportingJobService.class))
                                    // Difference here
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORT_JOB_ID));

                    // Execute
                    DebugReportingJobService.scheduleIfNeeded(
                            spyContext, /* forceSchedule = */ false);

                    // Validate
                    ExtendedMockito.verify(() -> DebugReportingJobService.schedule(any(), any()));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORT_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORT_JOB_ID));

                    // Execute
                    DebugReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule = */ true);

                    // Validate
                    ExtendedMockito.verify(
                            () -> DebugReportingJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORT_JOB_ID));
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
                    doReturn(/* noJobInfo = */ null)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORT_JOB_ID));

                    // Execute
                    DebugReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule = */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> DebugReportingJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORT_JOB_ID));
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

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
            Context context = Mockito.mock(Context.class);
            doReturn(CONTEXT.getPackageName()).when(context).getPackageName();
            doReturn(CONTEXT.getPackageManager()).when(context).getPackageManager();
            // Setup mock everything in job
            mMockDatastoreManager = mock(DatastoreManager.class);
            doReturn(Optional.empty())
                    .when(mMockDatastoreManager)
                    .runInTransactionWithResult(any());
            doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
            doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
            doReturn(context).when(mSpyService).getApplicationContext();
            ExtendedMockito.doReturn(mock(EnrollmentDao.class))
                    .when(() -> EnrollmentDao.getInstance(any()));
            ExtendedMockito.doReturn(mMockDatastoreManager)
                    .when(() -> DatastoreManagerFactory.getDatastoreManager(any()));
            ExtendedMockito.doNothing().when(() -> DebugReportingJobService.schedule(any(), any()));

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
        Flags mockFlags = Mockito.mock(Flags.class);
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(value).when(mockFlags).getMeasurementJobDebugReportingKillSwitch();
        ExtendedMockito.doReturn(JobInfo.NETWORK_TYPE_ANY)
                .when(mockFlags)
                .getMeasurementDebugReportingJobRequiredNetworkType();
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
