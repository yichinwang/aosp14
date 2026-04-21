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

import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkRange.forAtLeast;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkRange.forAtMost;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkRange.forExactly;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkRange.forRange;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkRange.forAnyLevel;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkRange.merge;

import static org.junit.Assert.assertThrows;

import static java.lang.Integer.MIN_VALUE;
import static java.lang.Integer.MAX_VALUE;

import com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkRange;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;

public final class AndroidSdkRangeTest {

    private static final Logger sLogger =
            new Logger(StandardStreamsLogger.getInstance(), AndroidSdkRangeTest.class);

    @Rule public final Expect expect = Expect.create();

    @Test
    public void testInvalidRanges() {
        assertThrows(IllegalArgumentException.class, () -> forRange(33, 32));
        assertThrows(IllegalArgumentException.class, () -> forAtLeast(MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> forAtMost(MIN_VALUE));
    }

    @Test
    public void testIsInRange() {
        AndroidSdkRange atLeast33 = forAtLeast(33);
        expectInRange(atLeast33, 33);
        expectInRange(atLeast33, MAX_VALUE - 1);
        expectInRange(atLeast33, MAX_VALUE);
        expectNotInRange(atLeast33, MIN_VALUE);
        expectNotInRange(atLeast33, MIN_VALUE + 1);
        expectNotInRange(atLeast33, 32);

        AndroidSdkRange atMost33 = forAtMost(33);
        expectInRange(atMost33, 32);
        expectInRange(atMost33, 33);
        expectNotInRange(atMost33, 35);
        expectInRange(atMost33, MIN_VALUE);
        expectInRange(atMost33, MIN_VALUE + 1);
        expectNotInRange(atMost33, MAX_VALUE - 1);
        expectNotInRange(atMost33, MAX_VALUE);

        AndroidSdkRange exactly33Indirectly = forRange(33, 33);
        expectNotInRange(exactly33Indirectly, 32);
        expectInRange(exactly33Indirectly, 33);
        expectNotInRange(exactly33Indirectly, 34);
        expectNotInRange(exactly33Indirectly, MIN_VALUE);
        expectNotInRange(exactly33Indirectly, MIN_VALUE + 1);
        expectNotInRange(exactly33Indirectly, MAX_VALUE - 1);
        expectNotInRange(exactly33Indirectly, MAX_VALUE);

        AndroidSdkRange exactly33Directly = forExactly(33);
        expectNotInRange(exactly33Directly, 32);
        expectInRange(exactly33Directly, 33);
        expectNotInRange(exactly33Directly, 34);
        expectNotInRange(exactly33Directly, MIN_VALUE);
        expectNotInRange(exactly33Directly, MIN_VALUE + 1);
        expectNotInRange(exactly33Directly, MAX_VALUE - 1);
        expectNotInRange(exactly33Directly, MAX_VALUE);

        AndroidSdkRange forAnyLevel = forAnyLevel();
        expectInRange(forAnyLevel, MIN_VALUE);
        expectInRange(forAnyLevel, MIN_VALUE + 1);
        expectInRange(forAnyLevel, 33);
        expectInRange(forAnyLevel, MAX_VALUE - 1);
        expectInRange(forAnyLevel, MAX_VALUE);
    }

    @Test
    public void testEqualsHashCode() {
        AndroidSdkRange atLeast33 = forAtLeast(33);
        AndroidSdkRange anotherAtLeast33 = forAtLeast(33);
        expectEquals(atLeast33, anotherAtLeast33);
        expectNotEqualsNull(atLeast33);

        AndroidSdkRange atLeast34 = forAtLeast(34);
        AndroidSdkRange anotherAtLeast34 = forAtLeast(34);
        expectEquals(atLeast34, anotherAtLeast34);
        expectNotEquals(atLeast33, null, atLeast34, anotherAtLeast34);

        AndroidSdkRange atMost33 = forAtMost(33);
        AndroidSdkRange anotherAtMost33 = forAtMost(33);
        expectEquals(atMost33, anotherAtMost33);
        expectNotEquals(atMost33, null, atLeast33, atLeast34, anotherAtLeast33, anotherAtLeast34);
        expectNotEqualsNull(atMost33);

        AndroidSdkRange atMost34 = forAtMost(34);
        AndroidSdkRange anotherAtMost34 = forAtMost(34);
        expectEquals(atMost34, anotherAtMost34);
        expectNotEquals(
                atMost34,
                null,
                atMost33,
                atLeast33,
                atLeast34,
                anotherAtLeast33,
                anotherAtLeast34,
                anotherAtMost33);

        AndroidSdkRange only33Exactly = forExactly(33);
        AndroidSdkRange anotherOnly33Exactly = forRange(33, 33);
        expectEquals(only33Exactly, anotherOnly33Exactly);

        AndroidSdkRange only33Range = forRange(33, 33);
        AndroidSdkRange anotherOnly33Range = forRange(33, 33);
        expectEquals(only33Range, anotherOnly33Range);

        expectEquals(only33Exactly, only33Range);

        expectNotEquals(
                only33Exactly,
                null,
                atMost33,
                atMost34,
                atLeast33,
                atLeast34,
                anotherAtLeast33,
                anotherAtLeast34,
                anotherAtMost33);
        expectNotEqualsNull(only33Exactly);

        AndroidSdkRange forAnyLevel = forAnyLevel();
        AndroidSdkRange anotherForAnyLevel = forAnyLevel();
        expectEquals(forAnyLevel, anotherForAnyLevel);
        expectNotEquals(
                forAnyLevel,
                null,
                only33Exactly,
                only33Range,
                atMost33,
                atMost34,
                atLeast33,
                atLeast34,
                anotherAtLeast33,
                anotherAtLeast34,
                anotherAtMost33);
        expectNotEqualsNull(forAnyLevel);
    }

    @Test
    public void testMerge_nullValues() {
        assertThrows(NullPointerException.class, () -> merge((AndroidSdkRange[]) null));

        assertThrows(IllegalArgumentException.class, () -> merge(new AndroidSdkRange[0]));
        assertThrows(IllegalArgumentException.class, () -> merge(new AndroidSdkRange[] {null}));
        assertThrows(
                IllegalArgumentException.class, () -> merge(null, forAtLeast(32), forAtLeast(33)));
        assertThrows(
                IllegalArgumentException.class, () -> merge(forAtLeast(32), null, forAtLeast(33)));
        assertThrows(
                IllegalArgumentException.class, () -> merge(forAtLeast(32), forAtLeast(33), null));
    }

    @Test
    public void testMergeRange_valid() {
        mergeValidAndAssertResult(forAtLeast(32), forAtLeast(32));
        mergeValidAndAssertResult(forAtLeast(32), forAtLeast(31), forAtLeast(32));
        mergeValidAndAssertResult(forAtLeast(32), forAtLeast(32), forAnyLevel());

        mergeValidAndAssertResult(forAtMost(32), forAtMost(32));
        mergeValidAndAssertResult(forAtMost(32), forAtMost(32), forAtMost(33));
        mergeValidAndAssertResult(forAtMost(32), forAtMost(32), forAnyLevel());

        mergeValidAndAssertResult(forExactly(32), forAtLeast(32), forAtMost(32));
        mergeValidAndAssertResult(forExactly(32), forAtLeast(32), forExactly(32), forAtMost(32));
        mergeValidAndAssertResult(
                forExactly(32), forAtLeast(32), forExactly(32), forRange(32, 32), forAtMost(32));

        mergeValidAndAssertResult(forRange(32, 34), forAtLeast(32), forAtMost(34));

        // Subset
        mergeValidAndAssertResult(forRange(33, 34), forRange(32, 35), forRange(33, 34));
        mergeValidAndAssertResult(forRange(33, 34), forRange(33, 35), forRange(33, 34));
        mergeValidAndAssertResult(forRange(34, 35), forRange(32, 35), forRange(34, 35));

        // Intesection
        mergeValidAndAssertResult(forExactly(33), forRange(32, 33), forRange(33, 34));
        mergeValidAndAssertResult(forRange(33, 34), forRange(32, 34), forRange(33, 35));

        // Merge forAnyLevel() with something else - should result into something else
        mergeValidAndAssertResult(forAnyLevel(), forAnyLevel());
        mergeValidAndAssertResult(forAnyLevel(), forAnyLevel(), forAnyLevel());
        mergeValidAndAssertResult(forAtLeast(32), forAnyLevel(), forAtLeast(32));
        mergeValidAndAssertResult(forAtMost(32), forAnyLevel(), forAtMost(32));
        mergeValidAndAssertResult(forRange(32, 34), forAnyLevel(), forRange(32, 34));
        mergeValidAndAssertResult(forExactly(32), forAnyLevel(), forExactly(32));
    }

    @Test
    public void testMergeRange_invalid() {
        mergeInvalidAndAssertThrows(forAtLeast(32), forAtMost(31));
        mergeInvalidAndAssertThrows(forExactly(32), forAtLeast(33));
        mergeInvalidAndAssertThrows(forExactly(32), forAtMost(31));
        mergeInvalidAndAssertThrows(forRange(32, 33), forRange(34, 35));
    }

    private void expectInRange(AndroidSdkRange range, int level) {
        expect.withMessage("Level %s is in range of %s", level, range)
                .that(range.isInRange(level))
                .isTrue();
    }

    private void expectNotInRange(AndroidSdkRange range, int level) {
        expect.withMessage("Level %s is in range of %s", level, range)
                .that(range.isInRange(level))
                .isFalse();
    }

    private void expectEquals(AndroidSdkRange range1, AndroidSdkRange range2) {
        expect.withMessage("%s.equals(%s)", range1, range2).that(range1).isEqualTo(range2);
        expect.withMessage("%s.equals(%s)", range2, range1).that(range2).isEqualTo(range1);
        expect.withMessage("hashcode of %s and %s", range1, range2)
                .that(range1.hashCode())
                .isEqualTo(range2.hashCode());
    }

    private void expectNotEquals(AndroidSdkRange range1, AndroidSdkRange... otherRanges) {
        for (AndroidSdkRange range2 : otherRanges) {
            expect.withMessage("%s.equals(%s)", range1, range2).that(range1).isNotEqualTo(range2);
            expect.withMessage("%s.equals(%s)", range2, range1).that(range2).isNotEqualTo(range1);
            if (range2 != null) {
                expect.withMessage("hashcode of %s and %s", range1, range2)
                        .that(range1.hashCode())
                        .isNotEqualTo(range2.hashCode());
            }
        }
    }

    private void expectNotEqualsNull(AndroidSdkRange range) {
        expect.withMessage("%s.equals(null)", range).that(range).isNotEqualTo(null);
    }

    private void mergeValidAndAssertResult(
            AndroidSdkRange expectedRange, AndroidSdkRange... ranges) {
        sLogger.d(
                "mergeAndAssert(): expectedRange=%s, ranges=%s",
                expectedRange, Arrays.toString(ranges));
        AndroidSdkRange actualRange = merge(ranges);
        expect.withMessage("merge(%s)", Arrays.toString(ranges)).that(actualRange).isNotNull();
        expect.withMessage("merge(%s)", Arrays.toString(ranges))
                .that(actualRange)
                .isEqualTo(expectedRange);
    }

    private void mergeInvalidAndAssertThrows(AndroidSdkRange... ranges) {
        sLogger.d("mergeInvalidAndAssertThrows(): ranges=%s", Arrays.toString(ranges));
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> merge(ranges));
        sLogger.d("mergeInvalidAndAssertThrows(): exception=%s", exception.getMessage());
    }
}
