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

package com.android.adservices.download;

import static com.android.adservices.download.MddJobService.KEY_MDD_TASK_TAG;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockAdservicesJobServiceLogger;
import static com.android.adservices.mockito.MockitoExpectations.mockBackgroundJobsLoggingKillSwitch;
import static com.android.adservices.mockito.MockitoExpectations.syncLogExecutionStats;
import static com.android.adservices.mockito.MockitoExpectations.syncPersistJobExecutionData;
import static com.android.adservices.mockito.MockitoExpectations.verifyBackgroundJobsSkipLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyJobFinishedLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyLoggingNotHappened;
import static com.android.adservices.mockito.MockitoExpectations.verifyOnStopJobLogged;
import static com.android.adservices.spe.AdservicesJobInfo.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdservicesJobInfo.MDD_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdservicesJobInfo.MDD_MAINTENANCE_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdservicesJobInfo.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.android.libraries.mobiledatadownload.TaskScheduler.WIFI_CHARGING_PERIODIC_TASK;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.FlakyTest;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.JobServiceCallback;
import com.android.adservices.common.synccallback.JobServiceLoggingCallback;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.adservices.spe.AdservicesJobServiceLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.android.libraries.mobiledatadownload.MobileDataDownload;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Unit tests for {@link com.android.adservices.download.MddJobService} */
@SpyStatic(MddJobService.class)
@SpyStatic(MobileDataDownloadFactory.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(MddFlags.class)
@SpyStatic(AdservicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class MddJobServiceTest extends AdServicesExtendedMockitoTestCase {

    private static final int JOB_SCHEDULED_WAIT_TIME_MS = 1_000;
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final JobScheduler JOB_SCHEDULER = CONTEXT.getSystemService(JobScheduler.class);
    private static final int MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID =
            MDD_MAINTENANCE_PERIODIC_TASK_JOB.getJobId();
    private static final int MDD_CHARGING_PERIODIC_TASK_JOB_ID =
            MDD_CHARGING_PERIODIC_TASK_JOB.getJobId();
    private static final int MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID =
            MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB.getJobId();
    private static final int MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID =
            MDD_WIFI_CHARGING_PERIODIC_TASK_JOB.getJobId();
    private static final long TASK_PERIOD = 21_600L;
    public static final int INTERVAL_MS = 10_000;
    public static final int FLEX_MS = 1_000;

    @Spy private MddJobService mSpyMddJobService;

    @Mock private JobParameters mMockJobParameters;

    @Mock private MobileDataDownload mMockMdd;
    @Mock private Flags mMockFlags;
    @Mock private MddFlags mMockMddFlags;
    @Mock private StatsdAdServicesLogger mMockStatsdLogger;

    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private AdservicesJobServiceLogger mLogger;

    @Before
    public void setup() {
        // Mock JobScheduler invocation in EpochJobService
        assertThat(JOB_SCHEDULER).isNotNull();
        assertNull(
                "Job already scheduled before setup!",
                JOB_SCHEDULER.getPendingJob(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID));

        extendedMockito.mockGetFlags(mMockFlags);

        doReturn(JOB_SCHEDULER).when(mSpyMddJobService).getSystemService(JobScheduler.class);

        mLogger = mockAdservicesJobServiceLogger(CONTEXT, mMockStatsdLogger);

        // MDD Task Tag.
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(KEY_MDD_TASK_TAG, WIFI_CHARGING_PERIODIC_TASK);
        when(mMockJobParameters.getExtras()).thenReturn(bundle);
    }

    @After
    public void teardown() {
        JOB_SCHEDULER.cancelAll();
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testOnStartJob_killswitchIsOff_withoutLogging() throws Exception {
        // Logging killswitch is on.
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, true);

        testOnStartJob_killswitchIsOff();

        // Verify logging methods are not invoked.
        verifyLoggingNotHappened(mLogger);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testOnStartJob_killswitchIsOff_withLogging() throws Exception {
        // Logging killswitch is off.
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, false);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(mLogger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mLogger);

        testOnStartJob_killswitchIsOff();

        // Verify logging methods are invoked.
        verifyJobFinishedLogged(mLogger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testOnStartJob_killswitchIsOn_withoutLogging() throws Exception {
        // Logging killswitch is on.
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, true);

        testOnStartJob_killswitchIsOn();

        // Verify logging methods are not invoked.
        verifyLoggingNotHappened(mLogger);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testOnStartJob_killSwitchOn_withLogging() throws Exception {
        // Logging killswitch is off.
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, false);
        JobServiceLoggingCallback callback = syncLogExecutionStats(mLogger);

        testOnStartJob_killswitchIsOn();

        // Verify logging methods are invoked.
        verifyBackgroundJobsSkipLogged(mLogger, callback);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testSchedule_killswitchOff() throws Exception {
        // Mock static method MddFlags.getInstance() to return Mock MddFlags.
        mockGetMddFlags();
        // Killswitch is off.
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, false);

        JobServiceCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);
        assertJobScheduled(
                callBack,
                MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);

        waitForJobFinished(JOB_SCHEDULED_WAIT_TIME_MS);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testSchedule_killswitchOn() throws Exception {
        // Killswitch is off.
        mockMddBackgroundTaskKillSwitch(/* toBeReturned */ true);

        JobServiceCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);

        verifyZeroInteractions(staticMockMarker(MobileDataDownloadFactory.class));
        assertJobScheduled(
                callBack,
                MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ false,
                /* checkPendingJob */ false);

        waitForJobFinished(JOB_SCHEDULED_WAIT_TIME_MS);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testOnStopJob_withoutLogging() throws Exception {
        // Logging killswitch is on.
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, true);

        testOnStopJob();

        // Verify logging methods are not invoked.
        verifyLoggingNotHappened(mLogger);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testOnStopJob_withLogging() throws Exception {
        // Logging killswitch is off.
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, false);
        JobServiceLoggingCallback callback = syncLogExecutionStats(mLogger);

        testOnStopJob();

        // Verify logging methods are invoked.
        verifyOnStopJobLogged(mLogger, callback);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testScheduleIfNeeded_Success() throws Exception {
        // Mock static method MddFlags.getInstance() to return Mock MddFlags.
        mockGetMddFlags();

        JobServiceCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);
        assertJobScheduled(
                callBack,
                MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);
        assertJobScheduled(
                callBack,
                MDD_CHARGING_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);
        assertJobScheduled(
                callBack,
                MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);
        assertJobScheduled(
                callBack,
                MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);

        waitForJobFinished(JOB_SCHEDULED_WAIT_TIME_MS);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testScheduleIfNeeded_ScheduledWithSameParameters() throws Exception {
        // Mock static method MddFlags.getInstance() to return Mock MddFlags.
        mockGetMddFlags();
        when(mMockMddFlags.maintenanceGcmTaskPeriod()).thenReturn(TASK_PERIOD);

        // The first invocation of scheduleIfNeeded() schedules the job.
        JobServiceCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);
        assertJobScheduled(
                callBack,
                MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);

        // The second invocation of scheduleIfNeeded() with same parameters skips the scheduling.
        callBack = scheduleJobInBackground(/* forceSchedule */ false);
        assertJobScheduled(
                callBack,
                MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ false,
                /* checkPendingJob */ false);

        waitForJobFinished(JOB_SCHEDULED_WAIT_TIME_MS);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testScheduleIfNeeded_ScheduledWithDifferentParameters() throws Exception {
        // Mock static method MddFlags.getInstance() to return Mock MddFlags.
        mockGetMddFlags();
        when(mMockMddFlags.maintenanceGcmTaskPeriod()).thenReturn(TASK_PERIOD);

        // The first invocation of scheduleIfNeeded() schedules the job.
        JobServiceCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);
        assertJobScheduled(
                callBack,
                MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);

        when(mMockMddFlags.maintenanceGcmTaskPeriod()).thenReturn(TASK_PERIOD + 1L);
        // The second invocation of scheduleIfNeeded() with different parameters should schedule a
        // new job.
        callBack = scheduleJobInBackground(/* forceSchedule */ false);
        assertJobScheduled(
                callBack,
                MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);

        waitForJobFinished(JOB_SCHEDULED_WAIT_TIME_MS);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testScheduleIfNeeded_forceRun() throws Exception {
        // Mock static method MddFlags.getInstance() to return Mock MddFlags.
        mockGetMddFlags();

        when(mMockMddFlags.maintenanceGcmTaskPeriod()).thenReturn(TASK_PERIOD);
        // The first invocation of scheduleIfNeeded() schedules the job.
        JobServiceCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);
        assertJobScheduled(
                callBack,
                MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);
        assertJobScheduled(
                callBack,
                MDD_CHARGING_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);
        assertJobScheduled(
                callBack,
                MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);
        assertJobScheduled(
                callBack,
                MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);

        // The second invocation of scheduleIfNeeded() with same parameters skips the scheduling.
        callBack = scheduleJobInBackground(/* forceSchedule */ false);
        assertJobScheduled(
                callBack,
                MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ false,
                /* checkPendingJob */ false);

        // The third invocation of scheduleIfNeeded() is forced and re-schedules the job.
        callBack = scheduleJobInBackground(/* forceSchedule */ true);
        assertJobScheduled(
                callBack,
                MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);
        assertJobScheduled(
                callBack,
                MDD_CHARGING_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);
        assertJobScheduled(
                callBack,
                MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);
        assertJobScheduled(
                callBack,
                MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);

        waitForJobFinished(JOB_SCHEDULED_WAIT_TIME_MS);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testScheduleIfNeededMddSingleTask_mddMaintenancePeriodicTask() throws Exception {
        // Mock static method MddFlags.getInstance() to return Mock MddFlags.
        mockGetMddFlags();
        when(mMockMddFlags.maintenanceGcmTaskPeriod()).thenReturn(TASK_PERIOD);
        JobServiceCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);
        assertJobScheduled(
                callBack,
                MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);

        waitForJobFinished(JOB_SCHEDULED_WAIT_TIME_MS);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testScheduleIfNeededMddSingleTask_mddChargingPeriodicTask() throws Exception {
        // Mock static method MddFlags.getInstance() to return Mock MddFlags.
        mockGetMddFlags();
        when(mMockMddFlags.chargingGcmTaskPeriod()).thenReturn(TASK_PERIOD);
        JobServiceCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);
        assertJobScheduled(
                callBack,
                MDD_CHARGING_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);

        waitForJobFinished(JOB_SCHEDULED_WAIT_TIME_MS);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testScheduleIfNeededMddSingleTask_mddCellularChargingPeriodicTask()
            throws Exception {
        // Mock static method MddFlags.getInstance() to return Mock MddFlags.
        mockGetMddFlags();
        when(mMockMddFlags.cellularChargingGcmTaskPeriod()).thenReturn(TASK_PERIOD);
        JobServiceCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);
        assertJobScheduled(
                callBack,
                MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);

        waitForJobFinished(JOB_SCHEDULED_WAIT_TIME_MS);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testScheduleIfNeededMddSingleTask_mddWifiChargingPeriodicTask() throws Exception {
        // Mock static method MddFlags.getInstance() to return Mock MddFlags.
        mockGetMddFlags();
        when(mMockMddFlags.wifiChargingGcmTaskPeriod()).thenReturn(TASK_PERIOD);
        JobServiceCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);
        assertJobScheduled(
                callBack,
                MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID,
                /* shouldSchedule */ true,
                /* checkPendingJob */ true);

        waitForJobFinished(JOB_SCHEDULED_WAIT_TIME_MS);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testOnStartJob_shouldDisableJobTrue_withoutLogging() {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, true);

        testOnStartJob_shouldDisableJobTrue();

        // Verify logging method is not invoked.
        verifyLoggingNotHappened(mLogger);
    }

    @Test
    @FlakyTest(bugId = 315980870)
    public void testOnStartJob_shouldDisableJobTrue_withLoggingEnabled() {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, false);

        testOnStartJob_shouldDisableJobTrue();

        // Verify no logging has happened even though logging is enabled because this field is not
        // logged
        verifyLoggingNotHappened(mLogger);
    }

    private void testOnStartJob_killswitchIsOn() throws InterruptedException {
        // Killswitch is on.
        mockMddBackgroundTaskKillSwitch(/* toBeReturned */ true);
        doNothing().when(mSpyMddJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID,
                                new ComponentName(CONTEXT, MddJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(INTERVAL_MS, FLEX_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertThat(JOB_SCHEDULER.getPendingJob(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID)).isNotNull();

        JobServiceCallback callback = createJobFinishedCallback(mSpyMddJobService);

        // Now verify that when the Job starts, it will unschedule itself.
        assertThat(mSpyMddJobService.onStartJob(mMockJobParameters)).isFalse();
        assertThat(JOB_SCHEDULER.getPendingJob(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID)).isNull();

        callback.assertJobFinished();

        verify(mSpyMddJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(MobileDataDownloadFactory.class));
    }

    private void testOnStartJob_killswitchIsOff() throws InterruptedException {
        // Killswitch is off.
        mockMddBackgroundTaskKillSwitch(/* toBeReturned */ false);

        doReturn(mMockMdd)
                .when(() -> MobileDataDownloadFactory.getMdd(any(Context.class), any(Flags.class)));

        JobServiceCallback callback = createJobFinishedCallback(mSpyMddJobService);

        mSpyMddJobService.onStartJob(mMockJobParameters);

        callback.assertJobFinished();

        // Check that Mdd.handleTask is executed.
        verify(() -> MobileDataDownloadFactory.getMdd(any(Context.class), any(Flags.class)));
        verify(mMockMdd).handleTask(WIFI_CHARGING_PERIODIC_TASK);
    }

    private void testOnStopJob() throws InterruptedException {
        JobServiceCallback callback = createOnStopJobCallback(mSpyMddJobService);

        // Verify nothing throws
        mSpyMddJobService.onStopJob(mMockJobParameters);

        callback.assertJobFinished();
    }

    private void testOnStartJob_shouldDisableJobTrue() {
        doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));

        doNothing().when(mSpyMddJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID,
                                new ComponentName(CONTEXT, MddJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(INTERVAL_MS, FLEX_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID));

        // Now verify that when the Job starts, it will unschedule itself.
        assertFalse(mSpyMddJobService.onStartJob(mMockJobParameters));

        assertNull(JOB_SCHEDULER.getPendingJob(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID));

        verify(mSpyMddJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(MobileDataDownloadFactory.class));
    }

    // TODO(b/296945680): remove Thread.sleep().
    /**
     * Waits for current running job to finish before mocked {@code Flags} finished mocking.
     *
     * <p>Tests needs to call this at the end of the test if scheduled background job to prevent
     * {@code android.permission.READ_DEVICE_CONFIG} permission error when scheduled job start
     * running after mocked {@code flags} finished mocking.
     */
    public void waitForJobFinished(int timeOutMs) throws InterruptedException {
        Thread.sleep(timeOutMs);
    }

    private void mockGetMddFlags() {
        doReturn(mMockMddFlags).when(MddFlags::getInstance);
    }

    private void mockMddBackgroundTaskKillSwitch(boolean toBeReturned) {
        doReturn(toBeReturned).when(mMockFlags).getMddBackgroundTaskKillSwitch();
    }

    private JobServiceCallback createJobFinishedCallback(JobService jobService) {
        JobServiceCallback callback = new JobServiceCallback();

        doAnswer(
                        unusedInvocation -> {
                            callback.onJobFinished();
                            return null;
                        })
                .when(jobService)
                .jobFinished(any(), anyBoolean());

        return callback;
    }

    private JobServiceCallback createOnStopJobCallback(JobService jobService) {
        JobServiceCallback callback = new JobServiceCallback();

        doAnswer(
                        invocation -> {
                            invocation.callRealMethod();
                            callback.onJobStopped();
                            return null;
                        })
                .when(jobService)
                .onStopJob(any());

        return callback;
    }

    private JobServiceCallback scheduleJobInBackground(boolean forceSchedule) {
        JobServiceCallback callback = new JobServiceCallback();

        mExecutorService.execute(
                () ->
                        callback.insertJobScheduledResult(
                                MddJobService.scheduleIfNeeded(CONTEXT, forceSchedule)));

        return callback;
    }

    private void assertJobScheduled(
            JobServiceCallback callback, int jobId, boolean shouldSchedule, boolean checkPendingJob)
            throws InterruptedException {
        assertWithMessage(
                        "Check callback received result. jobId: %s, shouldSchedule: %s",
                        jobId, shouldSchedule)
                .that(callback.assertResultReceived())
                .isEqualTo(shouldSchedule);

        if (checkPendingJob) {
            assertThat(JOB_SCHEDULER.getPendingJob(jobId)).isNotNull();
        }
    }
}
