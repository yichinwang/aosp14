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

package android.adservices.adselection;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.os.Parcel;

import org.junit.Test;

/** Unit tests for {@link SignedContextualAds} */
public class SignedContextualAdsTest {

    @Test
    public void testBuildContextualAdsSuccess() {
        SignedContextualAds contextualAds =
                new SignedContextualAds.Builder()
                        .setBuyer(SignedContextualAdsFixture.BUYER)
                        .setDecisionLogicUri(SignedContextualAdsFixture.DECISION_LOGIC_URI)
                        .setAdsWithBid(SignedContextualAdsFixture.ADS_WITH_BID)
                        .setSignature(SignedContextualAdsFixture.PLACEHOLDER_SIGNATURE)
                        .build();

        assertEquals(contextualAds.getBuyer(), SignedContextualAdsFixture.BUYER);
        assertEquals(
                contextualAds.getDecisionLogicUri(), SignedContextualAdsFixture.DECISION_LOGIC_URI);
        assertEquals(contextualAds.getAdsWithBid(), SignedContextualAdsFixture.ADS_WITH_BID);
        assertArrayEquals(
                contextualAds.getSignature(), SignedContextualAdsFixture.PLACEHOLDER_SIGNATURE);
    }

    @Test
    public void testParcelValidContextualAdsSuccess() {
        SignedContextualAds contextualAds = SignedContextualAdsFixture.aSignedContextualAd();

        Parcel p = Parcel.obtain();
        contextualAds.writeToParcel(p, 0);
        p.setDataPosition(0);
        SignedContextualAds fromParcel = SignedContextualAds.CREATOR.createFromParcel(p);

        assertEquals(contextualAds.getBuyer(), fromParcel.getBuyer());
        assertEquals(contextualAds.getDecisionLogicUri(), fromParcel.getDecisionLogicUri());
        assertEquals(contextualAds.getAdsWithBid(), fromParcel.getAdsWithBid());
        assertArrayEquals(contextualAds.getSignature(), fromParcel.getSignature());
    }

    @Test
    public void testSetContextualAdsNullBuyerFailure() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new SignedContextualAds.Builder().setBuyer(null);
                });
    }

    @Test
    public void testSetContextualAdsNullDecisionLogicUriFailure() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new SignedContextualAds.Builder().setDecisionLogicUri(null);
                });
    }

    @Test
    public void testSetContextualAdsNullAdWithBidFailure() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new SignedContextualAds.Builder().setAdsWithBid(null);
                });
    }

    @Test
    public void testSetContextualAdsNullSignatureFailure() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new SignedContextualAds.Builder().setSignature(null);
                });
    }

    @Test
    public void testParcelNullDestFailure() {
        SignedContextualAds contextualAds = SignedContextualAdsFixture.aSignedContextualAd();
        Parcel nullDest = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    contextualAds.writeToParcel(nullDest, 0);
                });
    }

    @Test
    public void testBuildContextualAdsUnsetBuyerFailure() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new SignedContextualAds.Builder()
                            .setDecisionLogicUri(SignedContextualAdsFixture.DECISION_LOGIC_URI)
                            .setAdsWithBid(SignedContextualAdsFixture.ADS_WITH_BID)
                            .setSignature(SignedContextualAdsFixture.PLACEHOLDER_SIGNATURE)
                            .build();
                });
    }

    @Test
    public void testBuildContextualAdsUnsetDecisionLogicUriFailure() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new SignedContextualAds.Builder()
                            .setBuyer(SignedContextualAdsFixture.BUYER)
                            .setAdsWithBid(SignedContextualAdsFixture.ADS_WITH_BID)
                            .setSignature(SignedContextualAdsFixture.PLACEHOLDER_SIGNATURE)
                            .build();
                });
    }

    @Test
    public void testBuildContextualAdsUnsetAdWithBidFailure() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new SignedContextualAds.Builder()
                            .setBuyer(SignedContextualAdsFixture.BUYER)
                            .setDecisionLogicUri(SignedContextualAdsFixture.DECISION_LOGIC_URI)
                            .setSignature(SignedContextualAdsFixture.PLACEHOLDER_SIGNATURE)
                            .build();
                });
    }

    @Test
    public void testBuildContextualAdsUnsetSignatureFailure() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new SignedContextualAds.Builder()
                            .setBuyer(SignedContextualAdsFixture.BUYER)
                            .setDecisionLogicUri(SignedContextualAdsFixture.DECISION_LOGIC_URI)
                            .setAdsWithBid(SignedContextualAdsFixture.ADS_WITH_BID)
                            .build();
                });
    }

    @Test
    public void testContextualAdsDescribeContents() {
        SignedContextualAds obj = SignedContextualAdsFixture.aSignedContextualAd();

        assertEquals(obj.describeContents(), 0);
    }

    @Test
    public void testContextualAdsHaveSameHashCode() {
        SignedContextualAds obj1 = SignedContextualAdsFixture.aSignedContextualAd();
        SignedContextualAds obj2 = SignedContextualAdsFixture.aSignedContextualAd();

        CommonFixture.assertHaveSameHashCode(obj1, obj2);
    }

    @Test
    public void testContextualAdsHaveDifferentHashCode() {
        SignedContextualAds obj1 = SignedContextualAdsFixture.aSignedContextualAd();
        SignedContextualAds obj2 =
                SignedContextualAdsFixture.aSignedContextualAdBuilder()
                        .setBuyer(SignedContextualAdsFixture.BUYER_2)
                        .build();
        SignedContextualAds obj3 =
                SignedContextualAdsFixture.aSignedContextualAdBuilder()
                        .setSignature(new byte[] {1, 2, 3})
                        .build();

        CommonFixture.assertDifferentHashCode(obj1, obj2, obj3);
    }
}
