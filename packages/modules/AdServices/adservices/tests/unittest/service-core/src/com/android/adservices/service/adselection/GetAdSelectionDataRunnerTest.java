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

package com.android.adservices.service.adselection;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.GetAdSelectionDataCallback;
import android.adservices.adselection.GetAdSelectionDataInput;
import android.adservices.adselection.GetAdSelectionDataResponse;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.AssetFileDescriptorUtil;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.content.Context;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.ohttp.algorithms.UnsupportedHpkeAlgorithmException;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ProtectedAudienceInput;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FluentFuture;
import com.google.protobuf.ByteString;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class GetAdSelectionDataRunnerTest {
    private static final int CALLER_UID = Process.myUid();
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final ExecutorService BLOCKING_EXECUTOR =
            AdServicesExecutors.getBlockingExecutor();
    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER_1;
    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private static final byte[] CIPHER_TEXT_BYTES =
            "encrypted-cipher-for-auction-result".getBytes(StandardCharsets.UTF_8);

    private static final Instant AD_SELECTION_INITIALIZATION_INSTANT =
            Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private Flags mFlags;
    private Context mContext;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    private CustomAudienceDao mCustomAudienceDao;
    private EncodedPayloadDao mEncodedPayloadDao;
    @Spy private AdSelectionEntryDao mAdSelectionEntryDaoSpy;
    @Mock private ObliviousHttpEncryptor mObliviousHttpEncryptorMock;
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilterMock;
    @Spy private AdFilterer mAdFiltererSpy = new AdFiltererNoOpImpl();
    @Mock private Clock mClockMock;

    @Mock private AuctionServerDebugReporting mAuctionServerDebugReporting;
    private GetAdSelectionDataRunner mGetAdSelectionDataRunner;
    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() throws InvalidKeySpecException, UnsupportedHpkeAlgorithmException {
        mFlags = new GetAdSelectionDataRunnerTestFlags();
        mContext = ApplicationProvider.getApplicationContext();
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();

        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true))
                        .build()
                        .customAudienceDao();
        mEncodedPayloadDao =
                Room.inMemoryDatabaseBuilder(mContext, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncodedPayloadDao();
        mAdSelectionEntryDaoSpy =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(PackageManagerCompatUtils.class)
                        .spyStatic(AssetFileDescriptorUtil.class)
                        .initMocks(this)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        MockitoAnnotations.initMocks(this); // init @Mock mocks

        doNothing()
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        SELLER,
                        CALLER_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());
        when(mClockMock.instant()).thenReturn(AD_SELECTION_INITIALIZATION_INSTANT);
        when(mAuctionServerDebugReporting.isEnabled()).thenReturn(false);
        mGetAdSelectionDataRunner = initRunner(mFlags);
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testRunner_getAdSelectionData_returnsSuccess() throws InterruptedException {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong());

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue(
                "Call failed with response " + callback.mFledgeErrorResponse, callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertArrayEquals(
                CIPHER_TEXT_BYTES, callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertTrue(callback.mGetAdSelectionDataResponse.getAdSelectionData().length > 0);
        verify(mObliviousHttpEncryptorMock, times(1)).encryptBytes(any(), anyLong(), anyLong());
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionInitialization(
                        callback.mGetAdSelectionDataResponse.getAdSelectionId(),
                        AdSelectionInitialization.builder()
                                .setSeller(SELLER)
                                .setCallerPackageName(CALLER_PACKAGE_NAME)
                                .setCreationInstant(AD_SELECTION_INITIALIZATION_INSTANT)
                                .build());
        verify(mAdFiltererSpy).filterCustomAudiences(any());
    }

    @Test
    public void testRunner_getAdSelectionData_returnsSuccessWithExcessiveSizeFormatterVersion()
            throws InterruptedException, IOException {
        mFlags = new GetAdSelectionDataRunnerTestFlagsWithExcessiveSizeFormatter();
        mGetAdSelectionDataRunner = initRunner(mFlags);

        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong());

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue(
                "Call failed with response " + callback.mFledgeErrorResponse, callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        // Make sure traditional byte array is null
        Assert.assertNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        // Make sure AssetFile descriptor is not null
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAssetFileDescriptor());

        // Read result into buffer
        byte[] result =
                AssetFileDescriptorUtil.readAssetFileDescriptorIntoBuffer(
                        callback.mGetAdSelectionDataResponse.getAssetFileDescriptor());

        // Assert result is expected
        Assert.assertArrayEquals(CIPHER_TEXT_BYTES, result);
        verify(mObliviousHttpEncryptorMock, times(1)).encryptBytes(any(), anyLong(), anyLong());
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionInitialization(
                        callback.mGetAdSelectionDataResponse.getAdSelectionId(),
                        AdSelectionInitialization.builder()
                                .setSeller(SELLER)
                                .setCallerPackageName(CALLER_PACKAGE_NAME)
                                .setCreationInstant(AD_SELECTION_INITIALIZATION_INSTANT)
                                .build());
        verify(mAdFiltererSpy).filterCustomAudiences(any());
    }

    @Test
    public void testRunner_getAdSelectionData_returnsInternalErrorWhenEncounteringIOException()
            throws InterruptedException, IOException {
        mFlags = new GetAdSelectionDataRunnerTestFlagsWithExcessiveSizeFormatter();
        mGetAdSelectionDataRunner = initRunner(mFlags);
        doThrow(new IOException())
                .when(() -> AssetFileDescriptorUtil.setupAssetFileDescriptorResponse(any(), any()));

        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong());

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertFalse("Call should not have succeeded", callback.mIsSuccess);
        Assert.assertEquals(STATUS_INTERNAL_ERROR, callback.mFledgeErrorResponse.getStatusCode());
    }

    @Test
    public void testRunner_revokedUserConsent_returnsRandomResult() throws InterruptedException {
        doReturn(mFlags).when(FlagsFactory::getFlags);
        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        eq(SELLER),
                        eq(CALLER_PACKAGE_NAME),
                        eq(false),
                        eq(true),
                        eq(CALLER_UID),
                        eq(AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(Throttler.ApiKey.FLEDGE_API_GET_AD_SELECTION_DATA),
                        eq(DevContext.createForDevOptionsDisabled()));

        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertEquals(
                GetAdSelectionDataRunner.REVOKED_CONSENT_RANDOM_DATA_SIZE,
                callback.mGetAdSelectionDataResponse.getAdSelectionData().length);
        verifyZeroInteractions(mObliviousHttpEncryptorMock);
        verifyZeroInteractions(mAdSelectionEntryDaoSpy);
        verifyZeroInteractions(mAdFiltererSpy);
    }

    @Test
    public void testRunner_revokedUserConsent_returnsRandomResultWithExcessiveSizeFormatterVersion()
            throws InterruptedException, IOException {
        mFlags = new GetAdSelectionDataRunnerTestFlagsWithExcessiveSizeFormatter();
        mGetAdSelectionDataRunner = initRunner(mFlags);

        doReturn(mFlags).when(FlagsFactory::getFlags);
        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        eq(SELLER),
                        eq(CALLER_PACKAGE_NAME),
                        eq(false),
                        eq(true),
                        eq(CALLER_UID),
                        eq(AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(Throttler.ApiKey.FLEDGE_API_GET_AD_SELECTION_DATA),
                        eq(DevContext.createForDevOptionsDisabled()));

        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertEquals(
                GetAdSelectionDataRunner.REVOKED_CONSENT_RANDOM_DATA_SIZE,
                callback.mGetAdSelectionDataResponse.getAdSelectionData().length);
        verifyZeroInteractions(mObliviousHttpEncryptorMock);
        verifyZeroInteractions(mAdSelectionEntryDaoSpy);
        verifyZeroInteractions(mAdFiltererSpy);
    }

    @Test
    public void test_composeProtectedAudienceInput_generatesProto() {
        byte[] buyer1data = new byte[] {2, 3};
        byte[] buyer2data = new byte[] {1};
        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerInputs =
                ImmutableMap.of(
                        BUYER_1,
                        AuctionServerDataCompressor.CompressedData.create(buyer1data),
                        BUYER_2,
                        AuctionServerDataCompressor.CompressedData.create(buyer2data));

        long adSelectionId = 234L;

        ProtectedAudienceInput result =
                mGetAdSelectionDataRunner.composeProtectedAudienceInputBytes(
                        buyerInputs, CALLER_PACKAGE_NAME, adSelectionId, false);

        Map<String, ByteString> expectedBuyerInput =
                ImmutableMap.of(
                        BUYER_1.toString(),
                        ByteString.copyFrom(buyer1data),
                        BUYER_2.toString(),
                        ByteString.copyFrom(buyer2data));
        Assert.assertEquals(result.getBuyerInput(), expectedBuyerInput);
        Assert.assertEquals(result.getPublisherName(), CALLER_PACKAGE_NAME);
        Assert.assertFalse(result.getEnableDebugReporting());
        Assert.assertEquals(result.getGenerationId(), String.valueOf(adSelectionId));
    }

    @Test
    public void test_composeProtectedAudienceInput_DebugReportingEnabled() {
        boolean isDebugReportingEnabled = true;
        long adSelectionId = 234L;
        doReturn(mFlags).when(FlagsFactory::getFlags);
        GetAdSelectionDataRunner getAdSelectionDataRunner = initRunner(mFlags);

        ProtectedAudienceInput result =
                getAdSelectionDataRunner.composeProtectedAudienceInputBytes(
                        createTestBuyerInputs(),
                        CALLER_PACKAGE_NAME,
                        adSelectionId,
                        isDebugReportingEnabled);

        Assert.assertEquals(true, result.getEnableDebugReporting());
    }

    @Test
    public void testRunner_getAdSelectionData_timeoutFailure() throws InterruptedException {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        Flags shortTimeoutFlags =
                new GetAdSelectionDataRunnerTestFlags() {
                    @Override
                    public long getFledgeAuctionServerOverallTimeoutMs() {
                        return 200L;
                    }
                };

        when(mObliviousHttpEncryptorMock.encryptBytes(any(), anyLong(), anyLong()))
                .thenAnswer(
                        new AnswersWithDelay(
                                2 * shortTimeoutFlags.getFledgeAuctionServerOverallTimeoutMs(),
                                new Returns(
                                        FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))));

        GetAdSelectionDataRunner getAdSelectionDataRunner =
                new GetAdSelectionDataRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mAdSelectionServiceFilterMock,
                        mAdFiltererSpy,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        BLOCKING_EXECUTOR,
                        mScheduledExecutor,
                        shortTimeoutFlags,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mAuctionServerDebugReporting);

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(getAdSelectionDataRunner, inputParams);

        Assert.assertFalse(callback.mIsSuccess);
        assertNotNull(callback.mFledgeErrorResponse);
        assertEquals(STATUS_TIMEOUT, callback.mFledgeErrorResponse.getStatusCode());
    }

    private Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>
            createTestBuyerInputs() {
        byte[] buyer1data = new byte[] {2, 3};
        byte[] buyer2data = new byte[] {1};
        return ImmutableMap.of(
                BUYER_1,
                AuctionServerDataCompressor.CompressedData.create(buyer1data),
                BUYER_2,
                AuctionServerDataCompressor.CompressedData.create(buyer2data));
    }

    private GetAdSelectionDataRunner initRunner(Flags flags) {
        return new GetAdSelectionDataRunner(
                mObliviousHttpEncryptorMock,
                mAdSelectionEntryDaoSpy,
                mCustomAudienceDao,
                mEncodedPayloadDao,
                mAdSelectionServiceFilterMock,
                mAdFiltererSpy,
                mBackgroundExecutorService,
                mLightweightExecutorService,
                BLOCKING_EXECUTOR,
                mScheduledExecutor,
                flags,
                CALLER_UID,
                DevContext.createForDevOptionsDisabled(),
                mClockMock,
                mAuctionServerDebugReporting);
    }

    private void createAndPersistDBCustomAudiencesWithAdRenderId() {
        Map<String, AdTechIdentifier> nameAndBuyers =
                Map.of(
                        "Shoes CA of Buyer 1", BUYER_1,
                        "Shirts CA of Buyer 1", BUYER_1,
                        "Shoes CA Of Buyer 2", BUYER_2);

        for (Map.Entry<String, AdTechIdentifier> entry : nameAndBuyers.entrySet()) {
            AdTechIdentifier buyer = entry.getValue();
            String name = entry.getKey();
            DBCustomAudience thisCustomAudience =
                    DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(buyer, name)
                            .build();
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    thisCustomAudience, Uri.EMPTY, /*debuggable=*/ false);
        }
    }

    private GetAdSelectionDataTestCallback invokeGetAdSelectionData(
            GetAdSelectionDataRunner runner, GetAdSelectionDataInput inputParams)
            throws InterruptedException {

        CountDownLatch countdownLatch = new CountDownLatch(1);
        GetAdSelectionDataTestCallback callback =
                new GetAdSelectionDataTestCallback(countdownLatch);

        runner.run(inputParams, callback);
        callback.mCountDownLatch.await();
        return callback;
    }

    public static class GetAdSelectionDataRunnerTestFlags implements Flags {
        @Override
        public long getFledgeAuctionServerOverallTimeoutMs() {
            return FLEDGE_AUCTION_SERVER_OVERALL_TIMEOUT_MS;
        }

        @Override
        public long getFledgeCustomAudienceActiveTimeWindowInMs() {
            return FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS;
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return true;
        }
    }

    static class GetAdSelectionDataRunnerTestFlagsWithExcessiveSizeFormatter
            extends GetAdSelectionDataRunnerTestFlags {
        @Override
        public int getFledgeAuctionServerPayloadFormatVersion() {
            return AuctionServerPayloadFormatterExcessiveMaxSize.VERSION;
        }
    }

    static class GetAdSelectionDataTestCallback extends GetAdSelectionDataCallback.Stub {
        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        GetAdSelectionDataResponse mGetAdSelectionDataResponse;
        FledgeErrorResponse mFledgeErrorResponse;

        GetAdSelectionDataTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mGetAdSelectionDataResponse = null;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess(GetAdSelectionDataResponse getAdSelectionDataResponse)
                throws RemoteException {
            mIsSuccess = true;
            mGetAdSelectionDataResponse = getAdSelectionDataResponse;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }
}
