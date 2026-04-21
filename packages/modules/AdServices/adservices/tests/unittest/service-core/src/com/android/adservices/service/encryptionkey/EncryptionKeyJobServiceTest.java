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

package com.android.adservices.service.encryptionkey;

import static com.android.adservices.service.Flags.ENCRYPTION_KEY_JOB_PERIOD_MS;
import static com.android.adservices.spe.AdservicesJobInfo.ENCRYPTION_KEY_PERIODIC_JOB;

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

import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.stats.Clock;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.adservices.spe.AdservicesJobServiceLogger;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.CallsRealMethods;
import org.mockito.quality.Strictness;

/** Unit test for {@link EncryptionKeyJobService}. */
public class EncryptionKeyJobServiceTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final int ENCRYPTION_KEY_JOB_ID = ENCRYPTION_KEY_PERIODIC_JOB.getJobId();
    private static final long WAIT_IN_MILLIS = 1000L;
    private JobScheduler mMockJobScheduler;
    private JobParameters mJobParameters;
    private AdservicesJobServiceLogger mSpyLogger;
    private EncryptionKeyJobService mSpyService;

    @Before
    public void setUp() {
        mSpyService = spy(new EncryptionKeyJobService());
        mMockJobScheduler = mock(JobScheduler.class);
        mJobParameters = mock(JobParameters.class);
        StatsdAdServicesLogger mockStatsdLogger = mock(StatsdAdServicesLogger.class);
        mSpyLogger =
                spy(new AdservicesJobServiceLogger(CONTEXT, Clock.SYSTEM_CLOCK, mockStatsdLogger));
    }

    @Test
    public void testOnStartJob_killSwitchOn() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    enableKillSwitch();

                    // Execute
                    boolean result = mSpyService.onStartJob(mJobParameters);

                    // Validate
                    assertFalse(result);
                    // Allow background thread to execute
                    Thread.sleep(WAIT_IN_MILLIS);
                    verify(mSpyService, times(1)).jobFinished(any(), eq(false));
                    verify(mMockJobScheduler, times(1)).cancel(ENCRYPTION_KEY_JOB_ID);
                });
    }

    @Test
    public void testOnStartJob_killSwitchOff() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    ExtendedMockito.doReturn(true)
                            .when(
                                    () ->
                                            EncryptionKeyJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));

                    // Execute
                    boolean result = mSpyService.onStartJob(mJobParameters);

                    // Validate
                    assertTrue(result);
                    // Allow background thread to execute
                    Thread.sleep(WAIT_IN_MILLIS);
                    verify(mSpyService, times(1)).jobFinished(any(), anyBoolean());
                    verify(mMockJobScheduler, never()).cancel(eq(ENCRYPTION_KEY_JOB_ID));
                });
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue() throws Exception {
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
                    verify(mSpyService, times(1)).jobFinished(any(), eq(false));
                    verify(mMockJobScheduler, times(1)).cancel(eq(ENCRYPTION_KEY_JOB_ID));
                    ExtendedMockito.verifyZeroInteractions(
                            ExtendedMockito.staticMockMarker(FlagsFactory.class));
                });
    }

    @Test
    public void testScheduleIfNeeded_killSwitchOn_dontSchedule() throws Exception {
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
                            .getPendingJob(eq(ENCRYPTION_KEY_JOB_ID));

                    // Execute
                    assertFalse(
                            EncryptionKeyJobService.scheduleIfNeeded(
                                    mockContext, /* forceSchedule = */ false));

                    // Validate
                    ExtendedMockito.verify(
                            () -> EncryptionKeyJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, never()).getPendingJob(eq(ENCRYPTION_KEY_JOB_ID));
                });
    }

    @Test
    public void testScheduleIfNeeded_killSwitchOff_sameJobInfoDontForceSchedule_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    final Context mockContext = spy(ApplicationProvider.getApplicationContext());
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);

                    Flags mockFlags = Mockito.mock(Flags.class);
                    ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);
                    when(mockFlags.getEncryptionKeyJobPeriodMs()).thenReturn(24 * 60 * 60 * 1000L);
                    when(mockFlags.getEncryptionKeyJobRequiredNetworkType())
                            .thenReturn(JobInfo.NETWORK_TYPE_UNMETERED);

                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            ENCRYPTION_KEY_JOB_ID,
                                            new ComponentName(
                                                    mockContext, EncryptionKeyJobService.class))
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                                    .setPeriodic(24 * 60 * 60 * 1000L)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(ENCRYPTION_KEY_JOB_ID));

                    // Execute
                    assertFalse(
                            EncryptionKeyJobService.scheduleIfNeeded(
                                    mockContext, /* forceSchedule = */ false));

                    // Validate
                    ExtendedMockito.verify(
                            () -> EncryptionKeyJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, times(1)).getPendingJob(eq(ENCRYPTION_KEY_JOB_ID));
                });
    }

    @Test
    public void testScheduleIfNeeded_killSwitchOff_diffJobInfoDontForceSchedule_schedule()
            throws Exception {
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
                                            ENCRYPTION_KEY_JOB_ID,
                                            new ComponentName(
                                                    mockContext, EncryptionKeyJobService.class))
                                    // Difference
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(ENCRYPTION_KEY_JOB_ID));

                    // Execute
                    assertTrue(
                            EncryptionKeyJobService.scheduleIfNeeded(
                                    mockContext, /* forceSchedule = */ false));

                    // Validate
                    ExtendedMockito.verify(() -> EncryptionKeyJobService.schedule(any(), any()));
                    verify(mMockJobScheduler, times(1)).getPendingJob(eq(ENCRYPTION_KEY_JOB_ID));
                });
    }

    @Test
    public void testScheduleIfNeeded_killSwitchOff_previouslyExecuted_forceSchedule_schedule()
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
                            .getPendingJob(eq(ENCRYPTION_KEY_JOB_ID));

                    // Execute
                    assertTrue(
                            EncryptionKeyJobService.scheduleIfNeeded(
                                    mockContext, /* forceSchedule = */ true));

                    // Validate
                    ExtendedMockito.verify(
                            () -> EncryptionKeyJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1)).getPendingJob(eq(ENCRYPTION_KEY_JOB_ID));
                });
    }

    @Test
    public void
            testScheduleIfNeeded_killSwitchOff_previouslyNotExecuted_dontForceSchedule_schedule()
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
                            .getPendingJob(eq(ENCRYPTION_KEY_JOB_ID));

                    // Execute
                    assertTrue(
                            EncryptionKeyJobService.scheduleIfNeeded(
                                    mockContext, /* forceSchedule = */ false));

                    // Validate
                    ExtendedMockito.verify(
                            () -> EncryptionKeyJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1)).getPendingJob(eq(ENCRYPTION_KEY_JOB_ID));
                });
    }

    @Test
    public void testOnStopJob_stopsExecutingThread() throws Exception {
        runWithMocks(
                () -> {
                    disableKillSwitch();

                    doAnswer(new AnswersWithDelay(WAIT_IN_MILLIS * 10, new CallsRealMethods()))
                            .when(mSpyService)
                            .fetchAndUpdateEncryptionKeys();
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
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(AdServicesConfig.class)
                        .spyStatic(EnrollmentDao.class)
                        .spyStatic(EncryptionKeyDao.class)
                        .spyStatic(EncryptionKeyJobService.class)
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(AdservicesJobServiceLogger.class)
                        .mockStatic(ServiceCompatUtils.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        try {
            // Setup mock everything in job
            doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
            doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
            doReturn(Mockito.mock(Context.class)).when(mSpyService).getApplicationContext();
            ExtendedMockito.doReturn(mock(EnrollmentDao.class))
                    .when(() -> EnrollmentDao.getInstance(any()));
            ExtendedMockito.doReturn(mock(EncryptionKeyDao.class))
                    .when(() -> EncryptionKeyDao.getInstance(any()));
            ExtendedMockito.doNothing().when(() -> EncryptionKeyJobService.schedule(any(), any()));

            // Mock AdservicesJobServiceLogger to not actually log the stats to server
            Mockito.doNothing()
                    .when(mSpyLogger)
                    .logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
            ExtendedMockito.doReturn(mSpyLogger)
                    .when(() -> AdservicesJobServiceLogger.getInstance(any(Context.class)));

            // Execute
            execute.run();
        } finally {
            session.finishMocking();
        }
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
        ExtendedMockito.doReturn(value).when(mockFlags).getEncryptionKeyPeriodicFetchKillSwitch();
        when(mockFlags.getEncryptionKeyJobRequiredNetworkType())
                .thenReturn(JobInfo.NETWORK_TYPE_UNMETERED);
        when(mockFlags.getEncryptionKeyJobPeriodMs()).thenReturn(ENCRYPTION_KEY_JOB_PERIOD_MS);
    }
}
