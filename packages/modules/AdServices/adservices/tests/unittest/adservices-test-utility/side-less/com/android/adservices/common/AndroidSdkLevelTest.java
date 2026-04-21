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
package com.android.adservices.common;

import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.ANY;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.R;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.S;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.S2;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.T;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.U;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

public final class AndroidSdkLevelTest {

    @Rule public final Expect expect = Expect.create();

    @Test
    public void testFactoryMethod() {
        AndroidSdkLevel for30 = AndroidSdkLevel.forLevel(30);
        expect.withMessage("level 30").that(for30).isSameInstanceAs(R);

        AndroidSdkLevel for31 = AndroidSdkLevel.forLevel(31);
        expect.withMessage("level 31").that(for31).isSameInstanceAs(S);

        AndroidSdkLevel for32 = AndroidSdkLevel.forLevel(32);
        expect.withMessage("level 32").that(for32).isSameInstanceAs(S2);

        AndroidSdkLevel for33 = AndroidSdkLevel.forLevel(33);
        expect.withMessage("level 33").that(for33).isSameInstanceAs(T);

        AndroidSdkLevel for34 = AndroidSdkLevel.forLevel(34);
        expect.withMessage("level 34").that(for34).isSameInstanceAs(U);

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> AndroidSdkLevel.forLevel(29));
        expect.that(e).hasMessageThat().contains("29");
    }

    @Test
    public void testGetLevel() {
        expect.withMessage("level of ANY").that(ANY.getLevel()).isLessThan(R.getLevel());
        expect.withMessage("level of R").that(R.getLevel()).isEqualTo(30);
        expect.withMessage("level of S").that(S.getLevel()).isEqualTo(31);
        expect.withMessage("level of S2").that(S2.getLevel()).isEqualTo(32);
        expect.withMessage("level of T").that(T.getLevel()).isEqualTo(33);
        expect.withMessage("level of U").that(U.getLevel()).isEqualTo(34);
    }

    @Test
    public void testAtLeast() {
        expect.withMessage("ANY.isAtLeast(ANY)").that(ANY.isAtLeast(ANY)).isTrue();
        expect.withMessage("ANY.isAtLeast(R)").that(ANY.isAtLeast(R)).isFalse();
        expect.withMessage("ANY.isAtLeast(S)").that(ANY.isAtLeast(S)).isFalse();
        expect.withMessage("ANY.isAtLeast(S2)").that(ANY.isAtLeast(S2)).isFalse();
        expect.withMessage("ANY.isAtLeast(T)").that(ANY.isAtLeast(T)).isFalse();
        expect.withMessage("ANY.isAtLeast(U)").that(ANY.isAtLeast(U)).isFalse();

        expect.withMessage("R.isAtLeast(ANY)").that(R.isAtLeast(ANY)).isTrue();
        expect.withMessage("R.isAtLeast(R)").that(R.isAtLeast(R)).isTrue();
        expect.withMessage("R.isAtLeast(S)").that(R.isAtLeast(S)).isFalse();
        expect.withMessage("R.isAtLeast(S2)").that(R.isAtLeast(S2)).isFalse();
        expect.withMessage("R.isAtLeast(T)").that(R.isAtLeast(T)).isFalse();
        expect.withMessage("R.isAtLeast(U)").that(R.isAtLeast(U)).isFalse();

        expect.withMessage("S.isAtLeast(ANY)").that(S.isAtLeast(ANY)).isTrue();
        expect.withMessage("S.isAtLeast(R)").that(S.isAtLeast(R)).isTrue();
        expect.withMessage("S.isAtLeast(S)").that(S.isAtLeast(S)).isTrue();
        expect.withMessage("S.isAtLeast(S2)").that(S.isAtLeast(S2)).isFalse();
        expect.withMessage("S.isAtLeast(T)").that(S.isAtLeast(T)).isFalse();
        expect.withMessage("S.isAtLeast(U)").that(S.isAtLeast(U)).isFalse();

        expect.withMessage("T.isAtLeast(ANY)").that(T.isAtLeast(ANY)).isTrue();
        expect.withMessage("T.isAtLeast(R)").that(T.isAtLeast(R)).isTrue();
        expect.withMessage("T.isAtLeast(S)").that(T.isAtLeast(S)).isTrue();
        expect.withMessage("T.isAtLeast(S2)").that(T.isAtLeast(S2)).isTrue();
        expect.withMessage("T.isAtLeast(T)").that(T.isAtLeast(T)).isTrue();
        expect.withMessage("T.isAtLeast(U)").that(T.isAtLeast(U)).isFalse();

        expect.withMessage("U.isAtLeast(ANY)").that(U.isAtLeast(ANY)).isTrue();
        expect.withMessage("U.isAtLeast(R)").that(U.isAtLeast(R)).isTrue();
        expect.withMessage("U.isAtLeast(S)").that(U.isAtLeast(S)).isTrue();
        expect.withMessage("U.isAtLeast(S2)").that(U.isAtLeast(S2)).isTrue();
        expect.withMessage("U.isAtLeast(T)").that(U.isAtLeast(T)).isTrue();
        expect.withMessage("U.isAtLeast(U)").that(U.isAtLeast(U)).isTrue();
    }
}
