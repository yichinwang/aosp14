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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.os.Parcel;

import org.junit.Test;

public class GetAdSelectionDataInputTest {
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final String ANOTHER_CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME_1;

    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER;
    private static final AdTechIdentifier ANOTHER_SELLER = AdSelectionConfigFixture.SELLER_1;

    @Test
    public void testBuildGetAdSelectionDataInput() {
        GetAdSelectionDataInput getAdSelectionDataInput =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        assertThat(getAdSelectionDataInput.getSeller()).isEqualTo(SELLER);
        assertThat(getAdSelectionDataInput.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
    }

    @Test
    public void testParcelGetAdSelectionDataInput() {
        GetAdSelectionDataInput getAdSelectionDataInput =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        Parcel p = Parcel.obtain();
        getAdSelectionDataInput.writeToParcel(p, 0);
        p.setDataPosition(0);
        GetAdSelectionDataInput fromParcel = GetAdSelectionDataInput.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getSeller()).isEqualTo(SELLER);
        assertThat(fromParcel.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
    }

    @Test
    public void testParcelGetAdSelectionDataInputWithNullValues() {
        GetAdSelectionDataInput getAdSelectionDataInput =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(null)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        Parcel p = Parcel.obtain();
        getAdSelectionDataInput.writeToParcel(p, 0);
        p.setDataPosition(0);
        GetAdSelectionDataInput fromParcel = GetAdSelectionDataInput.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getSeller()).isNull();
        assertThat(fromParcel.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
    }

    @Test
    public void testGetAdSelectionDataInputWithSameValuesAreEqual() {
        GetAdSelectionDataInput obj1 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataInput obj2 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        assertThat(obj1).isEqualTo(obj2);
    }

    @Test
    public void testGetAdSelectionDataInputWithDifferentValuesAreNotEqual() {
        GetAdSelectionDataInput obj1 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataInput obj2 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(ANOTHER_CALLER_PACKAGE_NAME)
                        .build();

        assertThat(obj1).isNotEqualTo(obj2);
    }

    @Test
    public void testGetAdSelectionDataInputDescribeContents() {
        GetAdSelectionDataInput obj =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        assertEquals(0, obj.describeContents());
    }

    @Test
    public void testEqualGetAdSelectionDataInputsHaveSameHashCode() {
        GetAdSelectionDataInput obj1 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        GetAdSelectionDataInput obj2 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        CommonFixture.assertHaveSameHashCode(obj1, obj2);
    }

    @Test
    public void testNotEqualGetAdSelectionDataInputsHaveDifferentHashCodes() {
        GetAdSelectionDataInput obj1 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        GetAdSelectionDataInput obj2 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(ANOTHER_CALLER_PACKAGE_NAME)
                        .build();
        GetAdSelectionDataInput obj3 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(ANOTHER_SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        CommonFixture.assertDifferentHashCode(obj1, obj2, obj3);
    }
}
