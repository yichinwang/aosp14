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

import static android.adservices.adselection.DataHandlersFixture.getAdSelectionInitialization;
import static android.adservices.adselection.DataHandlersFixture.getAdSelectionResultBidAndUri;
import static android.adservices.adselection.DataHandlersFixture.getReportingData;
import static android.adservices.adselection.DataHandlersFixture.getWinningCustomAudience;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;

import static com.android.adservices.service.Flags.FLEDGE_AUCTION_SERVER_OVERALL_TIMEOUT_MS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.PersistAdSelectionResultCallback;
import android.adservices.adselection.PersistAdSelectionResultInput;
import android.adservices.adselection.PersistAdSelectionResultResponse;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.common.AdTechIdentifier;
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
import com.android.adservices.data.adselection.DBRegisteredAdInteraction;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;
import com.android.adservices.data.adselection.datahandlers.ReportingData;
import com.android.adservices.data.adselection.datahandlers.WinningCustomAudience;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.ohttp.algorithms.UnsupportedHpkeAlgorithmException;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls.ReportingUrls;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class PersistAdSelectionResultRunnerTest {
    private static final int CALLER_UID = Process.myUid();
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final String DIFFERENT_CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME_2;
    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER;
    private static final AdTechIdentifier DIFFERENT_SELLER = AdSelectionConfigFixture.SELLER_1;
    private static final Uri AD_RENDER_URI_1 = Uri.parse("test2.com/render_uri");
    private static final Uri AD_RENDER_URI_2 = Uri.parse("test3.com/render_uri");
    private static final AdTechIdentifier WINNER_BUYER =
            AdTechIdentifier.fromString("winner-buyer.com");
    private static final AdTechIdentifier DIFFERENT_BUYER =
            AdTechIdentifier.fromString("different-buyer.com");
    private static final Uri WINNER_AD_RENDER_URI =
            CommonFixture.getUri(WINNER_BUYER, "/render_uri");
    private static final String BUYER_REPORTING_URI =
            CommonFixture.getUri(WINNER_BUYER, "/reporting").toString();
    private static final String BUYER_REPORTING_URI_DIFFERENT_BUYER =
            CommonFixture.getUri(DIFFERENT_BUYER, "/reporting").toString();
    private static final String SELLER_REPORTING_URI =
            CommonFixture.getUri(SELLER, "/reporting").toString();
    private static final String SELLER_REPORTING_URI_DIFFERENT_SELLER =
            CommonFixture.getUri(DIFFERENT_SELLER, "/reporting").toString();
    private static final String BUYER_INTERACTION_KEY = "buyer-interaction-key";
    private static final String BUYER_INTERACTION_URI =
            CommonFixture.getUri(WINNER_BUYER, "/interaction").toString();
    private static final String BUYER_INTERACTION_URI_DIFFERENT_BUYER =
            CommonFixture.getUri(DIFFERENT_BUYER, "/interaction").toString();
    private static final String SELLER_INTERACTION_KEY = "seller-interaction-key";
    private static final String SELLER_INTERACTION_URI =
            CommonFixture.getUri(SELLER, "/interaction").toString();
    private static final String SELLER_INTERACTION_URI_DIFFERENT_SELLER =
            CommonFixture.getUri(DIFFERENT_SELLER, "/interaction").toString();
    private static final String BUYER_INTERACTION_URI_EXCEEDS_MAX =
            CommonFixture.getUri(WINNER_BUYER, "/interaction_uri_exceeds_max").toString();
    private static final String SELLER_INTERACTION_KEY_EXCEEDS_MAX =
            "seller-interaction-key-exceeds-max";
    private static final WinReportingUrls WIN_REPORTING_URLS =
            WinReportingUrls.newBuilder()
                    .setBuyerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(BUYER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            BUYER_INTERACTION_KEY, BUYER_INTERACTION_URI)
                                    .build())
                    .setTopLevelSellerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(SELLER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            SELLER_INTERACTION_KEY, SELLER_INTERACTION_URI)
                                    .build())
                    .build();
    private static final WinReportingUrls WIN_REPORTING_URLS_WITH_DIFFERENT_SELLER_REPORTING_URIS =
            WinReportingUrls.newBuilder()
                    .setBuyerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(BUYER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            BUYER_INTERACTION_KEY, BUYER_INTERACTION_URI)
                                    .build())
                    .setTopLevelSellerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(SELLER_REPORTING_URI_DIFFERENT_SELLER)
                                    .putInteractionReportingUrls(
                                            SELLER_INTERACTION_KEY,
                                            SELLER_INTERACTION_URI_DIFFERENT_SELLER)
                                    .build())
                    .build();
    private static final WinReportingUrls WIN_REPORTING_URLS_WITH_DIFFERENT_BUYER_REPORTING_URIS =
            WinReportingUrls.newBuilder()
                    .setBuyerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(BUYER_REPORTING_URI_DIFFERENT_BUYER)
                                    .putInteractionReportingUrls(
                                            BUYER_INTERACTION_KEY,
                                            BUYER_INTERACTION_URI_DIFFERENT_BUYER)
                                    .build())
                    .setTopLevelSellerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(SELLER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            SELLER_INTERACTION_KEY, SELLER_INTERACTION_URI)
                                    .build())
                    .build();
    private static final WinReportingUrls WIN_REPORTING_URLS_WITH_INTERACTION_DATA_EXCEEDS_MAX =
            WinReportingUrls.newBuilder()
                    .setBuyerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(BUYER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            BUYER_INTERACTION_KEY,
                                            BUYER_INTERACTION_URI_EXCEEDS_MAX)
                                    .build())
                    .setTopLevelSellerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(SELLER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            SELLER_INTERACTION_KEY_EXCEEDS_MAX,
                                            SELLER_INTERACTION_URI_DIFFERENT_SELLER)
                                    .build())
                    .build();
    private static final String WINNER_CUSTOM_AUDIENCE_NAME = "test-name-1";
    private static final String WINNER_CUSTOM_AUDIENCE_OWNER = "winner-owner";
    private static final String CUSTOM_AUDIENCE_OWNER_1 = "owner-1";
    private static final String CUSTOM_AUDIENCE_OWNER_2 = "owner-2";
    private static final double BID = 5;
    private static final double SCORE = 5;
    private static final AuctionResult.Builder AUCTION_RESULT =
            AuctionResult.newBuilder()
                    .setAdRenderUrl(WINNER_AD_RENDER_URI.toString())
                    .setCustomAudienceName(WINNER_CUSTOM_AUDIENCE_NAME)
                    .setCustomAudienceOwner(WINNER_CUSTOM_AUDIENCE_OWNER)
                    .setBuyer(WINNER_BUYER.toString())
                    .setBid((float) BID)
                    .setScore((float) SCORE)
                    .setIsChaff(false)
                    .setWinReportingUrls(WIN_REPORTING_URLS);
    private static final AuctionResult.Builder
            AUCTION_RESULT_WITH_DIFFERENT_SELLER_IN_REPORTING_URIS =
                    AuctionResult.newBuilder()
                            .setAdRenderUrl(WINNER_AD_RENDER_URI.toString())
                            .setCustomAudienceName(WINNER_CUSTOM_AUDIENCE_NAME)
                            .setCustomAudienceOwner(WINNER_CUSTOM_AUDIENCE_OWNER)
                            .setBuyer(WINNER_BUYER.toString())
                            .setBid((float) BID)
                            .setScore((float) SCORE)
                            .setIsChaff(false)
                            .setWinReportingUrls(
                                    WIN_REPORTING_URLS_WITH_DIFFERENT_SELLER_REPORTING_URIS);
    private static final AuctionResult.Builder
            AUCTION_RESULT_WITH_DIFFERENT_BUYER_IN_REPORTING_URIS =
                    AuctionResult.newBuilder()
                            .setAdRenderUrl(WINNER_AD_RENDER_URI.toString())
                            .setCustomAudienceName(WINNER_CUSTOM_AUDIENCE_NAME)
                            .setCustomAudienceOwner(WINNER_CUSTOM_AUDIENCE_OWNER)
                            .setBuyer(WINNER_BUYER.toString())
                            .setBid((float) BID)
                            .setScore((float) SCORE)
                            .setIsChaff(false)
                            .setWinReportingUrls(
                                    WIN_REPORTING_URLS_WITH_DIFFERENT_BUYER_REPORTING_URIS);
    private static final AuctionResult.Builder
            AUCTION_RESULT_WITH_INTERACTION_REPORTING_DATA_EXCEEDS_MAX =
                    AuctionResult.newBuilder()
                            .setAdRenderUrl(WINNER_AD_RENDER_URI.toString())
                            .setCustomAudienceName(WINNER_CUSTOM_AUDIENCE_NAME)
                            .setCustomAudienceOwner(WINNER_CUSTOM_AUDIENCE_OWNER)
                            .setBuyer(WINNER_BUYER.toString())
                            .setBid((float) BID)
                            .setScore((float) SCORE)
                            .setIsChaff(false)
                            .setWinReportingUrls(
                                    WIN_REPORTING_URLS_WITH_INTERACTION_DATA_EXCEEDS_MAX);
    private static final AuctionResult.Builder AUCTION_RESULT_WITHOUT_OWNER =
            AuctionResult.newBuilder()
                    .setAdRenderUrl(WINNER_AD_RENDER_URI.toString())
                    .setCustomAudienceName(WINNER_CUSTOM_AUDIENCE_NAME)
                    .setBuyer(WINNER_BUYER.toString())
                    .setBid((float) BID)
                    .setScore((float) SCORE)
                    .setIsChaff(false)
                    .setWinReportingUrls(WIN_REPORTING_URLS);
    private static final AuctionResult.Builder AUCTION_RESULT_CHAFF =
            AuctionResult.newBuilder().setIsChaff(true);
    private static final AuctionResult.Builder AUCTION_RESULT_WITH_ERROR =
            AuctionResult.newBuilder()
                    .setError(
                            AuctionResult.Error.newBuilder()
                                    .setCode(-1)
                                    .setMessage("AuctionServerError: Bad things happened!")
                                    .build());
    private static final Set<Integer> AD_COUNTER_KEYS = Set.of(1, 2, 3);
    private static final DBAdData WINNING_AD =
            new DBAdData.Builder()
                    .setRenderUri(WINNER_AD_RENDER_URI)
                    .setAdCounterKeys(AD_COUNTER_KEYS)
                    .setMetadata("")
                    .build();
    private static final DBCustomAudience WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD =
            DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                            WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME, WINNER_CUSTOM_AUDIENCE_OWNER)
                    .setAds(ImmutableList.of(WINNING_AD))
                    .build();
    private static final DBCustomAudience CUSTOM_AUDIENCE_WITH_WIN_AD_1 =
            DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                            WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME, CUSTOM_AUDIENCE_OWNER_1)
                    .setAds(
                            ImmutableList.of(
                                    new DBAdData.Builder()
                                            .setRenderUri(AD_RENDER_URI_1)
                                            .setMetadata("")
                                            .build()))
                    .build();
    private static final DBCustomAudience CUSTOM_AUDIENCE_WITH_WIN_AD_2 =
            DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                            WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME, CUSTOM_AUDIENCE_OWNER_2)
                    .setAds(
                            ImmutableList.of(
                                    new DBAdData.Builder()
                                            .setRenderUri(AD_RENDER_URI_2)
                                            .setMetadata("")
                                            .build()))
                    .build();
    private static final List<DBCustomAudience> CUSTOM_AUDIENCE_LIST_INCLUDING_WINNER =
            List.of(
                    WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD,
                    CUSTOM_AUDIENCE_WITH_WIN_AD_1,
                    CUSTOM_AUDIENCE_WITH_WIN_AD_2);
    private static final byte[] CIPHER_TEXT_BYTES =
            "encrypted-cipher-for-auction-result".getBytes();
    private static final long AD_SELECTION_ID = 12345L;
    private static final AdSelectionInitialization INITIALIZATION_DATA =
            getAdSelectionInitialization(SELLER, CALLER_PACKAGE_NAME);
    private static final AdSelectionInitialization INITIALIZATION_DATA_WITH_DIFFERENT_SELLER =
            getAdSelectionInitialization(DIFFERENT_SELLER, CALLER_PACKAGE_NAME);
    private static final AdSelectionInitialization
            INITIALIZATION_DATA_WITH_DIFFERENT_CALLER_PACKAGE =
                    getAdSelectionInitialization(SELLER, DIFFERENT_CALLER_PACKAGE_NAME);
    private static final AdSelectionResultBidAndUri BID_AND_URI =
            getAdSelectionResultBidAndUri(AD_SELECTION_ID, BID, WINNER_AD_RENDER_URI);
    private static final WinningCustomAudience WINNER_CUSTOM_AUDIENCE_WITH_AD_COUNTER_KEYS =
            getWinningCustomAudience(
                    WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_CUSTOM_AUDIENCE_NAME, AD_COUNTER_KEYS);
    private static final WinningCustomAudience WINNER_CUSTOM_AUDIENCE_WITH_EMPTY_AD_COUNTER_KEYS =
            getWinningCustomAudience(
                    WINNER_CUSTOM_AUDIENCE_OWNER,
                    WINNER_CUSTOM_AUDIENCE_NAME,
                    Collections.emptySet());
    private static final WinningCustomAudience EMPTY_CUSTOM_AUDIENCE_FOR_APP_INSTALL =
            getWinningCustomAudience("", "", Collections.emptySet());
    private static final ReportingData REPORTING_DATA =
            getReportingData(Uri.parse(BUYER_REPORTING_URI), Uri.parse(SELLER_REPORTING_URI));
    private static final ReportingData REPORTING_DATA_WITH_EMPTY_SELLER =
            getReportingData(Uri.parse(BUYER_REPORTING_URI), Uri.EMPTY);
    private static final ReportingData REPORTING_DATA_WITH_EMPTY_BUYER =
            getReportingData(Uri.EMPTY, Uri.parse(SELLER_REPORTING_URI));
    private Context mContext;
    private Flags mFlags;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    @Mock private ObliviousHttpEncryptor mObliviousHttpEncryptorMock;
    private AdSelectionEntryDao mAdSelectionEntryDaoSpy;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    private AuctionServerPayloadFormatter mPayloadFormatter;
    private AuctionServerDataCompressor mDataCompressor;
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilterMock;

    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    private PersistAdSelectionResultRunner mPersistAdSelectionResultRunner;
    private long mOverallTimeout;
    private boolean mForceContinueOnAbsentOwner;
    private PersistAdSelectionResultRunner.ReportingRegistrationLimits mReportingLimits;
    private final AdCounterHistogramUpdater mAdCounterHistogramUpdaterSpy =
            spy(new AdCounterHistogramUpdaterNoOpImpl());
    private MockitoSession mStaticMockSession = null;

    private AuctionResultValidator mAuctionResultValidator;

    @Before
    public void setup() throws InvalidKeySpecException, UnsupportedHpkeAlgorithmException {
        mFlags = new PersistAdSelectionResultRunnerTestFlags();
        mContext = ApplicationProvider.getApplicationContext();
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();
        mAdSelectionEntryDaoSpy =
                spy(
                        Room.inMemoryDatabaseBuilder(mContext, AdSelectionDatabase.class)
                                .build()
                                .adSelectionEntryDao());
        mPayloadFormatter =
                AuctionServerPayloadFormatterFactory.createPayloadFormatter(
                        AuctionServerPayloadFormatterV0.VERSION,
                        mFlags.getFledgeAuctionServerPayloadBucketSizes());
        mDataCompressor = new AuctionServerDataCompressorGzip();

        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(PackageManagerCompatUtils.class)
                        .initMocks(this)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        MockitoAnnotations.initMocks(this); // init @Mock mocks
        mOverallTimeout = FLEDGE_AUCTION_SERVER_OVERALL_TIMEOUT_MS;
        mForceContinueOnAbsentOwner = false;
        mReportingLimits =
                PersistAdSelectionResultRunner.ReportingRegistrationLimits.builder()
                        .setMaxRegisteredAdBeaconsTotalCount(
                                mFlags.getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount())
                        .setMaxInteractionKeySize(
                                mFlags
                                        .getFledgeReportImpressionRegisteredAdBeaconsMaxInteractionKeySizeB())
                        .setMaxInteractionReportingUriSize(
                                mFlags.getFledgeReportImpressionMaxInteractionReportingUriSizeB())
                        .setMaxRegisteredAdBeaconsPerAdTechCount(
                                mFlags
                                        .getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount())
                        .build();
        mAuctionResultValidator =
                new AuctionResultValidator(
                        mFledgeAuthorizationFilterMock, false /* disableFledgeEnrollmentCheck */);
        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator);
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testRunner_persistRemarketingResult_success() throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDaoSpy.persistAdSelectionInitialization(
                AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionResultForCustomAudience(
                        AD_SELECTION_ID,
                        BID_AND_URI,
                        WINNER_BUYER,
                        WINNER_CUSTOM_AUDIENCE_WITH_AD_COUNTER_KEYS);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistReportingData(AD_SELECTION_ID, REPORTING_DATA);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .safelyInsertRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        List.of(
                                getDBRegisteredAdInteraction(
                                        SELLER_INTERACTION_KEY,
                                        SELLER_INTERACTION_URI,
                                        ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER)),
                        mReportingLimits.getMaxRegisteredAdBeaconsTotalCount(),
                        mReportingLimits.getMaxRegisteredAdBeaconsPerAdTechCount(),
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .safelyInsertRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        List.of(
                                getDBRegisteredAdInteraction(
                                        BUYER_INTERACTION_KEY,
                                        BUYER_INTERACTION_URI,
                                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER)),
                        mReportingLimits.getMaxRegisteredAdBeaconsTotalCount(),
                        mReportingLimits.getMaxRegisteredAdBeaconsPerAdTechCount(),
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER);
        verify(mAdCounterHistogramUpdaterSpy)
                .updateWinHistogram(
                        WINNER_BUYER,
                        mAdSelectionEntryDaoSpy.getAdSelectionInitializationForId(AD_SELECTION_ID),
                        mAdSelectionEntryDaoSpy.getWinningCustomAudienceDataForId(AD_SELECTION_ID));
    }

    @Test
    public void testRunner_persistAppInstallResult_success() throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(prepareDecryptedAuctionResultForAppInstallAd(AUCTION_RESULT))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDaoSpy.persistAdSelectionInitialization(
                AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionResultForCustomAudience(
                        AD_SELECTION_ID,
                        BID_AND_URI,
                        WINNER_BUYER,
                        EMPTY_CUSTOM_AUDIENCE_FOR_APP_INSTALL);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistReportingData(AD_SELECTION_ID, REPORTING_DATA);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .safelyInsertRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        List.of(
                                getDBRegisteredAdInteraction(
                                        SELLER_INTERACTION_KEY,
                                        SELLER_INTERACTION_URI,
                                        ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER)),
                        mReportingLimits.getMaxRegisteredAdBeaconsTotalCount(),
                        mReportingLimits.getMaxRegisteredAdBeaconsPerAdTechCount(),
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .safelyInsertRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        List.of(
                                getDBRegisteredAdInteraction(
                                        BUYER_INTERACTION_KEY,
                                        BUYER_INTERACTION_URI,
                                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER)),
                        mReportingLimits.getMaxRegisteredAdBeaconsTotalCount(),
                        mReportingLimits.getMaxRegisteredAdBeaconsPerAdTechCount(),
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER);
        verify(mAdCounterHistogramUpdaterSpy)
                .updateWinHistogram(
                        WINNER_BUYER,
                        mAdSelectionEntryDaoSpy.getAdSelectionInitializationForId(AD_SELECTION_ID),
                        mAdSelectionEntryDaoSpy.getWinningCustomAudienceDataForId(AD_SELECTION_ID));
    }

    @Test
    public void testRunner_persistRemarketingResult_withInvalidSellerReportingUriSuccess()
            throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(
                        prepareDecryptedAuctionResultForRemarketingAd(
                                AUCTION_RESULT_WITH_DIFFERENT_SELLER_IN_REPORTING_URIS))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDaoSpy.persistAdSelectionInitialization(
                AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionResultForCustomAudience(
                        AD_SELECTION_ID,
                        BID_AND_URI,
                        WINNER_BUYER,
                        WINNER_CUSTOM_AUDIENCE_WITH_AD_COUNTER_KEYS);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistReportingData(AD_SELECTION_ID, REPORTING_DATA_WITH_EMPTY_SELLER);
        verify(mAdSelectionEntryDaoSpy, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(),
                        any(),
                        anyLong(),
                        anyLong(),
                        eq(ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER));
        verify(mAdSelectionEntryDaoSpy, times(1))
                .safelyInsertRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        List.of(
                                getDBRegisteredAdInteraction(
                                        BUYER_INTERACTION_KEY,
                                        BUYER_INTERACTION_URI,
                                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER)),
                        mReportingLimits.getMaxRegisteredAdBeaconsTotalCount(),
                        mReportingLimits.getMaxRegisteredAdBeaconsPerAdTechCount(),
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER);
    }

    @Test
    public void testRunner_persistAppInstallResult_withInvalidSellerReportingUriSuccess()
            throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(
                        prepareDecryptedAuctionResultForAppInstallAd(
                                AUCTION_RESULT_WITH_DIFFERENT_SELLER_IN_REPORTING_URIS))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDaoSpy.persistAdSelectionInitialization(
                AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionResultForCustomAudience(
                        AD_SELECTION_ID,
                        BID_AND_URI,
                        WINNER_BUYER,
                        EMPTY_CUSTOM_AUDIENCE_FOR_APP_INSTALL);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistReportingData(AD_SELECTION_ID, REPORTING_DATA_WITH_EMPTY_SELLER);
        verify(mAdSelectionEntryDaoSpy, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(),
                        any(),
                        anyLong(),
                        anyLong(),
                        eq(ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER));
        verify(mAdSelectionEntryDaoSpy, times(1))
                .safelyInsertRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        List.of(
                                getDBRegisteredAdInteraction(
                                        BUYER_INTERACTION_KEY,
                                        BUYER_INTERACTION_URI,
                                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER)),
                        mReportingLimits.getMaxRegisteredAdBeaconsTotalCount(),
                        mReportingLimits.getMaxRegisteredAdBeaconsPerAdTechCount(),
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER);
    }

    @Test
    public void testRunner_persistRemarketingResult_withInvalidBuyerReportingUriSuccess()
            throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(
                        prepareDecryptedAuctionResultForRemarketingAd(
                                AUCTION_RESULT_WITH_DIFFERENT_BUYER_IN_REPORTING_URIS))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDaoSpy.persistAdSelectionInitialization(
                AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionResultForCustomAudience(
                        AD_SELECTION_ID,
                        BID_AND_URI,
                        WINNER_BUYER,
                        WINNER_CUSTOM_AUDIENCE_WITH_AD_COUNTER_KEYS);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistReportingData(AD_SELECTION_ID, REPORTING_DATA_WITH_EMPTY_BUYER);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .safelyInsertRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        List.of(
                                getDBRegisteredAdInteraction(
                                        SELLER_INTERACTION_KEY,
                                        SELLER_INTERACTION_URI,
                                        ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER)),
                        mReportingLimits.getMaxRegisteredAdBeaconsTotalCount(),
                        mReportingLimits.getMaxRegisteredAdBeaconsPerAdTechCount(),
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER);
        verify(mAdSelectionEntryDaoSpy, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(),
                        any(),
                        anyLong(),
                        anyLong(),
                        eq(ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER));
    }

    // TODO(b/291680065): Remove the test when owner field is returned from B&A
    @Test
    public void testRunner_persistRemarketingResult_forceOnAbsentOwnerFalseSkipsValidation()
            throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT_WITHOUT_OWNER))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);

        mAdSelectionEntryDaoSpy.persistAdSelectionInitialization(
                AD_SELECTION_ID, INITIALIZATION_DATA);

        boolean forceSearchOnAbsentOwner = false;
        PersistAdSelectionResultRunner persistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        forceSearchOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(persistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        verify(mCustomAudienceDaoMock, times(0)).getCustomAudiencesForBuyerAndName(any(), any());
        verify(mCustomAudienceDaoMock, times(0)).getCustomAudienceByPrimaryKey(any(), any(), any());
    }

    // TODO(b/291680065): Remove the test when owner field is returned from B&A
    @Test
    public void testRunner_persistRemarketingResult_forceOnAbsentOwnerFalseFuzzySearch()
            throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT_WITHOUT_OWNER))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(CUSTOM_AUDIENCE_LIST_INCLUDING_WINNER)
                .when(mCustomAudienceDaoMock)
                .getCustomAudiencesForBuyerAndName(WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDaoSpy.persistAdSelectionInitialization(
                AD_SELECTION_ID, INITIALIZATION_DATA);

        boolean forceSearchOnAbsentOwner = true;
        PersistAdSelectionResultRunner persistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        forceSearchOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(persistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        verify(mCustomAudienceDaoMock, times(1))
                .getCustomAudiencesForBuyerAndName(WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);
        verify(mCustomAudienceDaoMock, times(0)).getCustomAudienceByPrimaryKey(any(), any(), any());
    }

    @Test
    public void testRunner_persistChaffResult_nothingPersisted() throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT_CHAFF))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);

        mAdSelectionEntryDaoSpy.persistAdSelectionInitialization(
                AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(Uri.EMPTY, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        verify(mAdSelectionEntryDaoSpy, times(0))
                .persistAdSelectionResultForCustomAudience(anyLong(), any(), any(), any());
        verify(mAdSelectionEntryDaoSpy, times(0)).persistReportingData(anyLong(), any());
        verify(mAdSelectionEntryDaoSpy, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(),
                        any(),
                        anyLong(),
                        anyLong(),
                        eq(ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER));
        verify(mAdSelectionEntryDaoSpy, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(),
                        any(),
                        anyLong(),
                        anyLong(),
                        eq(ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER));
    }

    @Test
    public void testRunner_persistResultWithError_throwsException() throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT_WITH_ERROR))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);

        mAdSelectionEntryDaoSpy.persistAdSelectionInitialization(
                AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertFalse(callback.mIsSuccess);
        Assert.assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
    }

    @Test
    public void testRunner_persistTimesOut_throwsException() throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        mOverallTimeout = 200;
        when(mObliviousHttpEncryptorMock.decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID))
                .thenAnswer(
                        new AnswersWithDelay(
                                2 * mOverallTimeout,
                                new Returns(
                                        prepareDecryptedAuctionResultForRemarketingAd(
                                                AUCTION_RESULT))));

        mAdSelectionEntryDaoSpy.persistAdSelectionInitialization(
                AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultRunner persistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(persistAdSelectionResultRunner, inputParams);

        Assert.assertFalse(callback.mIsSuccess);
        assertNotNull(callback.mFledgeErrorResponse);
        assertEquals(STATUS_TIMEOUT, callback.mFledgeErrorResponse.getStatusCode());
    }

    @Test
    public void testRunner_revokedUserConsent_returnsEmptyResult() throws InterruptedException {
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
                        eq(Throttler.ApiKey.FLEDGE_API_PERSIST_AD_SELECTION_RESULT),
                        eq(DevContext.createForDevOptionsDisabled()));

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mPersistAdSelectionResultResponse);
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        Assert.assertEquals(Uri.EMPTY, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        verifyZeroInteractions(mCustomAudienceDaoMock);
        verifyZeroInteractions(mObliviousHttpEncryptorMock);
        verifyZeroInteractions(mAdSelectionEntryDaoSpy);
    }

    @Test
    public void testRunner_persistResultWithNotEnrolledBuyer_throwsException() throws Exception {
        Mockito.doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterMock)
                .assertAdTechEnrolled(
                        WINNER_BUYER,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN);
        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);
        Assert.assertFalse(callback.mIsSuccess);
        assertNotNull(callback.mFledgeErrorResponse);
        assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
    }

    @Test
    public void testRunner_persistResultWithWrongSeller_throwsException() throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDaoSpy.persistAdSelectionInitialization(
                AD_SELECTION_ID, INITIALIZATION_DATA_WITH_DIFFERENT_SELLER);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertFalse(callback.mIsSuccess);
        Assert.assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        verify(mAdSelectionEntryDaoSpy, times(1))
                .getAdSelectionInitializationForId(AD_SELECTION_ID);
        verify(mAdSelectionEntryDaoSpy, times(0))
                .persistAdSelectionResultForCustomAudience(anyLong(), any(), any(), any());
        verify(mAdSelectionEntryDaoSpy, times(0)).persistReportingData(anyLong(), any());
        verify(mAdSelectionEntryDaoSpy, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(), any(), anyLong(), anyLong(), anyInt());
    }

    @Test
    public void testRunner_persistResultWithWrongCallerPackage_throwsException() throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDaoSpy.persistAdSelectionInitialization(
                AD_SELECTION_ID, INITIALIZATION_DATA_WITH_DIFFERENT_CALLER_PACKAGE);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertFalse(callback.mIsSuccess);
        Assert.assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        verify(mAdSelectionEntryDaoSpy, times(1))
                .getAdSelectionInitializationForId(AD_SELECTION_ID);
        verify(mAdSelectionEntryDaoSpy, times(0))
                .persistAdSelectionResultForCustomAudience(anyLong(), any(), any(), any());
        verify(mAdSelectionEntryDaoSpy, times(0)).persistReportingData(anyLong(), any());
        verify(mAdSelectionEntryDaoSpy, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(), any(), anyLong(), anyLong(), anyInt());
    }

    @Test
    public void testRunner_persistResultWithLongInteractionKeyAndUri_throwsException()
            throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(
                        prepareDecryptedAuctionResultForRemarketingAd(
                                AUCTION_RESULT_WITH_INTERACTION_REPORTING_DATA_EXCEEDS_MAX))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDaoSpy.persistAdSelectionInitialization(
                AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultRunner.ReportingRegistrationLimits reportingLimits =
                PersistAdSelectionResultRunner.ReportingRegistrationLimits.builder()
                        .setMaxRegisteredAdBeaconsTotalCount(
                                mFlags.getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount())
                        .setMaxInteractionKeySize(
                                SELLER_INTERACTION_KEY_EXCEEDS_MAX.getBytes(StandardCharsets.UTF_8)
                                                .length
                                        - 1)
                        .setMaxInteractionReportingUriSize(
                                BUYER_INTERACTION_URI_EXCEEDS_MAX.getBytes(StandardCharsets.UTF_8)
                                                .length
                                        - 1)
                        .setMaxRegisteredAdBeaconsPerAdTechCount(
                                mFlags
                                        .getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount())
                        .build();

        PersistAdSelectionResultRunner persistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        reportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator);
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(persistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionResultForCustomAudience(
                        AD_SELECTION_ID,
                        BID_AND_URI,
                        WINNER_BUYER,
                        WINNER_CUSTOM_AUDIENCE_WITH_AD_COUNTER_KEYS);
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistReportingData(AD_SELECTION_ID, REPORTING_DATA);
        verify(mAdSelectionEntryDaoSpy, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(), any(), anyLong(), anyLong(), anyInt());
    }

    private byte[] prepareDecryptedAuctionResultForRemarketingAd(
            AuctionResult.Builder auctionResult) {
        return prepareDecryptedAuctionResult(
                auctionResult.setAdType(AuctionResult.AdType.REMARKETING_AD).build());
    }

    private byte[] prepareDecryptedAuctionResultForAppInstallAd(
            AuctionResult.Builder auctionResult) {
        return prepareDecryptedAuctionResult(
                auctionResult
                        .setCustomAudienceName("")
                        .setCustomAudienceOwner("")
                        .setAdType(AuctionResult.AdType.APP_INSTALL_AD)
                        .build());
    }

    private byte[] prepareDecryptedAuctionResult(AuctionResult auctionResult) {
        byte[] auctionResultBytes = auctionResult.toByteArray();
        AuctionServerDataCompressor.CompressedData compressedData =
                mDataCompressor.compress(
                        AuctionServerDataCompressor.UncompressedData.create(auctionResultBytes));
        AuctionServerPayloadFormattedData formattedData =
                mPayloadFormatter.apply(
                        AuctionServerPayloadUnformattedData.create(compressedData.getData()),
                        AuctionServerDataCompressorGzip.VERSION);
        return formattedData.getData();
    }

    private PersistAdSelectionResultTestCallback invokePersistAdSelectionResult(
            PersistAdSelectionResultRunner runner, PersistAdSelectionResultInput inputParams)
            throws InterruptedException {

        CountDownLatch countdownLatch = new CountDownLatch(1);
        PersistAdSelectionResultTestCallback callback =
                new PersistAdSelectionResultTestCallback(countdownLatch);

        runner.run(inputParams, callback);
        callback.mCountDownLatch.await();
        return callback;
    }

    private DBRegisteredAdInteraction getDBRegisteredAdInteraction(
            String interactionKey, String interactionUri, int reportingDestination) {
        return DBRegisteredAdInteraction.builder()
                .setAdSelectionId(AD_SELECTION_ID)
                .setInteractionKey(interactionKey)
                .setInteractionReportingUri(Uri.parse(interactionUri))
                .setDestination(reportingDestination)
                .build();
    }

    public static class PersistAdSelectionResultRunnerTestFlags implements Flags {
        @Override
        public long getFledgeAuctionServerOverallTimeoutMs() {
            return FLEDGE_AUCTION_SERVER_OVERALL_TIMEOUT_MS;
        }

        @Override
        public long getFledgeCustomAudienceActiveTimeWindowInMs() {
            return FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS;
        }
    }

    static class PersistAdSelectionResultTestCallback
            extends PersistAdSelectionResultCallback.Stub {
        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        PersistAdSelectionResultResponse mPersistAdSelectionResultResponse;
        FledgeErrorResponse mFledgeErrorResponse;

        PersistAdSelectionResultTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mPersistAdSelectionResultResponse = null;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess(PersistAdSelectionResultResponse persistAdSelectionResultResponse)
                throws RemoteException {
            mIsSuccess = true;
            mPersistAdSelectionResultResponse = persistAdSelectionResultResponse;
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
