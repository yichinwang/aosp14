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

package android.adservices.debuggablects;

import static android.adservices.common.CommonFixture.INVALID_EMPTY_BUYER;

import static com.android.adservices.service.adselection.AdSelectionScriptEngine.NUM_BITS_STOCHASTIC_ROUNDING;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_FROM_OUTCOMES_USE_CASE;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_HIGHEST_BID_WINS;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_PREBUILT_SCHEMA;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_USE_CASE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfigFixture;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.AddAdSelectionFromOutcomesOverrideRequest;
import android.adservices.adselection.AddAdSelectionOverrideRequest;
import android.adservices.adselection.GetAdSelectionDataOutcome;
import android.adservices.adselection.GetAdSelectionDataRequest;
import android.adservices.adselection.PersistAdSelectionResultRequest;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.adselection.SetAppInstallAdvertisersRequest;
import android.adservices.adselection.UpdateAdCounterHistogramRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.adselection.TestAdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.customaudience.TestAdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdFilters;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.AppInstallFilters;
import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.KeyedFrequencyCap;
import android.adservices.customaudience.AddCustomAudienceOverrideRequest;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.adservices.utils.CtsWebViewSupportUtil;
import android.net.Uri;
import android.os.Process;
import android.util.Log;

import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.common.SupportedByConditionRule;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.service.PhFlagsFixture;
import com.android.adservices.service.adselection.AdCost;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class FledgeCtsDebuggableTest extends ForegroundDebuggableCtsTest {
    public static final String TAG = "adservices";
    // Time allowed by current test setup for APIs to respond
    private static final int API_RESPONSE_TIMEOUT_SECONDS = 120;

    // This is used to check actual API timeout conditions; note that the default overall timeout
    // for ad selection is 10 seconds
    private static final int API_RESPONSE_LONGER_TIMEOUT_SECONDS = 120;

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER_1;

    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;

    private static final String AD_URI_PREFIX = "/adverts/123/";

    private static final String SELLER_DECISION_LOGIC_URI_PATH = "/ssp/decision/logic/";
    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String SELLER_TRUSTED_SIGNAL_URI_PATH = "/kv/seller/signals/";

    private static final String SELLER_REPORTING_PATH = "/reporting/seller";
    private static final String BUYER_REPORTING_PATH = "/reporting/buyer";

    // Interaction reporting constants
    private static final String CLICK_INTERACTION = "click";
    private static final String HOVER_INTERACTION = "hover";

    private static final String SELLER_CLICK_URI_PATH = "click/seller";
    private static final String SELLER_HOVER_URI_PATH = "hover/seller";

    private static final String BUYER_CLICK_URI_PATH = "click/buyer";
    private static final String BUYER_HOVER_URI_PATH = "hover/buyer";

    private static final String SELLER_REPORTING_URI =
            String.format("https://%s%s", AdSelectionConfigFixture.SELLER, SELLER_REPORTING_PATH);

    private static final String SELLER_CLICK_URI =
            String.format("https://%s%s", AdSelectionConfigFixture.SELLER, SELLER_CLICK_URI_PATH);

    private static final String SELLER_HOVER_URI =
            String.format("https://%s%s", AdSelectionConfigFixture.SELLER, SELLER_HOVER_URI_PATH);

    private static final String DEFAULT_DECISION_LOGIC_JS =
            "function scoreAd(ad, bid, auction_config, seller_signals,"
                    + " trusted_scoring_signals, contextual_signal, user_signal,"
                    + " custom_audience_signal) { \n"
                    + "  return {'status': 0, 'score': bid };\n"
                    + "}\n"
                    + "function reportResult(ad_selection_config, render_uri, bid,"
                    + " contextual_signals) { \n"
                    + " return {'status': 0, 'results': {'signals_for_buyer':"
                    + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                    + SELLER_REPORTING_URI
                    + "' } };\n"
                    + "}";

    private static final String DECISION_LOGIC_JS_REGISTER_AD_BEACON =
            "function scoreAd(ad, bid, auction_config, seller_signals,"
                    + " trusted_scoring_signals, contextual_signal, user_signal,"
                    + " custom_audience_signal) { \n"
                    + "  return {'status': 0, 'score': bid };\n"
                    + "}\n"
                    + "function reportResult(ad_selection_config, render_uri, bid,"
                    + " contextual_signals) { \n"
                    + "const beacons = {'click': '"
                    + SELLER_CLICK_URI
                    + "', 'hover': '"
                    + SELLER_HOVER_URI
                    + "'};\n"
                    + "registerAdBeacon(beacons);"
                    + " return {'status': 0, 'results': {'signals_for_buyer':"
                    + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                    + SELLER_REPORTING_URI
                    + "' } };\n"
                    + "}";

    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");
    private static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"example\": \"example\",\n"
                            + "\t\"valid\": \"Also valid\",\n"
                            + "\t\"list\": \"list\",\n"
                            + "\t\"of\": \"of\",\n"
                            + "\t\"keys\": \"trusted bidding signal Values\"\n"
                            + "}");
    private static final AdSelectionConfig AD_SELECTION_CONFIG =
            AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                    .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                    .setDecisionLogicUri(
                            Uri.parse(
                                    String.format(
                                            "https://%s%s",
                                            AdSelectionConfigFixture.SELLER,
                                            SELLER_DECISION_LOGIC_URI_PATH)))
                    .setTrustedScoringSignalsUri(
                            Uri.parse(
                                    String.format(
                                            "https://%s%s",
                                            AdSelectionConfigFixture.SELLER,
                                            SELLER_TRUSTED_SIGNAL_URI_PATH)))
                    .build();

    private static final String BUYER_2_REPORTING_URI =
            String.format("https://%s%s", AdSelectionConfigFixture.BUYER_2, BUYER_REPORTING_PATH);

    private static final String BUYER_2_CLICK_URI =
            String.format("https://%s%s", AdSelectionConfigFixture.BUYER_2, BUYER_CLICK_URI_PATH);

    private static final String BUYER_2_HOVER_URI =
            String.format("https://%s%s", AdSelectionConfigFixture.BUYER_2, BUYER_HOVER_URI_PATH);

    private static final String BUYER_2_BIDDING_LOGIC_JS =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals,"
                    + " custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '"
                    + BUYER_2_REPORTING_URI
                    + "' } };\n"
                    + "}";

    private static final String BUYER_2_BIDDING_LOGIC_JS_AD_COST =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals, custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result, 'adCost':"
                    + " ad.metadata.adCost };\n"
                    + "}\n"
                    + "\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer,\n"
                    + "    contextual_signals, custom_audience_reporting_signals) {\n"
                    + "    let reporting_address = '"
                    + BUYER_2_REPORTING_URI
                    + "';\n"
                    + "    return {'status': 0, 'results': {'reporting_uri':\n"
                    + "                reporting_address + '?adCost=' + contextual_signals.adCost}"
                    + " };\n"
                    + "}";

    private static final String BUYER_2_BIDDING_LOGIC_JS_REGISTER_AD_BEACON =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals,"
                    + " custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + "const beacons = {'click': '"
                    + BUYER_2_CLICK_URI
                    + "', 'hover': '"
                    + BUYER_2_HOVER_URI
                    + "'};\n"
                    + "registerAdBeacon(beacons);"
                    + " return {'status': 0, 'results': {'reporting_uri': '"
                    + BUYER_2_REPORTING_URI
                    + "' } };\n"
                    + "}";

    private static final String BUYER_1_REPORTING_URI =
            String.format("https://%s%s", AdSelectionConfigFixture.BUYER_1, BUYER_REPORTING_PATH);

    private static final String BUYER_1_CLICK_URI =
            String.format("https://%s%s", AdSelectionConfigFixture.BUYER_1, BUYER_CLICK_URI_PATH);

    private static final String BUYER_1_HOVER_URI =
            String.format("https://%s%s", AdSelectionConfigFixture.BUYER_1, BUYER_HOVER_URI_PATH);

    private static final String BUYER_1_BIDDING_LOGIC_JS =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals,"
                    + " custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '"
                    + BUYER_1_REPORTING_URI
                    + "' } };\n"
                    + "}";

    private static final String BUYER_1_BIDDING_LOGIC_JS_AD_COST =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals, custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result, 'adCost':"
                    + " ad.metadata.adCost };\n"
                    + "}\n"
                    + "\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer,\n"
                    + "    contextual_signals, custom_audience_reporting_signals) {\n"
                    + "    let reporting_address = '"
                    + BUYER_1_REPORTING_URI
                    + "';\n"
                    + "    return {'status': 0, 'results': {'reporting_uri':\n"
                    + "                reporting_address + '?adCost=' + contextual_signals.adCost}"
                    + " };\n"
                    + "}";

    private static final String BUYER_1_BIDDING_LOGIC_JS_REGISTER_AD_BEACON =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals,"
                    + " custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + "const beacons = {'click': '"
                    + BUYER_1_CLICK_URI
                    + "', 'hover': '"
                    + BUYER_1_HOVER_URI
                    + "'};\n"
                    + "registerAdBeacon(beacons);"
                    + " return {'status': 0, 'results': {'reporting_uri': '"
                    + BUYER_1_REPORTING_URI
                    + "' } };\n"
                    + "}";

    private static final AdCost AD_COST_1 = new AdCost(1.2, NUM_BITS_STOCHASTIC_ROUNDING);
    private static final AdCost AD_COST_2 = new AdCost(2.2, NUM_BITS_STOCHASTIC_ROUNDING);

    private static final int BUYER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
    private static final int SELLER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;

    private static final String INTERACTION_DATA = "{\"key\":\"value\"}";

    private AdSelectionClient mAdSelectionClient;
    private TestAdSelectionClient mTestAdSelectionClient;
    private AdvertisingCustomAudienceClient mCustomAudienceClient;
    private TestAdvertisingCustomAudienceClient mTestCustomAudienceClient;
    private DevContext mDevContext;

    private boolean mHasAccessToDevOverrides;

    private String mAccessStatus;

    private final ArrayList<CustomAudience> mCustomAudiencesToCleanUp = new ArrayList<>();
    private static final AtomicInteger sFrequencyCapKeyToFilter = new AtomicInteger(0);

    // Ignore tests when device is not at least S
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    // Every test in this class requires that the JS Sandbox be available. The JS Sandbox
    // availability depends on an external component (the system webview) being higher than a
    // certain minimum version.
    @Rule(order = 2)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            CtsWebViewSupportUtil.createJSSandboxAvailableRule(sContext);

    @Rule(order = 3)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setCompatModeFlags()
                    .setPpapiAppAllowList(sContext.getPackageName());

    @Before
    public void setup() throws InterruptedException {
        if (SdkLevel.isAtLeastT()) {
            assertForegroundActivityStarted();
            flags.setFlag(
                    FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH,
                    FlagsConstants.PPAPI_AND_SYSTEM_SERVER);
        }

        mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        mTestAdSelectionClient =
                new TestAdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        mCustomAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
        mTestCustomAudienceClient =
                new TestAdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
        DevContextFilter devContextFilter = DevContextFilter.create(sContext);
        mDevContext = DevContextFilter.create(sContext).createDevContext(Process.myUid());
        boolean isDebuggable =
                devContextFilter.isDebuggable(mDevContext.getCallingAppPackageName());
        boolean isDeveloperMode = devContextFilter.isDeveloperMode();
        mHasAccessToDevOverrides = mDevContext.getDevOptionsEnabled();
        mAccessStatus =
                String.format("Debuggable: %b\n", isDebuggable)
                        + String.format("Developer options on: %b", isDeveloperMode);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);

        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(false);

        // Enable CTS to be run with versions of WebView < M105
        PhFlagsFixture.overrideEnforceIsolateMaxHeapSize(false);
        PhFlagsFixture.overrideIsolateMaxHeapSizeBytes(0);

        // Disable registerAdBeacon by default
        PhFlagsFixture.overrideFledgeRegisterAdBeaconEnabled(false);

        // Disable cpc billing by default
        PhFlagsFixture.overrideFledgeCpcBillingEnabled(false);

        // Disable ad selection prebuilt by default
        PhFlagsFixture.overrideFledgeAdSelectionPrebuiltUriEnabled(false);

        PhFlagsFixture.overrideFledgeOnDeviceAdSelectionTimeouts(
                PhFlagsFixture.EXTENDED_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS,
                PhFlagsFixture.EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS,
                PhFlagsFixture.EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS);

        // Clear the buyer list with an empty call to setAppInstallAdvertisers
        mAdSelectionClient.setAppInstallAdvertisers(
                new SetAppInstallAdvertisersRequest(Collections.EMPTY_SET));

        // Make sure the flags are picked up cold
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @After
    public void tearDown() throws Exception {
        if (!CtsWebViewSupportUtil.isJSSandboxAvailable(sContext)) {
            return;
        }

        mTestAdSelectionClient.resetAllAdSelectionConfigRemoteOverrides();
        mTestCustomAudienceClient.resetAllCustomAudienceOverrides();
        // Clear the buyer list with an empty call to setAppInstallAdvertisers
        mAdSelectionClient.setAppInstallAdvertisers(
                new SetAppInstallAdvertisersRequest(Collections.EMPTY_SET));
        leaveJoinedCustomAudiences();

        // Reset the filtering flag
        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(false);
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @Test
    public void testFledgeAuctionSelectionFlow_overall_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is rendered, since it had the highest bid and score
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad3"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFledgeAuctionSelectionFlow_overall_SuccessWithCpcBillingEnabled()
            throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        PhFlagsFixture.overrideFledgeCpcBillingEnabled(true);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 =
                createCustomAudienceWithAdCost(BUYER_1, bidsForBuyer1, AD_COST_1.getAdCost());

        CustomAudience customAudience2 =
                createCustomAudienceWithAdCost(BUYER_2, bidsForBuyer2, AD_COST_2.getAdCost());

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS_AD_COST)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS_AD_COST)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is rendered, since it had the highest bid and score
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad3"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFledgeAuctionSelectionFlow_overall_SuccessWithCpcBillingDisabled()
            throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        PhFlagsFixture.overrideFledgeCpcBillingEnabled(false);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 =
                createCustomAudienceWithAdCost(BUYER_1, bidsForBuyer1, AD_COST_1.getAdCost());

        CustomAudience customAudience2 =
                createCustomAudienceWithAdCost(BUYER_2, bidsForBuyer2, AD_COST_2.getAdCost());

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS_AD_COST)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS_AD_COST)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is rendered, since it had the highest bid and score
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad3"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFledgeAuctionSelectionFlow_overallWithSubdomains_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudienceWithSubdomains(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudienceWithSubdomains(BUYER_2, bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        AdSelectionConfig adSelectionConfigWithSubdomains =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                        .setDecisionLogicUri(
                                CommonFixture.getUriWithValidSubdomain(
                                        AdSelectionConfigFixture.SELLER.toString(),
                                        SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                CommonFixture.getUriWithValidSubdomain(
                                        AdSelectionConfigFixture.SELLER.toString(),
                                        SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        adSelectionConfigWithSubdomains,
                        DEFAULT_DECISION_LOGIC_JS,
                        TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI "
                        + adSelectionConfigWithSubdomains.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + adSelectionConfigWithSubdomains.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(adSelectionConfigWithSubdomains)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is rendered, since it had the highest bid and score
        Assert.assertEquals(
                CommonFixture.getUriWithValidSubdomain(BUYER_2.toString(), AD_URI_PREFIX + "/ad3"),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(
                        outcome.getAdSelectionId(), adSelectionConfigWithSubdomains);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFledgeAuctionSelectionFlow_scoringPrebuilt_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        // Enable prebuilt uri feature
        PhFlagsFixture.overrideFledgeAdSelectionPrebuiltUriEnabled(true);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        String paramKey = "reportingUrl";
        String paramValue = "https://www.test.com/reporting/seller";
        AdSelectionConfig config =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                        .setDecisionLogicUri(
                                Uri.parse(
                                        String.format(
                                                "%s://%s/%s/?%s=%s",
                                                AD_SELECTION_PREBUILT_SCHEMA,
                                                AD_SELECTION_USE_CASE,
                                                AD_SELECTION_HIGHEST_BID_WINS,
                                                paramKey,
                                                paramValue)))
                        .setTrustedScoringSignalsUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER,
                                                SELLER_TRUSTED_SIGNAL_URI_PATH)))
                        .build();

        // Adding AdSelection override for the sake of the trusted signals, no result to do
        // assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        config, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(TAG, "Running ad selection with logic URI " + config.getDecisionLogicUri());
        Log.i(TAG, "Decision logic URI domain is " + config.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(config)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is rendered, since it had the highest bid and score
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad3"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), config);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /*
    // TODO(b/267712947) Unhide Contextual Ad flow with App Install API changes
    @Test
    public void testFledgeSelectionFlow_WithContextualAds_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        BuyersDecisionLogic buyersDecisionLogic =
                new BuyersDecisionLogic(ImmutableMap.of(CommonFixture.VALID_BUYER_2,
                        new DecisionLogic(
                                "function reportWin(ad_selection_signals, per_buyer_signals,"
                                        + " signals_for_buyer, contextual_signals, "
                                        + "custom_audience_signals) { \n"
                                        + " return {'status': 0, 'results': {'reporting_uri': '"
                                        + BUYER_2_REPORTING_URI
                                        + "' } };\n"
                                        + "}"))
                );

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS,
                        buyersDecisionLogic);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        AdSelectionConfig adSelectionConfigWithContextualAds =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                        .setDecisionLogicUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER,
                                                SELLER_DECISION_LOGIC_URI_PATH)))
                        .setTrustedScoringSignalsUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER,
                                                SELLER_TRUSTED_SIGNAL_URI_PATH)))
                        .setBuyerContextualAds(createContextualAds())
                        .build();
        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(adSelectionConfigWithContextualAds)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad with bid 500 from contextual ads is rendered
        Assert.assertEquals(
                AdDataFixture.getValidRenderUriByBuyer(CommonFixture.VALID_BUYER_2, 500),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(
                        outcome.getAdSelectionId(), adSelectionConfigWithContextualAds);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFledgeSelectionFlow_OnlyContextualAds_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        AdSelectionConfig adSelectionConfigOnlyContextualAds =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        // Adding no buyers in config
                        .setCustomAudienceBuyers(ImmutableList.of())
                        .setDecisionLogicUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER,
                                                SELLER_DECISION_LOGIC_URI_PATH)))
                        .setTrustedScoringSignalsUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER,
                                                SELLER_TRUSTED_SIGNAL_URI_PATH)))
                        .setBuyerContextualAds(createContextualAds())
                        .build();

        BuyersDecisionLogic buyersDecisionLogic =
                new BuyersDecisionLogic(ImmutableMap.of(CommonFixture.VALID_BUYER_2,
                        new DecisionLogic(
                                "function reportWin(ad_selection_signals, per_buyer_signals,"
                                        + " signals_for_buyer, contextual_signals, "
                                        + "custom_audience_signals) { \n"
                                        + " return {'status': 0, 'results': {'reporting_uri': '"
                                        + BUYER_2_REPORTING_URI
                                        + "' } };\n"
                                        + "}"))
                );

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        adSelectionConfigOnlyContextualAds, DEFAULT_DECISION_LOGIC_JS,
                        TRUSTED_SCORING_SIGNALS, buyersDecisionLogic);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI "
                        + adSelectionConfigOnlyContextualAds.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(adSelectionConfigOnlyContextualAds)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad with bid 500 from contextual ads is rendered
        Assert.assertEquals(
                AdDataFixture.getValidRenderUriByBuyer(CommonFixture.VALID_BUYER_2, 500),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(
                        outcome.getAdSelectionId(), adSelectionConfigOnlyContextualAds);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
    */

    @Test
    public void testFledgeAuctionSelectionFlow_overall_register_ad_beacon_Success()
            throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        // Enable registerAdBeacon feature
        PhFlagsFixture.overrideFledgeRegisterAdBeaconEnabled(true);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG,
                        DECISION_LOGIC_JS_REGISTER_AD_BEACON,
                        TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS_REGISTER_AD_BEACON)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS_REGISTER_AD_BEACON)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is rendered, since it had the highest bid and score
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad3"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        ReportEventRequest reportInteractionClickRequest =
                new ReportEventRequest.Builder(
                                outcome.getAdSelectionId(),
                                CLICK_INTERACTION,
                                INTERACTION_DATA,
                                BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        ReportEventRequest reportInteractionHoverRequest =
                new ReportEventRequest.Builder(
                                outcome.getAdSelectionId(),
                                HOVER_INTERACTION,
                                INTERACTION_DATA,
                                BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        // Performing interaction reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportEvent(reportInteractionClickRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mAdSelectionClient
                .reportEvent(reportInteractionHoverRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFledgeFlow_manuallyUpdateCustomAudience_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer = ImmutableList.of(1.1, 2.2);
        List<Double> updatedBidsForBuyer = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience = createCustomAudience(BUYER_1, bidsForBuyer);
        CustomAudience customAudienceUpdate = createCustomAudience(BUYER_1, updatedBidsForBuyer);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception.
        joinCustomAudience(customAudience);
        joinCustomAudience(customAudienceUpdate);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception.
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience.getBuyer())
                        .setName(customAudience.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception.
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 1 is rendered, since it had the highest bid and score
        // This verifies that the custom audience was updated, since it originally only had two ads
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad3"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testAdSelection_etldViolation_failure() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        AdSelectionConfig adSelectionConfigWithEtldViolations =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                        .setDecisionLogicUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER + "etld_noise",
                                                SELLER_DECISION_LOGIC_URI_PATH)))
                        .setTrustedScoringSignalsUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER + "etld_noise",
                                                SELLER_TRUSTED_SIGNAL_URI_PATH)))
                        .build();

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        adSelectionConfigWithEtldViolations,
                        DEFAULT_DECISION_LOGIC_JS,
                        TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that exception is thrown when decision and signals
        // URIs are not etld+1 compliant
        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mAdSelectionClient
                                        .selectAds(adSelectionConfigWithEtldViolations)
                                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertThat(selectAdsException.getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testReportImpression_etldViolation_failure() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        AdSelectionConfig adSelectionConfigWithEtldViolations =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                        .setDecisionLogicUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER + "etld_noise",
                                                SELLER_DECISION_LOGIC_URI_PATH)))
                        .setTrustedScoringSignalsUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER + "etld_noise",
                                                SELLER_TRUSTED_SIGNAL_URI_PATH)))
                        .build();

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is rendered, since it had the highest bid and score
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad3"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(
                        outcome.getAdSelectionId(), adSelectionConfigWithEtldViolations);

        // Running report Impression and asserting that exception is thrown when decision and
        // signals URIs are not etld+1 compliant
        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mAdSelectionClient
                                        .reportImpression(reportImpressionRequest)
                                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertThat(selectAdsException.getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testAdSelection_skipAdsMalformedBiddingLogic_success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        String malformedBiddingLogic = " This is an invalid javascript";

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(malformedBiddingLogic)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is skipped despite having the highest bid, since it has
        // malformed bidding logic
        // The winner should come from buyer1 with the highest bid i.e. ad2
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad2"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testAdSelection_malformedScoringLogic_failure() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        String malformedScoringLogic = " This is an invalid javascript";

        // Adding malformed scoring logic AdSelection override, no result to do assertion on.
        // Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, malformedScoringLogic, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Ad Selection will fail due to scoring logic malformed
        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mAdSelectionClient
                                        .selectAds(AD_SELECTION_CONFIG)
                                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertThat(selectAdsException.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testAdSelection_skipAdsFailedGettingBiddingLogic_success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        // We do not provide override for CA 2, that should lead to failure to get biddingLogic

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is skipped despite having the highest bid, since it has
        // missing bidding logic
        // The winner should come from buyer1 with the highest bid i.e. ad2
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad2"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testAdSelection_errorGettingScoringLogic_failure() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        // Skip adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Ad Selection will fail due to scoring logic not found, because the URI that is used to
        // fetch scoring logic does not exist
        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mAdSelectionClient
                                        .selectAds(AD_SELECTION_CONFIG)
                                        .get(
                                                API_RESPONSE_LONGER_TIMEOUT_SECONDS,
                                                TimeUnit.SECONDS));
        // Sometimes a 400 status code is returned (ISE) instead of the network fetch timing out
        assertThat(
                        selectAdsException.getCause() instanceof TimeoutException
                                || selectAdsException.getCause() instanceof IllegalStateException)
                .isTrue();
    }

    @Test
    public void testAdSelectionFlow_skipNonActivatedCA_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        // CA 2 activated long in the future
        CustomAudience customAudience2 =
                createCustomAudience(
                        BUYER_2,
                        bidsForBuyer2,
                        CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is skipped despite having the highest bid, since it is
        // not activated yet
        // The winner should come from buyer1 with the highest bid i.e. ad2
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad2"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testAdSelectionFlow_skipExpiredCA_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudienceRegularExpiry = createCustomAudience(BUYER_1, bidsForBuyer1);

        int caTimeToExpireSeconds = 2;
        // Since we cannot create CA which is already expired, we create one which expires in few
        // seconds
        // We will then wait till this CA expires before we run Ad Selection
        CustomAudience customAudienceEarlyExpiry =
                createCustomAudience(
                        BUYER_2,
                        bidsForBuyer2,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        Instant.now().plusSeconds(caTimeToExpireSeconds));

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."

        // Join the CA with early expiry first, to avoid waiting too long for another CA join
        joinCustomAudience(customAudienceEarlyExpiry);
        joinCustomAudience(customAudienceRegularExpiry);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceRegularExpiry.getBuyer())
                        .setName(customAudienceRegularExpiry.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceEarlyExpiry.getBuyer())
                        .setName(customAudienceEarlyExpiry.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Wait to ensure that CA2 gets expired
        CommonFixture.doSleep((caTimeToExpireSeconds * 2 * 1000));

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is skipped despite having the highest bid, since it is
        // expired
        // The winner should come from buyer1 with the highest bid i.e. ad2
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad2"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testAdSelectionFlow_skipCAsThatTimeoutDuringBidding_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        long biddingScoringTimeoutMs = 5_000L;
        PhFlagsFixture.overrideFledgeOnDeviceAdSelectionTimeouts(
                biddingScoringTimeoutMs,
                biddingScoringTimeoutMs,
                PhFlagsFixture.EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        try {
            List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
            List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

            CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);
            CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

            // Joining custom audiences, no result to do assertion on. Failures will generate an
            // exception.
            joinCustomAudience(customAudience1);
            joinCustomAudience(customAudience2);

            String jsWaitMoreThanAllowedForBiddingPerCa =
                    insertJsWait(biddingScoringTimeoutMs + 100L);
            String readBidFromAdMetadataWithDelayJs =
                    "function generateBid(ad, auction_signals, per_buyer_signals,"
                            + " trusted_bidding_signals, contextual_signals,"
                            + " custom_audience_signals) { \n"
                            + jsWaitMoreThanAllowedForBiddingPerCa
                            + "    return { 'status': 0, 'ad': result, 'bid': result.metadata"
                            + ".result, "
                            + "'render': result.render_uri };\n"
                            + "}\n";

            // Adding AdSelection override, no result to do assertion on. Failures will generate an
            // exception.
            AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                    new AddAdSelectionOverrideRequest(
                            AD_SELECTION_CONFIG,
                            DEFAULT_DECISION_LOGIC_JS,
                            TRUSTED_SCORING_SIGNALS);

            mTestAdSelectionClient
                    .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                    new AddCustomAudienceOverrideRequest.Builder()
                            .setBuyer(customAudience1.getBuyer())
                            .setName(customAudience1.getName())
                            .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                            .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                            .build();
            AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                    new AddCustomAudienceOverrideRequest.Builder()
                            .setBuyer(customAudience2.getBuyer())
                            .setName(customAudience2.getName())
                            .setBiddingLogicJs(readBidFromAdMetadataWithDelayJs)
                            .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                            .build();

            // Adding Custom audience override, no result to do assertion on. Failures will
            // generate an exception.
            mTestCustomAudienceClient
                    .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            mTestCustomAudienceClient
                    .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Running ad selection and asserting that the outcome is returned in < 10 seconds
            AdSelectionOutcome outcome =
                    mAdSelectionClient
                            .selectAds(AD_SELECTION_CONFIG)
                            .get(API_RESPONSE_LONGER_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Assert that the ad3 from buyer 2 is skipped despite having the highest bid, since it
            // timed out
            // The winner should come from buyer1 with the highest bid i.e. ad2
            Assert.assertEquals(
                    CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad2"), outcome.getRenderUri());

            ReportImpressionRequest reportImpressionRequest =
                    new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

            // Performing reporting, and asserting that no exception is thrown
            mAdSelectionClient
                    .reportImpression(reportImpressionRequest)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            PhFlagsFixture.overrideFledgeOnDeviceAdSelectionTimeouts(
                    PhFlagsFixture.EXTENDED_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS,
                    PhFlagsFixture.EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS,
                    PhFlagsFixture.EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS);
            AdservicesTestHelper.killAdservicesProcess(sContext);
        }
    }

    @Test
    public void testAdSelection_overallTimeout_Failure() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        long longerBiddingScoringTimeoutMs = 8_000L;
        long shortOverallAdSelectionTimeoutMs = 2_000L;
        PhFlagsFixture.overrideFledgeOnDeviceAdSelectionTimeouts(
                longerBiddingScoringTimeoutMs,
                longerBiddingScoringTimeoutMs,
                shortOverallAdSelectionTimeoutMs);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        try {
            List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
            List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

            CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

            CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

            // Joining custom audiences, no result to do assertion on. Failures will generate an
            // exception.
            joinCustomAudience(customAudience1);
            joinCustomAudience(customAudience2);

            String jsWaitMoreThanAllowedForBiddingScoring =
                    insertJsWait(longerBiddingScoringTimeoutMs - 100L);
            String biddingLogicWithWaitJs =
                    "function generateBid(ad, auction_signals, per_buyer_signals,"
                            + " trusted_bidding_signals, contextual_signals,"
                            + " custom_audience_signals) { \n"
                            + jsWaitMoreThanAllowedForBiddingScoring
                            + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                            + "}\n"
                            + "function reportWin(ad_selection_signals, per_buyer_signals,"
                            + " signals_for_buyer, contextual_signals, custom_audience_signals) {"
                            + " \n"
                            + " return {'status': 0, 'results': {'reporting_uri': '"
                            + BUYER_2_REPORTING_URI
                            + "' } };\n"
                            + "}";
            String useBidAsScoringWithDelayJs =
                    "function scoreAd(ad, bid, auction_config, seller_signals, "
                            + "trusted_scoring_signals, contextual_signal, user_signal, "
                            + "custom_audience_signal) { \n"
                            + jsWaitMoreThanAllowedForBiddingScoring
                            + "  return {'status': 0, 'score': bid };\n"
                            + "}";

            // Adding AdSelection override, no result to do assertion on. Failures will generate an
            // exception.
            AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                    new AddAdSelectionOverrideRequest(
                            AD_SELECTION_CONFIG,
                            useBidAsScoringWithDelayJs,
                            TRUSTED_SCORING_SIGNALS);

            mTestAdSelectionClient
                    .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest =
                    new AddCustomAudienceOverrideRequest.Builder()
                            .setBuyer(customAudience2.getBuyer())
                            .setName(customAudience2.getName())
                            .setBiddingLogicJs(biddingLogicWithWaitJs)
                            .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                            .build();

            // Adding Custom audience override, no result to do assertion on. Failures will
            // generate an exception.
            mTestCustomAudienceClient
                    .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Log.i(
                    TAG,
                    "Running ad selection with logic URI "
                            + AD_SELECTION_CONFIG.getDecisionLogicUri());
            Log.i(
                    TAG,
                    "Decision logic URI domain is "
                            + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

            // Running ad selection and asserting that the outcome is returned in < 10 seconds
            Exception selectAdsException =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    mAdSelectionClient
                                            .selectAds(AD_SELECTION_CONFIG)
                                            .get(
                                                    shortOverallAdSelectionTimeoutMs + 200L,
                                                    TimeUnit.MILLISECONDS));
            assertThat(selectAdsException.getCause()).isInstanceOf(TimeoutException.class);
        } finally {
            PhFlagsFixture.overrideFledgeOnDeviceAdSelectionTimeouts(
                    PhFlagsFixture.EXTENDED_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS,
                    PhFlagsFixture.EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS,
                    PhFlagsFixture.EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS);
            AdservicesTestHelper.killAdservicesProcess(sContext);
        }
    }

    @Test
    public void testAdSelectionFromOutcomesFlow_overall_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        // Perform ad selection to persist two results
        double bid1 = 10.0, bid2 = 15.0;
        AdSelectionOutcome outcome1 =
                runAdSelectionAsPreSteps(bid1, BUYER_1, BUYER_1_BIDDING_LOGIC_JS);
        AdSelectionOutcome outcome2 =
                runAdSelectionAsPreSteps(bid2, BUYER_2, BUYER_2_BIDDING_LOGIC_JS);

        // Inputs for outcome selection
        AdSelectionSignals selectionSignals = AdSelectionSignals.EMPTY;
        Uri selectionUri =
                Uri.parse(
                        String.format(
                                "https://%s%s",
                                AdSelectionConfigFixture.SELLER, SELLER_DECISION_LOGIC_URI_PATH));
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        List.of(outcome1.getAdSelectionId(), outcome2.getAdSelectionId()),
                        selectionSignals,
                        selectionUri);

        // Add overrides
        String selectionLogicPickSmallestJs =
                "function selectOutcome(outcomes, selection_signals) {\n"
                        + "    outcomes.sort(function(a, b) { return b.bid - a.bid;});\n"
                        + "    return {'status': 0, 'result': outcomes[0]};\n"
                        + "}";
        AddAdSelectionFromOutcomesOverrideRequest request =
                new AddAdSelectionFromOutcomesOverrideRequest(
                        config, selectionLogicPickSmallestJs, AdSelectionSignals.EMPTY);
        mTestAdSelectionClient
                .overrideAdSelectionFromOutcomesConfigRemoteInfo(request)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Run select ads from outcomes
        AdSelectionOutcome selectionOutcome =
                mAdSelectionClient
                        .selectAds(config)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(selectionOutcome.hasOutcome());
        assertEquals(outcome2.getAdSelectionId(), selectionOutcome.getAdSelectionId());
    }

    @Test
    public void testAdSelectionFromOutcomesFlow_waterfallWithPrebuilt_returnsOutcomeSuccess()
            throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        // Enable prebuilt uri feature
        PhFlagsFixture.overrideFledgeAdSelectionPrebuiltUriEnabled(true);

        // Perform ad selection to persist two results
        double bid1 = 10.0;
        double bidFloor = 5;
        AdSelectionOutcome outcome =
                runAdSelectionAsPreSteps(bid1, BUYER_1, BUYER_1_BIDDING_LOGIC_JS);

        // Inputs for outcome selection
        String paramKey = "bidFloor";
        String paramValue = "bid_floor";
        Uri prebuiltSelectionUri =
                Uri.parse(
                        String.format(
                                "%s://%s/%s/?%s=%s",
                                AD_SELECTION_PREBUILT_SCHEMA,
                                AD_SELECTION_FROM_OUTCOMES_USE_CASE,
                                AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION,
                                paramKey,
                                paramValue));
        AdSelectionSignals selectionSignals =
                AdSelectionSignals.fromString(String.format("{%s: %s}", paramValue, bidFloor));
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdSelectionConfigFixture.SELLER,
                        List.of(outcome.getAdSelectionId()),
                        selectionSignals,
                        prebuiltSelectionUri);

        // Skipping adding overrides since prebuilt uri is used

        // Run select ads from outcomes
        AdSelectionOutcome selectionOutcome =
                mAdSelectionClient
                        .selectAds(config)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(selectionOutcome.hasOutcome());
        assertEquals(outcome.getAdSelectionId(), selectionOutcome.getAdSelectionId());
    }

    @Test
    public void testAdSelectionFromOutcomesFlow_waterfallWithPrebuilt_returnsNoOutcomeSuccess()
            throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        // Enable prebuilt uri feature
        PhFlagsFixture.overrideFledgeAdSelectionPrebuiltUriEnabled(true);

        // Perform ad selection to persist two results
        double bid1 = 10.0;
        double bidFloor = 15;
        AdSelectionOutcome outcome =
                runAdSelectionAsPreSteps(bid1, BUYER_1, BUYER_1_BIDDING_LOGIC_JS);

        // Inputs for outcome selection
        String paramKey = "bidFloor";
        String paramValue = "bid_floor";
        Uri prebuiltSelectionUri =
                Uri.parse(
                        String.format(
                                "%s://%s/%s/?%s=%s",
                                AD_SELECTION_PREBUILT_SCHEMA,
                                AD_SELECTION_FROM_OUTCOMES_USE_CASE,
                                AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION,
                                paramKey,
                                paramValue));
        AdSelectionSignals selectionSignals =
                AdSelectionSignals.fromString(String.format("{%s: %s}", paramValue, bidFloor));
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdSelectionConfigFixture.SELLER,
                        List.of(outcome.getAdSelectionId()),
                        selectionSignals,
                        prebuiltSelectionUri);

        // Skipping adding overrides since prebuilt uri is used

        // Run select ads from outcomes
        AdSelectionOutcome selectionOutcome =
                mAdSelectionClient
                        .selectAds(config)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertFalse(selectionOutcome.hasOutcome());
    }

    @Test
    public void testAdSelectionFromOutcomesFlow_returnsNull_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        // Perform ad selection to persist two results
        double bid1 = 10.0, bid2 = 15.0;
        AdSelectionOutcome outcome1 =
                runAdSelectionAsPreSteps(bid1, BUYER_1, BUYER_1_BIDDING_LOGIC_JS);
        AdSelectionOutcome outcome2 =
                runAdSelectionAsPreSteps(bid2, BUYER_2, BUYER_2_BIDDING_LOGIC_JS);

        // Inputs for outcome selection
        AdSelectionSignals selectionSignals = AdSelectionSignals.EMPTY;
        Uri selectionUri =
                Uri.parse(
                        String.format(
                                "https://%s%s",
                                AdSelectionConfigFixture.SELLER, SELLER_DECISION_LOGIC_URI_PATH));
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        List.of(outcome1.getAdSelectionId(), outcome2.getAdSelectionId()),
                        selectionSignals,
                        selectionUri);

        // Add overrides
        String selectionLogicPickSmallestJs =
                "function selectOutcome(outcomes, selection_signals) {\n"
                        + "    return {'status': 0, 'result': null};\n"
                        + "}";
        AddAdSelectionFromOutcomesOverrideRequest request =
                new AddAdSelectionFromOutcomesOverrideRequest(
                        config, selectionLogicPickSmallestJs, AdSelectionSignals.EMPTY);
        mTestAdSelectionClient
                .overrideAdSelectionFromOutcomesConfigRemoteInfo(request)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Run select ads from outcomes
        AdSelectionOutcome selectionOutcome =
                mAdSelectionClient
                        .selectAds(config)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertFalse(selectionOutcome.hasOutcome());
        assertEquals(AdSelectionOutcome.NO_OUTCOME, selectionOutcome);
    }

    @Test
    public void testAdSelectionFromOutcomesFlow_overallTimeout_failure() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        // Perform ad selection to persist two results
        double bid1 = 10.0, bid2 = 15.0;
        AdSelectionOutcome outcome1 =
                runAdSelectionAsPreSteps(bid1, BUYER_1, BUYER_1_BIDDING_LOGIC_JS);
        AdSelectionOutcome outcome2 =
                runAdSelectionAsPreSteps(bid2, BUYER_2, BUYER_2_BIDDING_LOGIC_JS);

        // Inputs for outcome selection
        AdSelectionSignals selectionSignals = AdSelectionSignals.EMPTY;
        Uri selectionUri =
                Uri.parse(
                        String.format(
                                "https://%s%s",
                                AdSelectionConfigFixture.SELLER, SELLER_DECISION_LOGIC_URI_PATH));
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        List.of(outcome1.getAdSelectionId(), outcome2.getAdSelectionId()),
                        selectionSignals,
                        selectionUri);

        // Add overrides
        String jsWaitMoreThanAllowedForOutcomeSelection = insertJsWait(20000);
        String selectionLogicPickSmallestJs =
                "function selectOutcome(outcomes, selection_signals) {\n"
                        + jsWaitMoreThanAllowedForOutcomeSelection
                        + "    return {'status': 0, 'result': outcomes[0]};\n"
                        + "}";
        AddAdSelectionFromOutcomesOverrideRequest request =
                new AddAdSelectionFromOutcomesOverrideRequest(
                        config, selectionLogicPickSmallestJs, AdSelectionSignals.EMPTY);
        mTestAdSelectionClient
                .overrideAdSelectionFromOutcomesConfigRemoteInfo(request)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mAdSelectionClient
                                        .selectAds(config)
                                        .get(
                                                API_RESPONSE_LONGER_TIMEOUT_SECONDS,
                                                TimeUnit.SECONDS));
        assertThat(selectAdsException.getCause()).isInstanceOf(TimeoutException.class);
    }

    @Test
    public void testAdSelectionFromOutcomesFlow_emptyAdSelectionIds_Failure() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        // Inputs for outcome selection
        AdSelectionSignals selectionSignals = AdSelectionSignals.EMPTY;
        Uri selectionUri = Uri.parse("https://test.com/url-wont-used");
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        Collections.emptyList(), selectionSignals, selectionUri);

        // Add overrides
        String selectionLogicPickSmallestJs =
                "function selectOutcome(outcomes, selection_signals) {\n"
                        + "    return {'status': 0, 'result': null};\n"
                        + "}";
        AddAdSelectionFromOutcomesOverrideRequest request =
                new AddAdSelectionFromOutcomesOverrideRequest(
                        config, selectionLogicPickSmallestJs, AdSelectionSignals.EMPTY);
        mTestAdSelectionClient
                .overrideAdSelectionFromOutcomesConfigRemoteInfo(request)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mAdSelectionClient
                                        .selectAds(config)
                                        .get(
                                                API_RESPONSE_LONGER_TIMEOUT_SECONDS,
                                                TimeUnit.SECONDS));
        assertThat(selectAdsException.getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testAdSelectionFromOutcomesFlow_nonExistingAdSelectionId_Failure()
            throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        // Inputs for outcome selection
        long nonExistingAdSelectionId = 12345;
        AdSelectionSignals selectionSignals = AdSelectionSignals.EMPTY;
        Uri selectionUri = Uri.parse("https://test.com/url-wont-used");
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        Collections.singletonList(nonExistingAdSelectionId),
                        selectionSignals,
                        selectionUri);

        // Add overrides
        String selectionLogicPickSmallestJs =
                "function selectOutcome(outcomes, selection_signals) {\n"
                        + "    return {'status': 0, 'result': null};\n"
                        + "}";
        AddAdSelectionFromOutcomesOverrideRequest request =
                new AddAdSelectionFromOutcomesOverrideRequest(
                        config, selectionLogicPickSmallestJs, AdSelectionSignals.EMPTY);
        mTestAdSelectionClient
                .overrideAdSelectionFromOutcomesConfigRemoteInfo(request)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mAdSelectionClient
                                        .selectAds(config)
                                        .get(
                                                API_RESPONSE_LONGER_TIMEOUT_SECONDS,
                                                TimeUnit.SECONDS));
        assertThat(selectAdsException.getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testAdSelectionFromOutcomesFlow_malformedJs_failure() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        // Perform ad selection to persist two results
        double bid1 = 10.0, bid2 = 15.0;
        AdSelectionOutcome outcome1 =
                runAdSelectionAsPreSteps(bid1, BUYER_1, BUYER_1_BIDDING_LOGIC_JS);
        AdSelectionOutcome outcome2 =
                runAdSelectionAsPreSteps(bid2, BUYER_2, BUYER_2_BIDDING_LOGIC_JS);

        // Inputs for outcome selection
        AdSelectionSignals selectionSignals = AdSelectionSignals.EMPTY;
        Uri selectionUri =
                Uri.parse(
                        String.format(
                                "https://%s%s",
                                AdSelectionConfigFixture.SELLER, SELLER_DECISION_LOGIC_URI_PATH));
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        List.of(outcome1.getAdSelectionId(), outcome2.getAdSelectionId()),
                        selectionSignals,
                        selectionUri);

        // Add overrides
        String selectionLogicPickSmallestJs = "malformed js";
        AddAdSelectionFromOutcomesOverrideRequest request =
                new AddAdSelectionFromOutcomesOverrideRequest(
                        config, selectionLogicPickSmallestJs, AdSelectionSignals.EMPTY);
        mTestAdSelectionClient
                .overrideAdSelectionFromOutcomesConfigRemoteInfo(request)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mAdSelectionClient
                                        .selectAds(config)
                                        .get(
                                                API_RESPONSE_LONGER_TIMEOUT_SECONDS,
                                                TimeUnit.SECONDS));
        assertThat(selectAdsException.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Ignore
    @Test
    public void testFledgeAuctionAppFilteringFlow_overall_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(true);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        // Allow BUYER_2 to filter on the test package
        SetAppInstallAdvertisersRequest request =
                new SetAppInstallAdvertisersRequest(new HashSet<>(Arrays.asList(BUYER_2)));
        ListenableFuture<Void> appInstallFuture =
                mAdSelectionClient.setAppInstallAdvertisers(request);
        assertNull(appInstallFuture.get());

        // Run the auction with the ads that should be filtered
        String packageName = sContext.getPackageName();
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        List<AdData> adsForBuyer2 = new ArrayList<>();
        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata and add filters to the adss
        for (int i = 0; i < bidsForBuyer2.size(); i++) {
            adsForBuyer2.add(
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bidsForBuyer2.get(i) + "}")
                            .setAdFilters(
                                    new AdFilters.Builder()
                                            .setAppInstallFilters(
                                                    new AppInstallFilters.Builder()
                                                            .setPackageNames(
                                                                    new HashSet<>(
                                                                            Arrays.asList(
                                                                                    packageName)))
                                                            .build())
                                            .build())
                            .build());
        }

        CustomAudience customAudience2 =
                new CustomAudience.Builder()
                        .setBuyer(BUYER_2)
                        .setName(BUYER_2 + CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2))
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                        BUYER_2))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(BUYER_2, BUYER_BIDDING_LOGIC_URI_PATH))
                        .setAds(adsForBuyer2)
                        .build();

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 1 is rendered, since had the highest unfiltered score
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad2"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Ignore
    @Test
    public void testFledgeAuctionAppFilteringFlow_overall_AppInstallFailure() throws Exception {
        /**
         * In this test, we give bad input to setAppInstallAdvertisers and ensure that it gives an
         * error, and does not filter based on AdData filters.
         */
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(true);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        // Allow BUYER_2 to filter on the test package
        SetAppInstallAdvertisersRequest request =
                new SetAppInstallAdvertisersRequest(
                        new HashSet<>(Arrays.asList(BUYER_2, INVALID_EMPTY_BUYER)));
        mAdSelectionClient.setAppInstallAdvertisers(request);
        ListenableFuture<Void> appInstallFuture =
                mAdSelectionClient.setAppInstallAdvertisers(request);
        assertThrows(ExecutionException.class, appInstallFuture::get);

        // Run the auction with the ads that should be filtered
        String packageName = sContext.getPackageName();
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        List<AdData> adsForBuyer2 = new ArrayList<>();
        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata and add filters to the adss
        for (int i = 0; i < bidsForBuyer2.size(); i++) {
            adsForBuyer2.add(
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bidsForBuyer2.get(i) + "}")
                            .setAdFilters(
                                    new AdFilters.Builder()
                                            .setAppInstallFilters(
                                                    new AppInstallFilters.Builder()
                                                            .setPackageNames(
                                                                    new HashSet<>(
                                                                            Arrays.asList(
                                                                                    packageName)))
                                                            .build())
                                            .build())
                            .build());
        }

        CustomAudience customAudience2 =
                new CustomAudience.Builder()
                        .setBuyer(BUYER_2)
                        .setName(BUYER_2 + CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2))
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                        BUYER_2))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(BUYER_2, BUYER_BIDDING_LOGIC_URI_PATH))
                        .setAds(adsForBuyer2)
                        .build();

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 1 is rendered, since had the highest unfiltered score
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad3"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFrequencyCapFiltering_NonWinEvent_FiltersAds() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(true);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        // Because frequency cap events are per-buyer and not per-CA, they cannot be cleared,
        // so to eliminate flakiness within a suite run, each test case will have its own unique key
        final int keyToFilter = sFrequencyCapKeyToFilter.incrementAndGet();

        FrequencyCapFilters nonWinFilter =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForImpressionEvents(
                                ImmutableList.of(
                                        new KeyedFrequencyCap.Builder(
                                                        keyToFilter, 1, Duration.ofSeconds(10))
                                                .build()))
                        .build();

        AdData adWithNonWinFrequencyCapFilter =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad_with_filters"))
                        .setMetadata("{\"result\":10}")
                        .setAdCounterKeys(ImmutableSet.of(keyToFilter))
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(nonWinFilter)
                                        .build())
                        .build();

        AdData adWithoutFilters =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(
                                        BUYER_1, AD_URI_PREFIX + "/ad_without_filters"))
                        .setMetadata("{\"result\":5}")
                        .build();

        CustomAudience.Builder sameCustomAudienceBuilder =
                new CustomAudience.Builder()
                        .setBuyer(BUYER_1)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1))
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                        BUYER_1))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(BUYER_1, BUYER_BIDDING_LOGIC_URI_PATH));

        CustomAudience customAudienceWithFrequencyCapFilters =
                sameCustomAudienceBuilder
                        .setName(keyToFilter + "_ca_with_filters")
                        .setAds(ImmutableList.of(adWithNonWinFrequencyCapFilter))
                        .build();

        CustomAudience customAudienceWithoutFrequencyCapFilters =
                sameCustomAudienceBuilder
                        .setName(keyToFilter + "_ca_without_filters")
                        .setAds(ImmutableList.of(adWithoutFilters))
                        .build();

        // Joining custom audiences, no result to do assertion on
        // Failures will generate an exception
        joinCustomAudience(customAudienceWithFrequencyCapFilters);
        // Joining custom audiences, no result to do assertion on
        // Failures will generate an exception
        joinCustomAudience(customAudienceWithoutFrequencyCapFilters);

        // Adding AdSelection override, no result to do assertion on
        // Failures will generate an exception
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceWithFiltersOverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceWithFrequencyCapFilters.getBuyer())
                        .setName(customAudienceWithFrequencyCapFilters.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceWithoutFiltersOverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceWithoutFrequencyCapFilters.getBuyer())
                        .setName(customAudienceWithoutFrequencyCapFilters.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on
        // Failures will generate an exception
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceWithFiltersOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceWithoutFiltersOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome1 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad with filters won
        assertThat(outcome1.getRenderUri())
                .isEqualTo(adWithNonWinFrequencyCapFilter.getRenderUri());

        ReportImpressionRequest reportImpressionRequest1 =
                new ReportImpressionRequest(outcome1.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Update ad counter histogram for the first ad selection outcome
        UpdateAdCounterHistogramRequest updateRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                outcome1.getAdSelectionId(),
                                FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                                BUYER_1)
                        .build();
        mAdSelectionClient
                .updateAdCounterHistogram(updateRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection again and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome2 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad without filters won after updating the ad counter histogram
        assertThat(outcome2.getRenderUri()).isEqualTo(adWithoutFilters.getRenderUri());

        ReportImpressionRequest reportImpressionRequest2 =
                new ReportImpressionRequest(outcome2.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFrequencyCapFiltering_DifferentNonWinEvent_IsNotFiltered()
            throws ExecutionException, InterruptedException, TimeoutException {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(true);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        // Because frequency cap events are per-buyer and not per-CA, they cannot be cleared,
        // so to eliminate flakiness within a suite run, each test case will have its own unique key
        final int keyToFilter = sFrequencyCapKeyToFilter.incrementAndGet();

        FrequencyCapFilters nonWinFilter =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForImpressionEvents(
                                ImmutableList.of(
                                        new KeyedFrequencyCap.Builder(
                                                        keyToFilter, 1, Duration.ofSeconds(10))
                                                .build()))
                        .build();

        AdData adWithNonWinFrequencyCapFilter =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad_with_filters"))
                        .setMetadata("{\"result\":10}")
                        .setAdCounterKeys(ImmutableSet.of(keyToFilter))
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(nonWinFilter)
                                        .build())
                        .build();

        AdData adWithoutFilters =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(
                                        BUYER_1, AD_URI_PREFIX + "/ad_without_filters"))
                        .setMetadata("{\"result\":5}")
                        .build();

        CustomAudience.Builder sameCustomAudienceBuilder =
                new CustomAudience.Builder()
                        .setBuyer(BUYER_1)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1))
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                        BUYER_1))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(BUYER_1, BUYER_BIDDING_LOGIC_URI_PATH));

        CustomAudience customAudienceWithFrequencyCapFilters =
                sameCustomAudienceBuilder
                        .setName(keyToFilter + "_ca_with_filters")
                        .setAds(ImmutableList.of(adWithNonWinFrequencyCapFilter))
                        .build();

        CustomAudience customAudienceWithoutFrequencyCapFilters =
                sameCustomAudienceBuilder
                        .setName(keyToFilter + "_ca_without_filters")
                        .setAds(ImmutableList.of(adWithoutFilters))
                        .build();

        // Joining custom audiences, no result to do assertion on
        // Failures will generate an exception
        joinCustomAudience(customAudienceWithFrequencyCapFilters);
        joinCustomAudience(customAudienceWithoutFrequencyCapFilters);

        // Adding AdSelection override, no result to do assertion on
        // Failures will generate an exception
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceWithFiltersOverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceWithFrequencyCapFilters.getBuyer())
                        .setName(customAudienceWithFrequencyCapFilters.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceWithoutFiltersOverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceWithoutFrequencyCapFilters.getBuyer())
                        .setName(customAudienceWithoutFrequencyCapFilters.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on
        // Failures will generate an exception
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceWithFiltersOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceWithoutFiltersOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome1 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad with filters won
        assertThat(outcome1.getRenderUri())
                .isEqualTo(adWithNonWinFrequencyCapFilter.getRenderUri());

        ReportImpressionRequest reportImpressionRequest1 =
                new ReportImpressionRequest(outcome1.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Update ad counter histogram for the first ad selection outcome on the wrong event type
        UpdateAdCounterHistogramRequest updateRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                outcome1.getAdSelectionId(),
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                BUYER_1)
                        .build();
        mAdSelectionClient
                .updateAdCounterHistogram(updateRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection again and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome2 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad with filters won even after updating the ad counter histogram
        assertThat(outcome2.getRenderUri())
                .isEqualTo(adWithNonWinFrequencyCapFilter.getRenderUri());

        ReportImpressionRequest reportImpressionRequest2 =
                new ReportImpressionRequest(outcome2.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFrequencyCapFiltering_NonWinEventDifferentKey_IsNotFiltered()
            throws ExecutionException, InterruptedException, TimeoutException {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(true);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        // Because frequency cap events are per-buyer and not per-CA, they cannot be cleared,
        // so to eliminate flakiness within a suite run, each test case will have its own unique key
        final int keyToFilter = sFrequencyCapKeyToFilter.incrementAndGet();
        final int otherKeyNotFiltered = -1 * keyToFilter;

        FrequencyCapFilters nonWinFilterWithKeyToFilter =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForImpressionEvents(
                                ImmutableList.of(
                                        new KeyedFrequencyCap.Builder(
                                                        keyToFilter, 1, Duration.ofSeconds(10))
                                                .build()))
                        .build();

        AdData adWithFrequencyCapKeyToFilter =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(
                                        BUYER_1, AD_URI_PREFIX + "/adWithFrequencyCapKeyToFilter"))
                        .setMetadata("{\"result\":10}")
                        .setAdCounterKeys(ImmutableSet.of(keyToFilter))
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(nonWinFilterWithKeyToFilter)
                                        .build())
                        .build();

        FrequencyCapFilters nonWinFilterWithOtherKey =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForImpressionEvents(
                                ImmutableList.of(
                                        new KeyedFrequencyCap.Builder(
                                                        otherKeyNotFiltered,
                                                        1,
                                                        Duration.ofSeconds(10))
                                                .build()))
                        .build();

        AdData adWithOtherFrequencyCapKey =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(
                                        BUYER_1, AD_URI_PREFIX + "/adWithOtherFrequencyCapKey"))
                        .setMetadata("{\"result\":5}")
                        .setAdCounterKeys(ImmutableSet.of(otherKeyNotFiltered))
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(nonWinFilterWithOtherKey)
                                        .build())
                        .build();

        CustomAudience customAudienceWithFrequencyCapFilters =
                new CustomAudience.Builder()
                        .setBuyer(BUYER_1)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1))
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                        BUYER_1))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(BUYER_1, BUYER_BIDDING_LOGIC_URI_PATH))
                        .setName(keyToFilter + "_ca_with_filters")
                        .setAds(
                                ImmutableList.of(
                                        adWithFrequencyCapKeyToFilter, adWithOtherFrequencyCapKey))
                        .build();

        // Joining custom audiences, no result to do assertion on
        // Failures will generate an exception
        joinCustomAudience(customAudienceWithFrequencyCapFilters);

        // Adding AdSelection override, no result to do assertion on
        // Failures will generate an exception
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceWithFiltersOverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceWithFrequencyCapFilters.getBuyer())
                        .setName(customAudienceWithFrequencyCapFilters.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on
        // Failures will generate an exception
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceWithFiltersOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome1 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad with filters won
        assertThat(outcome1.getRenderUri()).isEqualTo(adWithFrequencyCapKeyToFilter.getRenderUri());

        ReportImpressionRequest reportImpressionRequest1 =
                new ReportImpressionRequest(outcome1.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Update ad counter histogram for the first ad selection outcome
        UpdateAdCounterHistogramRequest updateRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                outcome1.getAdSelectionId(),
                                FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                                BUYER_1)
                        .build();
        mAdSelectionClient
                .updateAdCounterHistogram(updateRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection again and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome2 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad with a different key won after updating the ad counter histogram
        assertThat(outcome2.getRenderUri()).isEqualTo(adWithOtherFrequencyCapKey.getRenderUri());

        ReportImpressionRequest reportImpressionRequest2 =
                new ReportImpressionRequest(outcome2.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFrequencyCapFiltering_NonWinEventDifferentBuyer_IsNotFiltered()
            throws ExecutionException, InterruptedException, TimeoutException {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(true);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        // Because frequency cap events are per-buyer and not per-CA, they cannot be cleared,
        // so to eliminate flakiness within a suite run, each test case will have its own unique key
        final int keyToFilter = sFrequencyCapKeyToFilter.incrementAndGet();

        FrequencyCapFilters nonWinFilter =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForImpressionEvents(
                                ImmutableList.of(
                                        new KeyedFrequencyCap.Builder(
                                                        keyToFilter, 1, Duration.ofSeconds(10))
                                                .build()))
                        .build();

        AdData adForBuyer1ToFilter =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad_for_buyer1"))
                        .setMetadata("{\"result\":10}")
                        .setAdCounterKeys(ImmutableSet.of(keyToFilter))
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(nonWinFilter)
                                        .build())
                        .build();

        AdData adForBuyer2 =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad_for_buyer2"))
                        .setMetadata("{\"result\":5}")
                        .setAdCounterKeys(ImmutableSet.of(keyToFilter))
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(nonWinFilter)
                                        .build())
                        .build();

        CustomAudience.Builder sameCustomAudienceBuilder =
                new CustomAudience.Builder()
                        .setName(keyToFilter + "_ca_with_filters")
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);

        CustomAudience customAudienceForBuyer1 =
                sameCustomAudienceBuilder
                        .setBuyer(BUYER_1)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1))
                        .setTrustedBiddingData(
                                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                        BUYER_1))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(BUYER_1, BUYER_BIDDING_LOGIC_URI_PATH))
                        .setAds(ImmutableList.of(adForBuyer1ToFilter))
                        .build();

        CustomAudience customAudienceForBuyer2 =
                sameCustomAudienceBuilder
                        .setBuyer(BUYER_2)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2))
                        .setTrustedBiddingData(
                                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                        BUYER_2))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(BUYER_2, BUYER_BIDDING_LOGIC_URI_PATH))
                        .setAds(ImmutableList.of(adForBuyer2))
                        .build();

        // Joining custom audiences, no result to do assertion on
        // Failures will generate an exception
        joinCustomAudience(customAudienceForBuyer1);
        joinCustomAudience(customAudienceForBuyer2);

        // Adding AdSelection override, no result to do assertion on
        // Failures will generate an exception
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceForBuyer1OverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceForBuyer1.getBuyer())
                        .setName(customAudienceForBuyer1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceForBuyer2OverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceForBuyer2.getBuyer())
                        .setName(customAudienceForBuyer2.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on
        // Failures will generate an exception
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceForBuyer1OverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceForBuyer2OverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome1 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad with filters won
        assertThat(outcome1.getRenderUri()).isEqualTo(adForBuyer1ToFilter.getRenderUri());

        ReportImpressionRequest reportImpressionRequest1 =
                new ReportImpressionRequest(outcome1.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Update ad counter histogram for the first ad selection outcome
        UpdateAdCounterHistogramRequest updateRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                outcome1.getAdSelectionId(),
                                FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                                BUYER_1)
                        .build();
        mAdSelectionClient
                .updateAdCounterHistogram(updateRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection again and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome2 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad without filters won after updating the ad counter histogram
        assertThat(outcome2.getRenderUri()).isEqualTo(adForBuyer2.getRenderUri());

        ReportImpressionRequest reportImpressionRequest2 =
                new ReportImpressionRequest(outcome2.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFrequencyCapFiltering_NonWinEventWrongAdSelection_DoesNotFilterAds()
            throws ExecutionException, InterruptedException, TimeoutException {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(true);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        // Because frequency cap events are per-buyer and not per-CA, they cannot be cleared,
        // so to eliminate flakiness within a suite run, each test case will have its own unique key
        final int keyToFilter = sFrequencyCapKeyToFilter.incrementAndGet();

        FrequencyCapFilters nonWinFilter =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForImpressionEvents(
                                ImmutableList.of(
                                        new KeyedFrequencyCap.Builder(
                                                        keyToFilter, 1, Duration.ofSeconds(10))
                                                .build()))
                        .build();

        AdData adWithNonWinFrequencyCapFilter =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad_with_filters"))
                        .setMetadata("{\"result\":10}")
                        .setAdCounterKeys(ImmutableSet.of(keyToFilter))
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(nonWinFilter)
                                        .build())
                        .build();

        AdData adWithoutFilters =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(
                                        BUYER_1, AD_URI_PREFIX + "/ad_without_filters"))
                        .setMetadata("{\"result\":5}")
                        .build();

        CustomAudience.Builder sameCustomAudienceBuilder =
                new CustomAudience.Builder()
                        .setBuyer(BUYER_1)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1))
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                        BUYER_1))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(BUYER_1, BUYER_BIDDING_LOGIC_URI_PATH));

        CustomAudience customAudienceWithFrequencyCapFilters =
                sameCustomAudienceBuilder
                        .setName(keyToFilter + "_ca_with_filters")
                        .setAds(ImmutableList.of(adWithNonWinFrequencyCapFilter))
                        .build();

        CustomAudience customAudienceWithoutFrequencyCapFilters =
                sameCustomAudienceBuilder
                        .setName(keyToFilter + "_ca_without_filters")
                        .setAds(ImmutableList.of(adWithoutFilters))
                        .build();

        // Joining custom audiences, no result to do assertion on
        // Failures will generate an exception
        joinCustomAudience(customAudienceWithFrequencyCapFilters);
        joinCustomAudience(customAudienceWithoutFrequencyCapFilters);

        // Adding AdSelection override, no result to do assertion on
        // Failures will generate an exception
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceWithFiltersOverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceWithFrequencyCapFilters.getBuyer())
                        .setName(customAudienceWithFrequencyCapFilters.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceWithoutFiltersOverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceWithoutFrequencyCapFilters.getBuyer())
                        .setName(customAudienceWithoutFrequencyCapFilters.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on
        // Failures will generate an exception
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceWithFiltersOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceWithoutFiltersOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome1 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad with filters won
        assertThat(outcome1.getRenderUri())
                .isEqualTo(adWithNonWinFrequencyCapFilter.getRenderUri());

        ReportImpressionRequest reportImpressionRequest1 =
                new ReportImpressionRequest(outcome1.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Update ad counter histogram for an incorrect ad selection ID
        UpdateAdCounterHistogramRequest updateRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                outcome1.getAdSelectionId() + 1,
                                FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                                BUYER_1)
                        .build();
        mAdSelectionClient
                .updateAdCounterHistogram(updateRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection again and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome2 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad with filters won after updating the ad counter histogram for the
        // wrong ad selection ID
        assertThat(outcome2.getRenderUri())
                .isEqualTo(adWithNonWinFrequencyCapFilter.getRenderUri());

        ReportImpressionRequest reportImpressionRequest2 =
                new ReportImpressionRequest(outcome2.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFrequencyCapFiltering_WinEvent_FiltersAds() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(true);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        // Because frequency cap events are per-buyer and not per-CA, they cannot be cleared,
        // so to eliminate flakiness within a suite run, each test case will have its own unique key
        final int keyToFilter = sFrequencyCapKeyToFilter.incrementAndGet();

        FrequencyCapFilters winFilter =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(
                                ImmutableList.of(
                                        new KeyedFrequencyCap.Builder(
                                                        keyToFilter, 1, Duration.ofSeconds(10))
                                                .build()))
                        .build();

        AdData adWithWinFrequencyCapFilter =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad_with_filters"))
                        .setMetadata("{\"result\":10}")
                        .setAdCounterKeys(ImmutableSet.of(keyToFilter))
                        .setAdFilters(
                                new AdFilters.Builder().setFrequencyCapFilters(winFilter).build())
                        .build();

        AdData adWithoutFilters =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(
                                        BUYER_1, AD_URI_PREFIX + "/ad_without_filters"))
                        .setMetadata("{\"result\":5}")
                        .build();

        CustomAudience.Builder sameCustomAudienceBuilder =
                new CustomAudience.Builder()
                        .setBuyer(BUYER_1)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1))
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                        BUYER_1))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(BUYER_1, BUYER_BIDDING_LOGIC_URI_PATH));

        CustomAudience customAudienceWithFrequencyCapFilters =
                sameCustomAudienceBuilder
                        .setName(keyToFilter + "_ca_with_filters")
                        .setAds(ImmutableList.of(adWithWinFrequencyCapFilter))
                        .build();

        CustomAudience customAudienceWithoutFrequencyCapFilters =
                sameCustomAudienceBuilder
                        .setName(keyToFilter + "_ca_without_filters")
                        .setAds(ImmutableList.of(adWithoutFilters))
                        .build();

        // Joining custom audiences, no result to do assertion on
        // Failures will generate an exception
        joinCustomAudience(customAudienceWithFrequencyCapFilters);
        joinCustomAudience(customAudienceWithoutFrequencyCapFilters);

        // Adding AdSelection override, no result to do assertion on
        // Failures will generate an exception
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceWithFiltersOverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceWithFrequencyCapFilters.getBuyer())
                        .setName(customAudienceWithFrequencyCapFilters.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceWithoutFiltersOverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceWithoutFrequencyCapFilters.getBuyer())
                        .setName(customAudienceWithoutFrequencyCapFilters.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on
        // Failures will generate an exception
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceWithFiltersOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceWithoutFiltersOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome1 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad with filters won
        assertThat(outcome1.getRenderUri()).isEqualTo(adWithWinFrequencyCapFilter.getRenderUri());

        ReportImpressionRequest reportImpressionRequest1 =
                new ReportImpressionRequest(outcome1.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection again and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome2 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad without filters won after updating the ad counter histogram
        assertThat(outcome2.getRenderUri()).isEqualTo(adWithoutFilters.getRenderUri());

        ReportImpressionRequest reportImpressionRequest2 =
                new ReportImpressionRequest(outcome2.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFrequencyCapFiltering_WinEventDifferentKey_IsNotFiltered() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(true);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        // Because frequency cap events are per-buyer and not per-CA, they cannot be cleared,
        // so to eliminate flakiness within a suite run, each test case will have its own unique key
        final int keyToFilter = sFrequencyCapKeyToFilter.incrementAndGet();
        final int otherKeyNotFiltered = -1 * keyToFilter;

        FrequencyCapFilters winFilterWithKeyToFilter =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(
                                ImmutableList.of(
                                        new KeyedFrequencyCap.Builder(
                                                        keyToFilter, 1, Duration.ofSeconds(10))
                                                .build()))
                        .build();

        AdData adWithFrequencyCapKeyToFilter =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(
                                        BUYER_1, AD_URI_PREFIX + "/adWithFrequencyCapKeyToFilter"))
                        .setMetadata("{\"result\":10}")
                        .setAdCounterKeys(ImmutableSet.of(keyToFilter))
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(winFilterWithKeyToFilter)
                                        .build())
                        .build();

        FrequencyCapFilters winFilterWithOtherKey =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(
                                ImmutableList.of(
                                        new KeyedFrequencyCap.Builder(
                                                        otherKeyNotFiltered,
                                                        1,
                                                        Duration.ofSeconds(10))
                                                .build()))
                        .build();

        AdData adWithOtherFrequencyCapKey =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(
                                        BUYER_1, AD_URI_PREFIX + "/adWithOtherFrequencyCapKey"))
                        .setMetadata("{\"result\":5}")
                        .setAdCounterKeys(ImmutableSet.of(otherKeyNotFiltered))
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(winFilterWithOtherKey)
                                        .build())
                        .build();

        CustomAudience customAudienceWithFrequencyCapFilters =
                new CustomAudience.Builder()
                        .setBuyer(BUYER_1)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1))
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                        BUYER_1))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(BUYER_1, BUYER_BIDDING_LOGIC_URI_PATH))
                        .setName(keyToFilter + "_ca_with_filters")
                        .setAds(
                                ImmutableList.of(
                                        adWithFrequencyCapKeyToFilter, adWithOtherFrequencyCapKey))
                        .build();

        // Joining custom audiences, no result to do assertion on
        // Failures will generate an exception
        joinCustomAudience(customAudienceWithFrequencyCapFilters);

        // Adding AdSelection override, no result to do assertion on
        // Failures will generate an exception
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceWithFiltersOverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceWithFrequencyCapFilters.getBuyer())
                        .setName(customAudienceWithFrequencyCapFilters.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on
        // Failures will generate an exception
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceWithFiltersOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome1 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad with filters won
        assertThat(outcome1.getRenderUri()).isEqualTo(adWithFrequencyCapKeyToFilter.getRenderUri());

        ReportImpressionRequest reportImpressionRequest1 =
                new ReportImpressionRequest(outcome1.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection again and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome2 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad with a different key won after updating the ad counter histogram
        assertThat(outcome2.getRenderUri()).isEqualTo(adWithOtherFrequencyCapKey.getRenderUri());

        ReportImpressionRequest reportImpressionRequest2 =
                new ReportImpressionRequest(outcome2.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFrequencyCapFiltering_WinEventDifferentBuyer_IsNotFiltered() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(true);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        // Because frequency cap events are per-buyer and not per-CA, they cannot be cleared,
        // so to eliminate flakiness within a suite run, each test case will have its own unique key
        final int keyToFilter = sFrequencyCapKeyToFilter.incrementAndGet();

        FrequencyCapFilters winFilter =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(
                                ImmutableList.of(
                                        new KeyedFrequencyCap.Builder(
                                                        keyToFilter, 1, Duration.ofSeconds(10))
                                                .build()))
                        .build();

        AdData adForBuyer1ToFilter =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad_for_buyer1"))
                        .setMetadata("{\"result\":10}")
                        .setAdCounterKeys(ImmutableSet.of(keyToFilter))
                        .setAdFilters(
                                new AdFilters.Builder().setFrequencyCapFilters(winFilter).build())
                        .build();

        AdData adForBuyer2 =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad_for_buyer2"))
                        .setMetadata("{\"result\":5}")
                        .setAdCounterKeys(ImmutableSet.of(keyToFilter))
                        .setAdFilters(
                                new AdFilters.Builder().setFrequencyCapFilters(winFilter).build())
                        .build();

        CustomAudience.Builder sameCustomAudienceBuilder =
                new CustomAudience.Builder()
                        .setName(keyToFilter + "_ca_with_filters")
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);

        CustomAudience customAudienceForBuyer1 =
                sameCustomAudienceBuilder
                        .setBuyer(BUYER_1)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1))
                        .setTrustedBiddingData(
                                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                        BUYER_1))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(BUYER_1, BUYER_BIDDING_LOGIC_URI_PATH))
                        .setAds(ImmutableList.of(adForBuyer1ToFilter))
                        .build();

        CustomAudience customAudienceForBuyer2 =
                sameCustomAudienceBuilder
                        .setBuyer(BUYER_2)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2))
                        .setTrustedBiddingData(
                                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                        BUYER_2))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(BUYER_2, BUYER_BIDDING_LOGIC_URI_PATH))
                        .setAds(ImmutableList.of(adForBuyer2))
                        .build();

        // Joining custom audiences, no result to do assertion on
        // Failures will generate an exception
        joinCustomAudience(customAudienceForBuyer1);
        joinCustomAudience(customAudienceForBuyer2);

        // Adding AdSelection override, no result to do assertion on
        // Failures will generate an exception
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceForBuyer1OverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceForBuyer1.getBuyer())
                        .setName(customAudienceForBuyer1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceForBuyer2OverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudienceForBuyer2.getBuyer())
                        .setName(customAudienceForBuyer2.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on
        // Failures will generate an exception
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceForBuyer1OverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceForBuyer2OverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome1 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad with filters won
        assertThat(outcome1.getRenderUri()).isEqualTo(adForBuyer1ToFilter.getRenderUri());

        ReportImpressionRequest reportImpressionRequest1 =
                new ReportImpressionRequest(outcome1.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection again and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome2 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad without filters won after updating the ad counter histogram
        assertThat(outcome2.getRenderUri()).isEqualTo(adForBuyer2.getRenderUri());

        ReportImpressionRequest reportImpressionRequest2 =
                new ReportImpressionRequest(outcome2.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFrequencyCapFiltering_WinEventSameBuyerDifferentCustomAudience_IsNotFiltered()
            throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(true);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        // Because frequency cap events are per-buyer and not per-CA, they cannot be cleared,
        // so to eliminate flakiness within a suite run, each test case will have its own unique key
        final int keyToFilter = sFrequencyCapKeyToFilter.incrementAndGet();

        FrequencyCapFilters winFilter =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(
                                ImmutableList.of(
                                        new KeyedFrequencyCap.Builder(
                                                        keyToFilter, 1, Duration.ofSeconds(10))
                                                .build()))
                        .build();

        AdData adForCustomAudience1ToFilter =
                new AdData.Builder()
                        .setRenderUri(
                                CommonFixture.getUri(
                                        BUYER_1, AD_URI_PREFIX + "/ad_for_CA1_to_filter"))
                        .setMetadata("{\"result\":10}")
                        .setAdCounterKeys(ImmutableSet.of(keyToFilter))
                        .setAdFilters(
                                new AdFilters.Builder().setFrequencyCapFilters(winFilter).build())
                        .build();

        AdData adForCustomAudience2 =
                new AdData.Builder()
                        .setRenderUri(CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad_for_CA2"))
                        .setMetadata("{\"result\":5}")
                        .setAdCounterKeys(ImmutableSet.of(keyToFilter))
                        .setAdFilters(
                                new AdFilters.Builder().setFrequencyCapFilters(winFilter).build())
                        .build();

        CustomAudience.Builder sameCustomAudienceBuilder =
                new CustomAudience.Builder()
                        .setBuyer(BUYER_1)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1))
                        .setTrustedBiddingData(
                                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                        BUYER_1))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(BUYER_1, BUYER_BIDDING_LOGIC_URI_PATH))
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);

        CustomAudience customAudience1 =
                sameCustomAudienceBuilder
                        .setName(keyToFilter + "_ca_to_filter")
                        .setAds(ImmutableList.of(adForCustomAudience1ToFilter))
                        .build();

        CustomAudience customAudience2 =
                sameCustomAudienceBuilder
                        .setName(keyToFilter + "_ca_to_not_filter")
                        .setAds(ImmutableList.of(adForCustomAudience2))
                        .build();

        // Joining custom audiences, no result to do assertion on
        // Failures will generate an exception
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        // Adding AdSelection override, no result to do assertion on
        // Failures will generate an exception
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudience1OverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudience2OverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on
        // Failures will generate an exception
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudience1OverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudience2OverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome1 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad with filters won
        assertThat(outcome1.getRenderUri()).isEqualTo(adForCustomAudience1ToFilter.getRenderUri());

        ReportImpressionRequest reportImpressionRequest1 =
                new ReportImpressionRequest(outcome1.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection again and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome2 =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad without filters won after updating the ad counter histogram
        assertThat(outcome2.getRenderUri()).isEqualTo(adForCustomAudience2.getRenderUri());

        ReportImpressionRequest reportImpressionRequest2 =
                new ReportImpressionRequest(outcome2.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @FlakyTest(bugId = 298832350)
    public void testGetAdSelectionData_collectsCa_success()
            throws ExecutionException, InterruptedException, TimeoutException {
        // TODO(b/293022107): Add success tests when encryption key fetch can be done in CTS
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        PhFlagsFixture.overrideFledgeAdSelectionAuctionServerApisEnabled(false);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);
        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);
        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception.
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder().setSeller(SELLER).build();

        GetAdSelectionDataOutcome outcome =
                mAdSelectionClient
                        .getAdSelectionData(request)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(outcome.getAdSelectionId())
                .isNotEqualTo(AdSelectionOutcome.UNSET_AD_SELECTION_ID);
        assertThat(outcome.getAdSelectionData()).isNotNull();
    }

    @Test
    @FlakyTest(bugId = 302669752)
    public void testPersistAdSelectionData_adSelectionIdDoesntExist_failure()
            throws ExecutionException, InterruptedException, TimeoutException {
        // TODO(b/293022107): This test is currently using a placeholder ad selection id that cause
        //  it to fail. Add success tests when encryption key fetch can be done in CTS
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        PhFlagsFixture.overrideFledgeAdSelectionAuctionServerApisEnabled(false);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);
        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);
        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception.
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        GetAdSelectionDataRequest request1 =
                new GetAdSelectionDataRequest.Builder().setSeller(SELLER).build();

        GetAdSelectionDataOutcome outcome1 =
                mAdSelectionClient
                        .getAdSelectionData(request1)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(outcome1.getAdSelectionId())
                .isNotEqualTo(AdSelectionOutcome.UNSET_AD_SELECTION_ID);
        assertThat(outcome1.getAdSelectionData()).isNotNull();

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        CommonFixture.doSleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        String base64ServerResponse =
                "6fKmaBsiN0bnJZ+7Z4rSc7rkRS1RtzJjO+M9pfbcxum2A4iPcSEK4sRHhTQf4EUDd82HImUxGYrik6UEF6"
                        + "Ky/o4/scXj3wde0AKncnGeNtZGeL2qVdkXmVYqz1CRpsDst9XvMev+dYDHGl7iSfr1kZqNDF"
                        + "ij+q/lCgCVO2AqXNP8sdi0mhc6GpdfTftLebkIAV6oHXVgbPbb71WCv5VaipxJz3Uok/anZN"
                        + "zCXQQ/gfoPA1xleWZ7leNOTYJxMBfEASoIpsFmHfc1+VRUKYTemM0fMGA6zwWpyrKCJLec98"
                        + "D894+6zQhnxElwIa7JtimBMO2jFE3zwLG9/PrhURYANJknYFr0zsndnO4u3PA2lRP7IHRpFQ"
                        + "Vx7CfZaXJYZmat4igpELp1VsNq4zfhmakEyhDF08TYyac+FyD6WU4fv8y9RNlgLzx0EhUcsO"
                        + "7TVciCic0paM4QGUCmefSMoY2LfodfZPFptDD4at0Un7ptGwviKXOx2vUDy7jcGKOZ6AP167"
                        + "HeX9LhKHHWPsau8DyPZnU35Rf8gfa4FE9Mdem6vBj5pQNA41lHuz4YewQ/GvkRvtg0ZQCDK5"
                        + "7wGSz6ue1a/JUTD21xJjP9XOb7BJP2A8Vuob+K2r45t5sfDvHhYbTpTfOD7hA/zhLHf2Ra/+"
                        + "D36Tve3dmggMZIVnILIRUo/qksd54APEAAkHdpAo94SCpW37HBI1Q3NCgnxzn4ebnO86y87g"
                        + "Pe8Gxft1AhQb1p+gc=";
        PersistAdSelectionResultRequest request2 =
                new PersistAdSelectionResultRequest.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(outcome1.getAdSelectionId())
                        .setAdSelectionResult(BaseEncoding.base64().decode(base64ServerResponse))
                        .build();
        ThrowingRunnable runnable =
                () ->
                        mAdSelectionClient
                                .persistAdSelectionResult(request2)
                                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Throwable thrown = Assert.assertThrows(ExecutionException.class, runnable);
        assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);

        // TODO(b/293022107): Assert render uri when proper payload is generated or a call to
        //  auction servers is able to be made
        // assertThat(outcome2.getAdSelectionId()).isEqualTo(outcome1.getAdSelectionId());
        // assertThat(outcome2.getRenderUri()).isNotEqualTo(Uri.EMPTY);
    }

    private String insertJsWait(long waitTimeMs) {
        return "    const wait = (ms) => {\n"
                + "       var start = new Date().getTime();\n"
                + "       var end = start;\n"
                + "       while(end < start + ms) {\n"
                + "         end = new Date().getTime();\n"
                + "      }\n"
                + "    }\n"
                + String.format("    wait(\"%d\");\n", waitTimeMs);
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private CustomAudience createCustomAudience(final AdTechIdentifier buyer, List<Double> bids) {
        return createCustomAudience(
                buyer,
                bids,
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                CustomAudienceFixture.VALID_EXPIRATION_TIME);
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private CustomAudience createCustomAudienceWithAdCost(
            final AdTechIdentifier buyer, List<Double> bids, double adCost) {
        return createCustomAudienceWithAdCost(
                buyer,
                bids,
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                CustomAudienceFixture.VALID_EXPIRATION_TIME,
                adCost);
    }

    private CustomAudience createCustomAudience(
            final AdTechIdentifier buyer,
            List<Double> bids,
            Instant activationTime,
            Instant expirationTime) {
        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUri(buyer, AD_URI_PREFIX + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}")
                            .build());
        }

        return new CustomAudience.Builder()
                .setBuyer(buyer)
                .setName(buyer + CustomAudienceFixture.VALID_NAME)
                .setActivationTime(activationTime)
                .setExpirationTime(expirationTime)
                .setDailyUpdateUri(CustomAudienceFixture.getValidDailyUpdateUriByBuyer(buyer))
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(buyer))
                .setBiddingLogicUri(CommonFixture.getUri(buyer, BUYER_BIDDING_LOGIC_URI_PATH))
                .setAds(ads)
                .build();
    }

    private CustomAudience createCustomAudienceWithAdCost(
            final AdTechIdentifier buyer,
            List<Double> bids,
            Instant activationTime,
            Instant expirationTime,
            double adCost) {
        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUri(buyer, AD_URI_PREFIX + "/ad" + (i + 1)))
                            .setMetadata(
                                    "{\"result\":" + bids.get(i) + ",\"adCost\":" + adCost + "}")
                            .build());
        }

        return new CustomAudience.Builder()
                .setBuyer(buyer)
                .setName(buyer + CustomAudienceFixture.VALID_NAME)
                .setActivationTime(activationTime)
                .setExpirationTime(expirationTime)
                .setDailyUpdateUri(CustomAudienceFixture.getValidDailyUpdateUriByBuyer(buyer))
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(buyer))
                .setBiddingLogicUri(CommonFixture.getUri(buyer, BUYER_BIDDING_LOGIC_URI_PATH))
                .setAds(ads)
                .build();
    }

    private CustomAudience createCustomAudienceWithSubdomains(
            final AdTechIdentifier buyer, List<Double> bids) {
        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUriWithValidSubdomain(
                                            buyer.toString(), AD_URI_PREFIX + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}")
                            .build());
        }

        return CustomAudienceFixture.getValidBuilderWithSubdomainsForBuyer(buyer)
                .setAds(ads)
                .build();
    }

    /*
    // TODO(b/267712947) Unhisde Contextual Ad flow with App Install API changes
    private Map<AdTechIdentifier, ContextualAds> createContextualAds() {
        Map<AdTechIdentifier, ContextualAds> buyerContextualAds = new HashMap<>();

        AdTechIdentifier buyer1 = CommonFixture.VALID_BUYER_1;
        ContextualAds contextualAds1 =
                ContextualAdsFixture.generateContextualAds(
                                buyer1, ImmutableList.of(100.0, 200.0, 300.0))
                        .build();

        AdTechIdentifier buyer2 = CommonFixture.VALID_BUYER_2;
        ContextualAds contextualAds2 =
                ContextualAdsFixture.generateContextualAds(buyer2, ImmutableList.of(400.0, 500.0))
                        .build();

        buyerContextualAds.put(buyer1, contextualAds1);
        buyerContextualAds.put(buyer2, contextualAds2);

        return buyerContextualAds;
    }
    */

    private void joinCustomAudience(CustomAudience customAudience)
            throws ExecutionException, InterruptedException, TimeoutException {
        mCustomAudiencesToCleanUp.add(customAudience);
        Log.i(
                TAG,
                "Joining custom audience "
                        + customAudience.getName()
                        + " for buyer"
                        + customAudience.getBuyer());
        mCustomAudienceClient
                .joinCustomAudience(customAudience)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void leaveJoinedCustomAudiences()
            throws ExecutionException, InterruptedException, TimeoutException {
        try {
            for (CustomAudience customAudience : mCustomAudiencesToCleanUp) {
                Log.i(
                        TAG,
                        "Cleanup: leaving custom audience "
                                + customAudience.getName()
                                + " for buyer"
                                + customAudience.getBuyer());
                mCustomAudienceClient
                        .leaveCustomAudience(
                                customAudience.getBuyer(),
                                customAudience.getName())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            mCustomAudiencesToCleanUp.clear();
        }
    }

    private AdSelectionOutcome runAdSelectionAsPreSteps(
            double bid, AdTechIdentifier buyer, String buyerDecisionLogic) throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer = ImmutableList.of(bid);
        CustomAudience customAudience1 = createCustomAudience(buyer, bidsForBuyer);

        // Joining custom audiences
        joinCustomAudience(customAudience1);
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(buyerDecisionLogic)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER,
                                                SELLER_DECISION_LOGIC_URI_PATH)))
                        .setTrustedScoringSignalsUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER,
                                                SELLER_TRUSTED_SIGNAL_URI_PATH)))
                        .setCustomAudienceBuyers(Collections.singletonList(buyer))
                        .build();

        // Adding AdSelection override
        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(
                        new AddAdSelectionOverrideRequest(
                                adSelectionConfig,
                                DEFAULT_DECISION_LOGIC_JS,
                                TRUSTED_SCORING_SIGNALS))
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        return mAdSelectionClient
                .selectAds(adSelectionConfig)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
