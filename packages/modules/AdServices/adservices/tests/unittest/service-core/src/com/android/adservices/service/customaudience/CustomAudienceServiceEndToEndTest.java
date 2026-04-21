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

package com.android.adservices.service.customaudience;

import static android.adservices.common.AdServicesStatusUtils.STATUS_SERVER_RATE_LIMIT_REACHED;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_NAME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_OWNER;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS;

import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.customaudience.FetchCustomAudienceFixture.getFullSuccessfulJsonResponseString;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyBoolean;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierFailureImpl;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.adservices.customaudience.FetchAndJoinCustomAudienceInput;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.IBinder;
import android.os.LimitExceededException;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceOverride;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.adselection.JsVersionRegister;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FrequencyCapAdDataValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidatorNoOpImpl;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class CustomAudienceServiceEndToEndTest {
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    protected static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private static final CustomAudience CUSTOM_AUDIENCE_PK1_1 =
            CustomAudienceFixture.getValidBuilderForBuyerFilters(CommonFixture.VALID_BUYER_1)
                    .build();

    private static final CustomAudience CUSTOM_AUDIENCE_PK1_2 =
            CustomAudienceFixture.getValidBuilderForBuyerFilters(CommonFixture.VALID_BUYER_1)
                    .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME)
                    .build();

    private static final CustomAudience CUSTOM_AUDIENCE_PK1_BEYOND_MAX_EXPIRATION_TIME =
            CustomAudienceFixture.getValidBuilderForBuyerFilters(CommonFixture.VALID_BUYER_1)
                    .setExpirationTime(CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME)
                    .build();

    private static final DBCustomAudience DB_CUSTOM_AUDIENCE_PK1_1 =
            DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build();

    private static final DBCustomAudience DB_CUSTOM_AUDIENCE_PK1_2 =
            DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                    .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME)
                    .build();

    private static final String MY_APP_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final AdTechIdentifier LOCALHOST_BUYER =
            AdTechIdentifier.fromString("localhost");
    private static final AdTechIdentifier BUYER_1 = AdTechIdentifier.fromString("BUYER_1");
    private static final AdTechIdentifier BUYER_2 = AdTechIdentifier.fromString("BUYER_2");
    private static final String NAME_1 = "NAME_1";
    private static final String NAME_2 = "NAME_2";
    private static final String BIDDING_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final AdSelectionSignals TRUSTED_BIDDING_DATA =
            AdSelectionSignals.fromString("{\"trusted_bidding_data\":1}");

    private static final FrequencyCapAdDataValidator FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP =
            new FrequencyCapAdDataValidatorNoOpImpl();

    private static final AdRenderIdValidator RENDER_ID_VALIDATOR_NO_OP =
            AdRenderIdValidator.AD_RENDER_ID_VALIDATOR_NO_OP;

    private CustomAudienceDao mCustomAudienceDao;
    private AppInstallDao mAppInstallDao;
    private FrequencyCapDao mFrequencyCapDao;

    private CustomAudienceServiceImpl mService;

    private MockitoSession mStaticMockSession = null;

    @Mock private ConsentManager mConsentManagerMock;
    @Mock private CustomAudienceQuantityChecker mCustomAudienceQuantityCheckerMock;
    @Mock private CustomAudienceValidator mCustomAudienceValidatorMock;

    // This object access some system APIs
    @Mock private DevContextFilter mDevContextFilter;
    @Mock private Throttler mMockThrottler;
    @Mock private AppImportanceFilter mAppImportanceFilter;

    private FledgeAuthorizationFilter mFledgeAuthorizationFilterSpy;

    private final AdServicesLogger mAdServicesLogger = AdServicesLoggerImpl.getInstance();
    private Uri mFetchUri;

    private CustomAudienceQuantityChecker mCustomAudienceQuantityChecker;
    private CustomAudienceValidator mCustomAudienceValidator;

    @Before
    public void setup() {
        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(BackgroundFetchJobService.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        ExtendedMockito.doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        mFetchUri = mMockWebServerRule.uriForPath("/fetch");

        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true))
                        .build()
                        .customAudienceDao();

        SharedStorageDatabase sharedDb =
                Room.inMemoryDatabaseBuilder(CONTEXT, SharedStorageDatabase.class).build();
        mAppInstallDao = sharedDb.appInstallDao();
        mFrequencyCapDao = sharedDb.frequencyCapDao();
        AdSelectionServerDatabase serverDb =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionServerDatabase.class).build();

        mCustomAudienceQuantityChecker =
                new CustomAudienceQuantityChecker(mCustomAudienceDao, CommonFixture.FLAGS_FOR_TEST);

        mCustomAudienceValidator =
                new CustomAudienceValidator(
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        CommonFixture.FLAGS_FOR_TEST,
                        FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP,
                        RENDER_ID_VALIDATOR_NO_OP);

        // Spy FledgeAuthorizationFilter to bypass the permission check in some tests in order to
        // validate other checks, such as package name mismatching.
        mFledgeAuthorizationFilterSpy =
                spy(
                        new FledgeAuthorizationFilter(
                                CONTEXT.getPackageManager(),
                                EnrollmentDao.getInstance(CONTEXT),
                                mAdServicesLogger));

        mService =
                new CustomAudienceServiceImpl(
                        CONTEXT,
                        new CustomAudienceImpl(
                                mCustomAudienceDao,
                                mCustomAudienceQuantityChecker,
                                mCustomAudienceValidator,
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                CommonFixture.FLAGS_FOR_TEST),
                        mFledgeAuthorizationFilterSpy,
                        mConsentManagerMock,
                        mDevContextFilter,
                        MoreExecutors.newDirectExecutorService(),
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        CommonFixture.FLAGS_FOR_TEST,
                        CallingAppUidSupplierProcessImpl.create(),
                        new CustomAudienceServiceFilter(
                                CONTEXT,
                                mConsentManagerMock,
                                CommonFixture.FLAGS_FOR_TEST,
                                mAppImportanceFilter,
                                new FledgeAuthorizationFilter(
                                        CONTEXT.getPackageManager(),
                                        EnrollmentDao.getInstance(CONTEXT),
                                        mAdServicesLogger),
                                new FledgeAllowListsFilter(
                                        CommonFixture.FLAGS_FOR_TEST, mAdServicesLogger),
                                mMockThrottler),
                        new AdFilteringFeatureFactory(
                                mAppInstallDao, mFrequencyCapDao, CommonFixture.FLAGS_FOR_TEST));

        Mockito.lenient()
                .when(mMockThrottler.tryAcquire(eq(FLEDGE_API_JOIN_CUSTOM_AUDIENCE), anyString()))
                .thenReturn(true);
        Mockito.lenient()
                .when(mMockThrottler.tryAcquire(eq(FLEDGE_API_FETCH_CUSTOM_AUDIENCE), anyString()))
                .thenReturn(true);
        Mockito.lenient()
                .when(mMockThrottler.tryAcquire(eq(FLEDGE_API_LEAVE_CUSTOM_AUDIENCE), anyString()))
                .thenReturn(true);
        Mockito.doReturn(DevContext.createForDevOptionsDisabled())
                .when(mDevContextFilter)
                .createDevContext();
    }

    @After
    public void teardown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testJoinCustomAudience_notInBinderThread_fail() {
        CustomAudienceQuantityChecker customAudienceQuantityChecker =
                new CustomAudienceQuantityChecker(mCustomAudienceDao, CommonFixture.FLAGS_FOR_TEST);

        CustomAudienceValidator customAudienceValidator =
                new CustomAudienceValidator(
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        CommonFixture.FLAGS_FOR_TEST,
                        FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP,
                        RENDER_ID_VALIDATOR_NO_OP);

        mService =
                new CustomAudienceServiceImpl(
                        CONTEXT,
                        new CustomAudienceImpl(
                                mCustomAudienceDao,
                                customAudienceQuantityChecker,
                                customAudienceValidator,
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                CommonFixture.FLAGS_FOR_TEST),
                        new FledgeAuthorizationFilter(
                                CONTEXT.getPackageManager(),
                                EnrollmentDao.getInstance(CONTEXT),
                                mAdServicesLogger),
                        mConsentManagerMock,
                        mDevContextFilter,
                        MoreExecutors.newDirectExecutorService(),
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        CommonFixture.FLAGS_FOR_TEST,
                        CallingAppUidSupplierFailureImpl.create(),
                        new CustomAudienceServiceFilter(
                                CONTEXT,
                                mConsentManagerMock,
                                CommonFixture.FLAGS_FOR_TEST,
                                mAppImportanceFilter,
                                new FledgeAuthorizationFilter(
                                        CONTEXT.getPackageManager(),
                                        EnrollmentDao.getInstance(CONTEXT),
                                        mAdServicesLogger),
                                new FledgeAllowListsFilter(
                                        CommonFixture.FLAGS_FOR_TEST, mAdServicesLogger),
                                mMockThrottler),
                        new AdFilteringFeatureFactory(
                                mAppInstallDao, mFrequencyCapDao, CommonFixture.FLAGS_FOR_TEST));

        ResultCapturingCallback callback = new ResultCapturingCallback();
        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.joinCustomAudience(
                                CUSTOM_AUDIENCE_PK1_1,
                                CustomAudienceFixture.VALID_OWNER,
                                callback));
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));
    }

    @Test
    public void testJoinCustomAudience_callerPackageNameMismatch_fail() {
        String otherOwnerPackageName = "other_owner";
        // Bypass the permission check since it's enforced before the package name check
        doNothing()
                .when(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredCustomAudiencePermission(
                        CONTEXT,
                        otherOwnerPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build(),
                otherOwnerPackageName,
                callback);

        assertFalse(callback.isSuccess());
        assertTrue(callback.getException() instanceof SecurityException);
        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE,
                callback.getException().getMessage());
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));
        verify(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredCustomAudiencePermission(
                        CONTEXT,
                        otherOwnerPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);
    }

    @Test
    public void testJoinCustomAudience_joinTwice_secondJoinOverrideValues() {
        doReturn(CommonFixture.FLAGS_FOR_TEST).when(FlagsFactory::getFlags);
        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_1, CustomAudienceFixture.VALID_OWNER, callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_2, CustomAudienceFixture.VALID_OWNER, callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));
    }

    @Test
    public void testJoinCustomAudienceWithRevokedUserConsentForAppSuccess() {
        doReturn(true)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_1, CustomAudienceFixture.VALID_OWNER, callback);
        assertTrue(callback.isSuccess());
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CUSTOM_AUDIENCE_PK1_1.getBuyer(),
                        CUSTOM_AUDIENCE_PK1_1.getName()));
    }

    @Test
    public void testJoinCustomAudience_beyondMaxExpirationTime_fail() {
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_BEYOND_MAX_EXPIRATION_TIME,
                CustomAudienceFixture.VALID_OWNER,
                callback);
        assertFalse(callback.isSuccess());
        assertTrue(callback.getException() instanceof IllegalArgumentException);
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));
    }

    @Test
    public void testFetchAndJoinCustomAudience_overridesJoinCustomAudience() throws Exception {
        // Join a custom audience using the joinCustomAudience API.
        ResultCapturingCallback joinCallback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CustomAudienceFixture.getValidBuilderForBuyerFilters(LOCALHOST_BUYER).build(),
                CustomAudienceFixture.VALID_OWNER,
                joinCallback);
        assertTrue(joinCallback.mIsSuccess);

        // Fetch and join a custom audience with the same owner, buyer and name but a different
        // value for one of fields. In this case, we'll use a different activation time.
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponseString(
                                                        LOCALHOST_BUYER))));
        FetchAndJoinCustomAudienceInput input =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(VALID_NAME)
                        .setActivationTime(VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback);
        resultLatch.await();
        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(fetchAndJoinCallback.mIsSuccess);

        // Assert persisted custom audience's activation time is from the fetched custom audience.
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, VALID_NAME);
        assertEquals(VALID_DELAYED_ACTIVATION_TIME, persistedCustomAudience.getActivationTime());
    }

    @Test
    public void testFetchAndJoinCustomAudience_overriddenByJoinCustomAudience() throws Exception {
        // Fetch and join a custom audience.
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponseString(
                                                        LOCALHOST_BUYER))));
        FetchAndJoinCustomAudienceInput input =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(VALID_NAME)
                        .setActivationTime(VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback);
        resultLatch.await();
        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(fetchAndJoinCallback.mIsSuccess);

        // Join a custom audience using the joinCustomAudience API with the same owner, buyer and
        // name but a different value for one of fields. In this case, we'll use a different
        // activation time.
        ResultCapturingCallback joinCallback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CustomAudienceFixture.getValidBuilderForBuyerFilters(LOCALHOST_BUYER).build(),
                CustomAudienceFixture.VALID_OWNER,
                joinCallback);
        assertTrue(joinCallback.mIsSuccess);

        // Assert persisted custom audience's activation time is from the joined custom audience.
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, VALID_NAME);
        assertEquals(VALID_ACTIVATION_TIME, persistedCustomAudience.getActivationTime());
    }

    @Test
    public void testFetchAndJoinCustomAudience_FirstJoinsQuarantineTableSecondIsFiltered()
            throws Exception {
        assertFalse(mCustomAudienceDao.doesCustomAudienceQuarantineExist(VALID_OWNER, BUYER_1));

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse().setResponseCode(429)));

        FetchAndJoinCustomAudienceInput input =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(VALID_NAME)
                        .setActivationTime(VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();
        CountDownLatch resultLatch1 = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback1 =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch1);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback1);
        resultLatch1.await();
        assertEquals(1, mockWebServer.getRequestCount());
        assertFalse(fetchAndJoinCallback1.mIsSuccess);
        assertEquals(
                STATUS_SERVER_RATE_LIMIT_REACHED,
                fetchAndJoinCallback1.mFledgeErrorResponse.getStatusCode());
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceQuarantineExist(VALID_OWNER, LOCALHOST_BUYER));

        // Try to make the same request again, should fail with STATUS_SERVER_RATE_LIMIT_REACHED but
        // should not request the server
        CountDownLatch resultLatch2 = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback2 =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch2);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback2);
        resultLatch2.await();
        // Assert a new request was not made
        assertEquals(1, mockWebServer.getRequestCount());
        assertFalse(fetchAndJoinCallback2.mIsSuccess);
        assertEquals(
                STATUS_SERVER_RATE_LIMIT_REACHED,
                fetchAndJoinCallback2.mFledgeErrorResponse.getStatusCode());
    }

    @Test
    public void testFetchAndJoinCustomAudience_FirstJoinsQuarantineTableSecondPasses()
            throws Exception {
        Flags flagsWithZeroMinRetry =
                new CustomAudienceServiceE2ETestFlags() {
                    @Override
                    public long getFledgeFetchCustomAudienceMinRetryAfterValueMs() {
                        return 0;
                    }
                };

        reInitServiceWithFlags(flagsWithZeroMinRetry);

        assertFalse(mCustomAudienceDao.doesCustomAudienceQuarantineExist(VALID_OWNER, BUYER_1));

        // Start server that returns 429 with 1 ms before expiration in first call, and allows the
        // second call
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setResponseCode(429).setHeader("Retry-After", 1),
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponseString(
                                                        LOCALHOST_BUYER))));

        FetchAndJoinCustomAudienceInput input =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(VALID_NAME)
                        .setActivationTime(VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();
        CountDownLatch resultLatch1 = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback1 =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch1);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback1);
        resultLatch1.await();
        assertEquals(1, mockWebServer.getRequestCount());
        assertFalse(fetchAndJoinCallback1.mIsSuccess);
        assertEquals(
                STATUS_SERVER_RATE_LIMIT_REACHED,
                fetchAndJoinCallback1.mFledgeErrorResponse.getStatusCode());
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceQuarantineExist(VALID_OWNER, LOCALHOST_BUYER));

        // Try to make the same request again, should pass this time
        CountDownLatch resultLatch2 = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback2 =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch2);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback2);
        resultLatch2.await();
        // Assert a new request was not made
        assertEquals(2, mockWebServer.getRequestCount());
        assertTrue(fetchAndJoinCallback2.mIsSuccess);

        // Assert entry was cleared
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceQuarantineExist(VALID_OWNER, LOCALHOST_BUYER));

        // Assert persisted custom audience's activation time is from the fetched custom audience.
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, VALID_NAME);
        assertEquals(VALID_DELAYED_ACTIVATION_TIME, persistedCustomAudience.getActivationTime());
    }

    @Test
    public void testFetchAndJoinCustomAudience_leaveSuccessfully() throws Exception {
        // Fetch and join a custom audience.
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponseString(
                                                        LOCALHOST_BUYER))));
        FetchAndJoinCustomAudienceInput input =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(VALID_NAME)
                        .setActivationTime(VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback);
        resultLatch.await();
        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(fetchAndJoinCallback.mIsSuccess);

        // Leave the fetched and joined custom audience.
        ResultCapturingCallback leaveCallback = new ResultCapturingCallback();
        mService.leaveCustomAudience(VALID_OWNER, LOCALHOST_BUYER, VALID_NAME, leaveCallback);
        assertTrue(leaveCallback.mIsSuccess);

        // Assert the custom audience does not exist in the DB.
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, VALID_NAME));
    }

    @Test
    public void testLeaveCustomAudience_notInBinderThread_fail() {
        CustomAudienceQuantityChecker customAudienceQuantityChecker =
                new CustomAudienceQuantityChecker(mCustomAudienceDao, CommonFixture.FLAGS_FOR_TEST);

        CustomAudienceValidator customAudienceValidator =
                new CustomAudienceValidator(
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        CommonFixture.FLAGS_FOR_TEST,
                        FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP,
                        RENDER_ID_VALIDATOR_NO_OP);

        mService =
                new CustomAudienceServiceImpl(
                        CONTEXT,
                        new CustomAudienceImpl(
                                mCustomAudienceDao,
                                customAudienceQuantityChecker,
                                customAudienceValidator,
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                CommonFixture.FLAGS_FOR_TEST),
                        new FledgeAuthorizationFilter(
                                CONTEXT.getPackageManager(),
                                EnrollmentDao.getInstance(CONTEXT),
                                mAdServicesLogger),
                        mConsentManagerMock,
                        mDevContextFilter,
                        MoreExecutors.newDirectExecutorService(),
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        CommonFixture.FLAGS_FOR_TEST,
                        CallingAppUidSupplierFailureImpl.create(),
                        new CustomAudienceServiceFilter(
                                CONTEXT,
                                mConsentManagerMock,
                                CommonFixture.FLAGS_FOR_TEST,
                                mAppImportanceFilter,
                                new FledgeAuthorizationFilter(
                                        CONTEXT.getPackageManager(),
                                        EnrollmentDao.getInstance(CONTEXT),
                                        mAdServicesLogger),
                                new FledgeAllowListsFilter(
                                        CommonFixture.FLAGS_FOR_TEST, mAdServicesLogger),
                                mMockThrottler),
                        new AdFilteringFeatureFactory(
                                mAppInstallDao, mFrequencyCapDao, CommonFixture.FLAGS_FOR_TEST));

        ResultCapturingCallback callback = new ResultCapturingCallback();
        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.leaveCustomAudience(
                                CustomAudienceFixture.VALID_OWNER,
                                CommonFixture.VALID_BUYER_1,
                                VALID_NAME,
                                callback));
    }

    @Test
    public void testLeaveCustomAudience_callerPackageNameMismatch_fail() {
        String otherOwnerPackageName = "other_owner";
        // Bypass the permission check since it's enforced before the package name check
        doNothing()
                .when(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredCustomAudiencePermission(
                        CONTEXT,
                        otherOwnerPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);
        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.leaveCustomAudience(
                otherOwnerPackageName, CommonFixture.VALID_BUYER_1, VALID_NAME, callback);

        assertFalse(callback.isSuccess());
        assertTrue(callback.getException() instanceof SecurityException);
        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE,
                callback.getException().getMessage());
        verify(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredCustomAudiencePermission(
                        CONTEXT,
                        otherOwnerPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);
    }

    @Test
    public void testLeaveCustomAudience_leaveJoinedCustomAudience() {
        doReturn(CommonFixture.FLAGS_FOR_TEST).when(FlagsFactory::getFlags);
        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_1, CustomAudienceFixture.VALID_OWNER, callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        callback = new ResultCapturingCallback();
        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                VALID_NAME,
                callback);
        assertTrue(callback.isSuccess());
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)));
    }

    @Test
    public void testLeaveCustomAudience_leaveJoinedCustomAudienceFilersDisabled() {
        doReturn(
                        // CHECKSTYLE:OFF IndentationCheck
                        new Flags() {
                            @Override
                            public boolean getFledgeAdSelectionFilteringEnabled() {
                                return false;
                            }
                        })
                .when(FlagsFactory::getFlags);
        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_1, CustomAudienceFixture.VALID_OWNER, callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        callback = new ResultCapturingCallback();
        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                VALID_NAME,
                callback);
        assertTrue(callback.isSuccess());
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)));
    }

    @Test
    public void testLeaveCustomAudienceWithRevokedUserConsentForAppSuccess() {
        doReturn(CommonFixture.FLAGS_FOR_TEST).when(FlagsFactory::getFlags);
        doReturn(true).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_1, CustomAudienceFixture.VALID_OWNER, callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CUSTOM_AUDIENCE_PK1_1.getBuyer(),
                        CUSTOM_AUDIENCE_PK1_1.getName()));

        callback = new ResultCapturingCallback();
        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CUSTOM_AUDIENCE_PK1_1.getBuyer(),
                CUSTOM_AUDIENCE_PK1_1.getName(),
                callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CUSTOM_AUDIENCE_PK1_1.getBuyer(),
                        CUSTOM_AUDIENCE_PK1_1.getName()));
    }

    @Test
    public void testLeaveCustomAudience_leaveNotJoinedCustomAudience_doesNotFail() {
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                "Not exist name",
                callback);
        assertTrue(callback.isSuccess());
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));
    }

    @Test
    public void testOverrideCustomAudienceRemoteInfoSuccess() throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        CustomAudienceOverrideTestCallback callback =
                callAddOverride(
                        MY_APP_PACKAGE_NAME,
                        BUYER_1,
                        NAME_1,
                        BIDDING_LOGIC_JS,
                        JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                        TRUSTED_BIDDING_DATA,
                        mService);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
    }

    @Test
    public void testOverrideCustomAudienceRemoteInfoWithRevokedUserConsentSuccess()
            throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());
        doReturn(true).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        CustomAudienceOverrideTestCallback callback =
                callAddOverride(
                        MY_APP_PACKAGE_NAME,
                        BUYER_1,
                        NAME_1,
                        BIDDING_LOGIC_JS,
                        JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                        TRUSTED_BIDDING_DATA,
                        mService);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
    }

    @Test
    public void testOverrideCustomAudienceRemoteInfoDoesNotAddOverrideWithPackageNameNotMatchOwner()
            throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());
        // Bypass the permission check since it's enforced before the package name check
        doNothing()
                .when(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredCustomAudiencePermission(
                        CONTEXT,
                        MY_APP_PACKAGE_NAME,
                        AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO);

        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        String otherOwner = "otherOwner";

        CustomAudienceOverrideTestCallback callback =
                callAddOverride(
                        otherOwner,
                        BUYER_1,
                        NAME_1,
                        BIDDING_LOGIC_JS,
                        JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                        TRUSTED_BIDDING_DATA,
                        mService);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(otherOwner, BUYER_1, NAME_1));

        verify(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredCustomAudiencePermission(
                        CONTEXT,
                        MY_APP_PACKAGE_NAME,
                        AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO);
    }

    @Test
    public void testOverrideCustomAudienceRemoteInfoFailsWithDevOptionsDisabled() throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        assertThrows(
                SecurityException.class,
                () ->
                        callAddOverride(
                                MY_APP_PACKAGE_NAME,
                                BUYER_1,
                                NAME_1,
                                BIDDING_LOGIC_JS,
                                JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                                TRUSTED_BIDDING_DATA,
                                mService));

        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
    }

    @Test
    public void testRemoveCustomAudienceRemoteInfoOverrideSuccess() throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));

        CustomAudienceOverrideTestCallback callback =
                callRemoveOverride(MY_APP_PACKAGE_NAME, BUYER_1, NAME_1, mService);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
    }

    @Test
    public void testRemoveCustomAudienceRemoteInfoOverrideWithRevokedUserConsentSuccess()
            throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());
        doReturn(true).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));

        CustomAudienceOverrideTestCallback callback =
                callRemoveOverride(MY_APP_PACKAGE_NAME, BUYER_1, NAME_1, mService);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
    }

    @Test
    public void testRemoveCustomAudienceRemoteInfoOverrideDoesNotDeleteWithIncorrectPackageName()
            throws Exception {
        String incorrectPackageName = "incorrectPackageName";

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(incorrectPackageName)
                                .build());
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());
        // Bypass the permission check since it's enforced before the package name check
        doNothing()
                .when(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredCustomAudiencePermission(
                        CONTEXT,
                        incorrectPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE);

        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));

        CustomAudienceOverrideTestCallback callback =
                callRemoveOverride(MY_APP_PACKAGE_NAME, BUYER_1, NAME_1, mService);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
        verify(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredCustomAudiencePermission(
                        CONTEXT,
                        incorrectPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE);
    }

    @Test
    public void testRemoveCustomAudienceRemoteInfoOverrideFailsWithDevOptionsDisabled()
            throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));

        assertThrows(
                SecurityException.class,
                () -> callRemoveOverride(MY_APP_PACKAGE_NAME, BUYER_1, NAME_1, mService));

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
    }

    @Test
    public void testResetAllCustomAudienceRemoteOverridesSuccess() throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        DBCustomAudienceOverride dbCustomAudienceOverride1 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        DBCustomAudienceOverride dbCustomAudienceOverride2 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_2)
                        .setName(NAME_2)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride1);
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride2);

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));

        CustomAudienceOverrideTestCallback callback = callResetAllOverrides(mService);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
    }

    @Test
    public void testResetAllCustomAudienceRemoteOverridesWithRevokedUserConsentSuccess()
            throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());
        doReturn(true).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        DBCustomAudienceOverride dbCustomAudienceOverride1 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        DBCustomAudienceOverride dbCustomAudienceOverride2 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_2)
                        .setName(NAME_2)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride1);
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride2);

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));

        CustomAudienceOverrideTestCallback callback = callResetAllOverrides(mService);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
    }

    @Test
    public void testResetAllCustomAudienceRemoteOverridesDoesNotDeleteWithIncorrectPackageName()
            throws Exception {
        String incorrectPackageName = "incorrectPackageName";

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(incorrectPackageName)
                                .build());
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());
        // Bypass the permission check since it's enforced before the package name check
        doNothing()
                .when(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredCustomAudiencePermission(
                        CONTEXT,
                        incorrectPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES);

        DBCustomAudienceOverride dbCustomAudienceOverride1 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        DBCustomAudienceOverride dbCustomAudienceOverride2 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_2)
                        .setName(NAME_2)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride1);
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride2);

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));

        CustomAudienceOverrideTestCallback callback = callResetAllOverrides(mService);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
        verify(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredCustomAudiencePermission(
                        CONTEXT,
                        incorrectPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES);
    }

    @Test
    public void testResetAllCustomAudienceRemoteOverridesFailsWithDevOptionsDisabled() {
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        DBCustomAudienceOverride dbCustomAudienceOverride1 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        DBCustomAudienceOverride dbCustomAudienceOverride2 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_2)
                        .setName(NAME_2)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride1);
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride2);

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));

        assertThrows(SecurityException.class, () -> callResetAllOverrides(mService));

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
    }

    @Test
    public void testCustomAudience_throttledSubsequentCallFails() {
        class FlagsWithLowRateLimit implements Flags {
            @Override
            public boolean getDisableFledgeEnrollmentCheck() {
                return true;
            }

            @Override
            public boolean getFledgeAdSelectionFilteringEnabled() {
                return true;
            }

            @Override
            public float getSdkRequestPermitsPerSecond() {
                return 1f;
            }
        }

        Flags flagsWithLowRateLimit = new FlagsWithLowRateLimit();

        doReturn(flagsWithLowRateLimit).when(FlagsFactory::getFlags);
        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        Throttler.destroyExistingThrottler();
        try {
            CustomAudienceServiceImpl customAudienceService =
                    mService =
                            new CustomAudienceServiceImpl(
                                    CONTEXT,
                                    new CustomAudienceImpl(
                                            mCustomAudienceDao,
                                            mCustomAudienceQuantityCheckerMock,
                                            mCustomAudienceValidatorMock,
                                            CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                            flagsWithLowRateLimit),
                                    new FledgeAuthorizationFilter(
                                            CONTEXT.getPackageManager(),
                                            EnrollmentDao.getInstance(CONTEXT),
                                            mAdServicesLogger),
                                    mConsentManagerMock,
                                    mDevContextFilter,
                                    MoreExecutors.newDirectExecutorService(),
                                    mAdServicesLogger,
                                    mAppImportanceFilter,
                                    flagsWithLowRateLimit,
                                    CallingAppUidSupplierProcessImpl.create(),
                                    new CustomAudienceServiceFilter(
                                            CONTEXT,
                                            mConsentManagerMock,
                                            flagsWithLowRateLimit,
                                            mAppImportanceFilter,
                                            new FledgeAuthorizationFilter(
                                                    CONTEXT.getPackageManager(),
                                                    EnrollmentDao.getInstance(CONTEXT),
                                                    mAdServicesLogger),
                                            new FledgeAllowListsFilter(
                                                    flagsWithLowRateLimit, mAdServicesLogger),
                                            Throttler.getInstance(flagsWithLowRateLimit)),
                                    new AdFilteringFeatureFactory(
                                            mAppInstallDao,
                                            mFrequencyCapDao,
                                            flagsWithLowRateLimit));

            // The first call should succeed
            ResultCapturingCallback callbackFirstCall = new ResultCapturingCallback();
            customAudienceService.joinCustomAudience(
                    CUSTOM_AUDIENCE_PK1_1, CustomAudienceFixture.VALID_OWNER, callbackFirstCall);

            // The immediate subsequent call should be throttled
            ResultCapturingCallback callbackSubsequentCall = new ResultCapturingCallback();
            customAudienceService.joinCustomAudience(
                    CUSTOM_AUDIENCE_PK1_1,
                    CustomAudienceFixture.VALID_OWNER,
                    callbackSubsequentCall);

            assertWithMessage("First callback success")
                    .that(callbackFirstCall.isSuccess())
                    .isTrue();
            assertWithMessage("Inserted CA")
                    .that(
                            mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                                    CustomAudienceFixture.VALID_OWNER,
                                    CommonFixture.VALID_BUYER_1,
                                    VALID_NAME))
                    .isEqualTo(DB_CUSTOM_AUDIENCE_PK1_1);

            assertWithMessage("Second callback success")
                    .that(callbackSubsequentCall.isSuccess())
                    .isFalse();
            assertWithMessage("Second callback exception")
                    .that(callbackSubsequentCall.getException())
                    .isInstanceOf(LimitExceededException.class);
            assertWithMessage("Second callback exception")
                    .that(callbackSubsequentCall.getException())
                    .hasMessageThat()
                    .isEqualTo(AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE);
        } finally {
            resetThrottlerToNoRateLimits();
        }
    }

    /**
     * Given Throttler is singleton, & shared across tests, this method should be invoked after
     * tests that impose restrictive rate limits.
     */
    private void resetThrottlerToNoRateLimits() {
        Throttler.destroyExistingThrottler();
        final float noRateLimit = -1;
        Flags mockNoRateLimitFlags = mock(Flags.class);
        doReturn(noRateLimit).when(mockNoRateLimitFlags).getSdkRequestPermitsPerSecond();
        Throttler.getInstance(mockNoRateLimitFlags);
    }

    private CustomAudienceOverrideTestCallback callAddOverride(
            String owner,
            AdTechIdentifier buyer,
            String name,
            String biddingLogicJs,
            Long biddingLogicJsVersion,
            AdSelectionSignals trustedBiddingData,
            CustomAudienceServiceImpl customAudienceService)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        CustomAudienceOverrideTestCallback callback =
                new CustomAudienceOverrideTestCallback(resultLatch);

        customAudienceService.overrideCustomAudienceRemoteInfo(
                owner,
                buyer,
                name,
                biddingLogicJs,
                biddingLogicJsVersion,
                trustedBiddingData,
                callback);
        resultLatch.await();
        return callback;
    }

    private CustomAudienceOverrideTestCallback callRemoveOverride(
            String owner,
            AdTechIdentifier buyer,
            String name,
            CustomAudienceServiceImpl customAudienceService)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        CustomAudienceOverrideTestCallback callback =
                new CustomAudienceOverrideTestCallback(resultLatch);

        customAudienceService.removeCustomAudienceRemoteInfoOverride(owner, buyer, name, callback);

        resultLatch.await();
        return callback;
    }

    private CustomAudienceOverrideTestCallback callResetAllOverrides(
            CustomAudienceServiceImpl customAudienceService) throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        CustomAudienceOverrideTestCallback callback =
                new CustomAudienceOverrideTestCallback(resultLatch);

        customAudienceService.resetAllCustomAudienceOverrides(callback);
        resultLatch.await();
        return callback;
    }

    public static class CustomAudienceOverrideTestCallback
            extends CustomAudienceOverrideCallback.Stub {
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;
        private final CountDownLatch mCountDownLatch;

        public CustomAudienceOverrideTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    private static class ResultCapturingCallback implements ICustomAudienceCallback {
        private boolean mIsSuccess;
        private Exception mException;

        public boolean isSuccess() {
            return mIsSuccess;
        }

        public Exception getException() {
            return mException;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
        }

        @Override
        public void onFailure(FledgeErrorResponse responseParcel) throws RemoteException {
            mIsSuccess = false;
            mException = AdServicesStatusUtils.asException(responseParcel);
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Should not be called.");
        }
    }

    private void reInitServiceWithFlags(Flags flags) {
        mService =
                new CustomAudienceServiceImpl(
                        CONTEXT,
                        new CustomAudienceImpl(
                                mCustomAudienceDao,
                                mCustomAudienceQuantityChecker,
                                mCustomAudienceValidator,
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                flags),
                        mFledgeAuthorizationFilterSpy,
                        mConsentManagerMock,
                        mDevContextFilter,
                        MoreExecutors.newDirectExecutorService(),
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        flags,
                        CallingAppUidSupplierProcessImpl.create(),
                        new CustomAudienceServiceFilter(
                                CONTEXT,
                                mConsentManagerMock,
                                flags,
                                mAppImportanceFilter,
                                new FledgeAuthorizationFilter(
                                        CONTEXT.getPackageManager(),
                                        EnrollmentDao.getInstance(CONTEXT),
                                        mAdServicesLogger),
                                new FledgeAllowListsFilter(flags, mAdServicesLogger),
                                mMockThrottler),
                        new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDao, flags));
    }

    private static class CustomAudienceServiceE2ETestFlags implements Flags {
        // Using tolerant timeouts for tests to avoid flakiness.
        // Tests that need to validate timeout behaviours will override these values too.
        @Override
        public long getAdSelectionBiddingTimeoutPerCaMs() {
            return 10000;
        }

        @Override
        public long getAdSelectionScoringTimeoutMs() {
            return 10000;
        }

        @Override
        public long getAdSelectionOverallTimeoutMs() {
            return 600000;
        }

        @Override
        public boolean getEnforceIsolateMaxHeapSize() {
            return false;
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return true;
        }

        @Override
        public boolean getFledgeRegisterAdBeaconEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAdSelectionFilteringEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeFetchCustomAudienceEnabled() {
            return true;
        }

        @Override
        public boolean getMeasurementFlexibleEventReportingApiEnabled() {
            return true;
        }

        @Override
        public boolean getEnableLoggedTopic() {
            return true;
        }

        @Override
        public boolean getEnableDatabaseSchemaVersion8() {
            return true;
        }

        @Override
        public boolean getFledgeAuctionServerEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeEventLevelDebugReportingEnabled() {
            return true;
        }
    }
}
