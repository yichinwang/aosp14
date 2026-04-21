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

package com.android.adservices.service.signals;

import static com.android.adservices.service.common.Throttler.ApiKey.PROTECTED_SIGNAL_API_UPDATE_SIGNALS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.signals.UpdateSignalsCallback;
import android.adservices.signals.UpdateSignalsInput;
import android.content.Context;
import android.net.Uri;
import android.os.LimitExceededException;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.Flags;
import com.android.adservices.service.common.CallingAppUidSupplier;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.concurrent.ExecutorService;

public class ProtectedSignalsServiceImplTest {

    // TODO(b/296586554) Add API id
    private static final int API_NAME = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
    private static final int UID = 42;
    private static final AdTechIdentifier ADTECH = AdTechIdentifier.fromString("example.com");
    private static final Uri URI = Uri.parse("https://example.com");
    private static final String PACKAGE = CommonFixture.TEST_PACKAGE_NAME_1;
    private static final String EXCEPTION_MESSAGE = "message";

    private MockitoSession mStaticMockSession = null;
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final ExecutorService DIRECT_EXECUTOR = MoreExecutors.newDirectExecutorService();
    @Mock private UpdateSignalsOrchestrator mUpdateSignalsOrchestratorMock;
    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private DevContextFilter mDevContextFilterMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private Flags mFlagsMock;
    @Mock private CallingAppUidSupplier mCallingAppUidSupplierMock;
    @Mock private CustomAudienceServiceFilter mCustomAudienceServiceFilterMock;
    @Mock private UpdateSignalsCallback mUpdateSignalsCallbackMock;

    @Captor ArgumentCaptor<FledgeErrorResponse> mErrorCaptor;

    private ProtectedSignalsServiceImpl mProtectedSignalsService;
    private DevContext mDevContext;
    private UpdateSignalsInput mInput;

    @Before
    public void setup() {

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(PeriodicEncodingJobService.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();

        mProtectedSignalsService =
                new ProtectedSignalsServiceImpl(
                        CONTEXT,
                        mUpdateSignalsOrchestratorMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mFlagsMock,
                        mCallingAppUidSupplierMock,
                        mCustomAudienceServiceFilterMock);

        mDevContext =
                DevContext.builder()
                        .setDevOptionsEnabled(false)
                        .setCallingAppPackageName(PACKAGE)
                        .build();
        mInput = new UpdateSignalsInput.Builder(URI, PACKAGE).build();

        // Set up the mocks for a success flow -- indivual tests that want a failure can overwrite
        when(mCallingAppUidSupplierMock.getCallingAppUid()).thenReturn(UID);
        when(mFlagsMock.getDisableFledgeEnrollmentCheck()).thenReturn(false);
        when(mFlagsMock.getEnforceForegroundStatusForSignals()).thenReturn(true);
        when(mDevContextFilterMock.createDevContext()).thenReturn(mDevContext);
        when(mCustomAudienceServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_UPDATE_SIGNALS),
                        eq(mDevContext)))
                .thenReturn(ADTECH);
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(false);
        SettableFuture<Object> emptyReturn = SettableFuture.create();
        emptyReturn.set(new Object());
        when(mUpdateSignalsOrchestratorMock.orchestrateUpdate(
                        eq(URI), eq(ADTECH), eq(PACKAGE), eq(mDevContext)))
                .thenReturn(FluentFuture.from(emptyReturn));
        doNothing()
                .when(
                        () ->
                                PeriodicEncodingJobService.scheduleIfNeeded(
                                        any(), any(), anyBoolean()));
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testUpdateSignalsSuccess() throws Exception {
        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredProtectedSignalsPermission(
                        eq(CONTEXT), eq(PACKAGE), eq(API_NAME));
        verify(mCallingAppUidSupplierMock).getCallingAppUid();
        verify(mDevContextFilterMock).createDevContext();
        verify(mFlagsMock).getDisableFledgeEnrollmentCheck();
        verify(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_UPDATE_SIGNALS),
                        eq(mDevContext));
        verify(mConsentManagerMock).isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE));
        verify(mUpdateSignalsOrchestratorMock)
                .orchestrateUpdate(eq(URI), eq(ADTECH), eq(PACKAGE), eq(mDevContext));
        verify(mUpdateSignalsCallbackMock).onSuccess();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(AdServicesStatusUtils.STATUS_SUCCESS), eq(0));
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(1));
    }

    @Test
    public void testUpdateSignalsNullInput() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> mProtectedSignalsService.updateSignals(null, mUpdateSignalsCallbackMock));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(API_NAME, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, 0);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    public void testUpdateSignalsNullCallback() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> mProtectedSignalsService.updateSignals(mInput, null));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(API_NAME, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, 0);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    public void testUpdateSignalsExceptionGettingUid() throws Exception {
        when(mCallingAppUidSupplierMock.getCallingAppUid()).thenThrow(new IllegalStateException());
        assertThrows(
                IllegalStateException.class,
                () -> mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(AdServicesStatusUtils.STATUS_INTERNAL_ERROR), eq(0));
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    public void testUpdateSignalsFilterException() throws Exception {
        when(mCustomAudienceServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_UPDATE_SIGNALS),
                        eq(mDevContext)))
                .thenThrow(new LimitExceededException(EXCEPTION_MESSAGE));
        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);

        verify(mUpdateSignalsCallbackMock).onFailure(mErrorCaptor.capture());
        FledgeErrorResponse actual = mErrorCaptor.getValue();
        assertEquals(AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED, actual.getStatusCode());
        assertEquals(EXCEPTION_MESSAGE, actual.getErrorMessage());
        verifyZeroInteractions(mAdServicesLoggerMock);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    public void testUpdateSignalsIllegalArgumentException() throws Exception {
        IllegalArgumentException exception = new IllegalArgumentException(EXCEPTION_MESSAGE);
        SettableFuture<Object> future = SettableFuture.create();
        future.setException(exception);
        when(mUpdateSignalsOrchestratorMock.orchestrateUpdate(
                        eq(URI), eq(ADTECH), eq(PACKAGE), eq(mDevContext)))
                .thenReturn(FluentFuture.from(future));
        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);

        verify(mUpdateSignalsCallbackMock).onFailure(mErrorCaptor.capture());
        FledgeErrorResponse actual = mErrorCaptor.getValue();
        assertEquals(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, actual.getStatusCode());
        assertEquals(EXCEPTION_MESSAGE, actual.getErrorMessage());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT), eq(0));
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    public void testUpdateSignalsNoConsent() throws Exception {
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(true);
        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED), eq(0));
        verify(mUpdateSignalsCallbackMock).onSuccess();
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    public void testUpdateSignalsCallbackException() throws Exception {
        doThrow(new RuntimeException()).when(mUpdateSignalsCallbackMock).onSuccess();
        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(AdServicesStatusUtils.STATUS_INTERNAL_ERROR), eq(0));
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(1));
    }
}
