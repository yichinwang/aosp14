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

package com.android.adservices.service.customaudience;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.customaudience.DBCustomAudience;

import org.json.JSONException;
import org.json.JSONObject;

public class FetchCustomAudienceFixture {

    public static String getFullSuccessfulJsonResponseString(AdTechIdentifier buyer)
            throws JSONException {
        return CustomAudienceBlobFixture.asJSONObjectString(
                CustomAudienceFixture.VALID_OWNER,
                buyer,
                CustomAudienceFixture.VALID_NAME,
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                CustomAudienceFixture.VALID_EXPIRATION_TIME,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(buyer),
                CustomAudienceFixture.getValidBiddingLogicUriByBuyer(buyer),
                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString(),
                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(buyer).build(),
                DBAdDataFixture.getValidDbAdDataListByBuyer(buyer));
    }

    public static JSONObject getFullSuccessfulJsonResponse(AdTechIdentifier buyer)
            throws JSONException {
        return CustomAudienceBlobFixture.asJSONObject(
                CustomAudienceFixture.VALID_OWNER,
                buyer,
                CustomAudienceFixture.VALID_NAME,
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                CustomAudienceFixture.VALID_EXPIRATION_TIME,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(buyer),
                CustomAudienceFixture.getValidBiddingLogicUriByBuyer(buyer),
                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString(),
                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(buyer).build(),
                DBAdDataFixture.getValidDbAdDataListByBuyer(buyer),
                false);
    }

    public static DBCustomAudience getFullSuccessfulDBCustomAudience() throws JSONException {
        return new DBCustomAudience.Builder()
                .setBuyer(AdTechIdentifier.fromString("localhost"))
                .setOwner(CustomAudienceFixture.VALID_OWNER)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setBiddingLogicUri(
                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                AdTechIdentifier.fromString("localhost")))
                .setTrustedBiddingData(
                        DBTrustedBiddingDataFixture.getValidBuilderByBuyer(
                                        AdTechIdentifier.fromString("localhost"))
                                .build())
                .setAds(
                        DBAdDataFixture.getValidDbAdDataListByBuyer(
                                AdTechIdentifier.fromString("localhost")))
                .build();
    }
}
