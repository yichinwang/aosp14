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
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.DEFAULT_REASON;
import static com.android.adservices.common.TestAnnotations.newAnnotationForAtLeast;
import static com.android.adservices.common.TestAnnotations.newAnnotationForLessThanT;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel;
import com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkRange;
import com.android.adservices.common.AbstractSdkLevelSupportedRule.RequiredRange;
import com.android.adservices.common.Logger.RealLogger;

import com.google.common.truth.Expect;
import com.google.common.truth.StringSubject;

import org.junit.AssumptionViolatedException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Objects;

// TODO(b/295321663): provide host-side implementation
/**
 * Test case for {@link AbstractSdkLevelSupportedRule} implementations.
 *
 * <p>By default, it uses a {@link FakeSdkLevelSupportedRule bogus rule} so it can be run by IDEs,\
 * but subclasses should implement {@link #newRule(AndroidSdkLevel, AndroidSdkLevel)} and {@link
 * #newRuleForDeviceLevelAndRuleAtLeastLevel(AndroidSdkLevel)}.
 */
public class AbstractSdkLevelSupportedRuleTest {

    // Not a real test (i.e., it doesn't exist on this class), but it's passed to Description
    private static final String TEST_METHOD_BEING_EXECUTED = "testAmI..OrNot";

    private static final String REASON = "Because I said so";
    private static final String ANOTHER_REASON = "To get to the other side.";

    private static final String REASON_R = "ARRRRRGH";
    private static final String REASON_S = "'SUP?";
    private static final String REASON_S2 = "2nd time is a charm";
    private static final String REASON_T = "TBone";
    private static final String REASON_U = "Y U?";
    private static final String REASON_LESS_THAN_T = "LESS THAN T, Y NOT AT MOST S2?";

    private final SimpleStatement mBaseStatement = new SimpleStatement();

    protected final Logger mLog;

    @Rule public final Expect expect = Expect.create();

    public AbstractSdkLevelSupportedRuleTest() {
        this(StandardStreamsLogger.getInstance());
    }

    protected AbstractSdkLevelSupportedRuleTest(RealLogger realLogger) {
        mLog = new Logger(Objects.requireNonNull(realLogger), getClass());
    }

    // NOTE: the testRuleIsAtLeastMethods... refers to the device SDK, not the rule's

    @Test
    public void testRuleIsAtLeastMethods_deviceIsR() throws Exception {
        var rule = newRule(/* ruleLevel= */ ANY, /* deviceLevel= */ R);

        expect.withMessage("rule.atLeastR()").that(rule.isAtLeastR()).isTrue();
        expect.withMessage("rule.atLeastS()").that(rule.isAtLeastS()).isFalse();
        expect.withMessage("rule.atLeastS2()").that(rule.isAtLeastS2()).isFalse();
        expect.withMessage("rule.atLeastT()").that(rule.isAtLeastT()).isFalse();
        expect.withMessage("rule.atLeastU()").that(rule.isAtLeastU()).isFalse();
    }

    @Test
    public void testRuleIsAtLeastMethods_deviceIsS() throws Exception {
        var rule = newRule(/* ruleLevel= */ ANY, /* deviceLevel= */ S);

        expect.withMessage("rule.atLeastR()").that(rule.isAtLeastR()).isTrue();
        expect.withMessage("rule.atLeastS()").that(rule.isAtLeastS()).isTrue();
        expect.withMessage("rule.atLeastS2()").that(rule.isAtLeastS2()).isFalse();
        expect.withMessage("rule.atLeastT()").that(rule.isAtLeastT()).isFalse();
        expect.withMessage("rule.atLeastU()").that(rule.isAtLeastU()).isFalse();
    }

    @Test
    public void testRuleIsAtLeastMethods_deviceIsS2() throws Exception {
        var rule = newRule(/* ruleLevel= */ ANY, /* deviceLevel= */ S2);

        expect.withMessage("rule.atLeastR()").that(rule.isAtLeastR()).isTrue();
        expect.withMessage("rule.atLeastS()").that(rule.isAtLeastS()).isTrue();
        expect.withMessage("rule.atLeastS2()").that(rule.isAtLeastS2()).isTrue();
        expect.withMessage("rule.atLeastT()").that(rule.isAtLeastT()).isFalse();
        expect.withMessage("rule.atLeastU()").that(rule.isAtLeastU()).isFalse();
    }

    @Test
    public void testRuleIsAtLeastMethods_deviceIsT() throws Exception {
        var rule = newRule(/* ruleLevel= */ ANY, /* deviceLevel= */ T);

        expect.withMessage("rule.atLeastR()").that(rule.isAtLeastR()).isTrue();
        expect.withMessage("rule.atLeastS()").that(rule.isAtLeastS()).isTrue();
        expect.withMessage("rule.atLeastS2()").that(rule.isAtLeastS2()).isTrue();
        expect.withMessage("rule.atLeastT()").that(rule.isAtLeastT()).isTrue();
        expect.withMessage("rule.atLeastU()").that(rule.isAtLeastU()).isFalse();
    }

    @Test
    public void testRuleIsAtLeastMethods_deviceIsU() throws Exception {
        var rule = newRule(/* ruleLevel= */ ANY, /* deviceLevel= */ U);

        expect.withMessage("rule.atLeastR()").that(rule.isAtLeastR()).isTrue();
        expect.withMessage("rule.atLeastS()").that(rule.isAtLeastS()).isTrue();
        expect.withMessage("rule.atLeastS2()").that(rule.isAtLeastS2()).isTrue();
        expect.withMessage("rule.atLeastT()").that(rule.isAtLeastT()).isTrue();
        expect.withMessage("rule.atLeastU()").that(rule.isAtLeastU()).isTrue();
    }

    @Test
    public void testPassesWhenDeviceLevelIsInRange() throws Throwable {
        var ruleRange = AndroidSdkRange.forRange(R.getLevel(), U.getLevel());
        var deviceLevel = T;
        var rule = newRule(ruleRange, deviceLevel);
        Description testMethod = newTestMethod();

        try {
            rule.apply(mBaseStatement, testMethod).evaluate();
        } catch (AssumptionViolatedException e) {
            throw new Exception("test should not throw", e);
        }

        mBaseStatement.assertEvaluated();
    }

    @Test
    public void testSkipsWhenDeviceLevelIsOutsideRange() throws Throwable {
        var ruleRange = AndroidSdkRange.forRange(R.getLevel(), S.getLevel());
        var deviceLevel = T;
        var rule = newRule(ruleRange, deviceLevel);

        Description testMethod = newTestMethod();

        AssumptionViolatedException e =
                assertThrows(
                        AssumptionViolatedException.class,
                        () -> rule.apply(mBaseStatement, testMethod).evaluate());

        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .contains(ruleRange.toString());

        mBaseStatement.assertNotEvaluated();
    }

    @Test
    public void testGetRequiredRange_unannotatedTest() throws Throwable {
        assertGetRequiredRangeForUnannotatedTest(/* ruleRange= */ AndroidSdkRange.forAnyLevel());
        assertGetRequiredRangeForUnannotatedTest(/* ruleRange= */ AndroidSdkRange.forAtLeast(42));
        assertGetRequiredRangeForUnannotatedTest(/* ruleRange= */ AndroidSdkRange.forAtMost(42));
        assertGetRequiredRangeForUnannotatedTest(/* ruleRange= */ AndroidSdkRange.forExactly(42));
        assertGetRequiredRangeForUnannotatedTest(
                /* ruleRange= */ AndroidSdkRange.forRange(42, 108));
    }

    private void assertGetRequiredRangeForUnannotatedTest(AndroidSdkRange ruleRange) {
        var rule = new FakeSdkLevelSupportedRule(ruleRange);
        var expectedRequiredRange = new RequiredRange(ruleRange, DEFAULT_REASON);

        assertGetRequiredRange(expectedRequiredRange, rule, newTestMethod());
    }

    private void assertGetRequiredRange(
            RequiredRange expectedRequiredRange,
            AbstractSdkLevelSupportedRule rule,
            Description testMethod) {
        RequiredRange actualRequiredRange = rule.getRequiredRange(testMethod);
        mLog.v(
                "assertGetRequiredRange(): rule=%s, expectedRequiredRange=%s,"
                        + " actualRequiredRange=%s, testMethod=%s",
                rule, expectedRequiredRange, actualRequiredRange, testMethod);

        expect.withMessage("getRequiredRange()")
                .that(actualRequiredRange)
                .isEqualTo(expectedRequiredRange);
        expect.withMessage("getRequiredRange().reason")
                .that(actualRequiredRange.reasons)
                .containsExactlyElementsIn(expectedRequiredRange.reasons);
    }

    @Test
    public void testGetRequiredRange_annotatedTest() throws Throwable {
        // Test with any-range constructor first
        var ruleForAnyLevel = AndroidSdkRange.forAnyLevel();
        assertGetRequiredRangeForAnnotatedTest(
                AndroidSdkRange.forAtLeast(R.getLevel()),
                REASON,
                ruleForAnyLevel,
                newAnnotationForAtLeast(R, REASON));
        assertGetRequiredRangeForAnnotatedTest(
                AndroidSdkRange.forAtLeast(S.getLevel()),
                REASON,
                ruleForAnyLevel,
                newAnnotationForAtLeast(S, REASON));
        assertGetRequiredRangeForAnnotatedTest(
                AndroidSdkRange.forAtLeast(S2.getLevel()),
                REASON,
                ruleForAnyLevel,
                newAnnotationForAtLeast(S2, REASON));
        assertGetRequiredRangeForAnnotatedTest(
                AndroidSdkRange.forAtLeast(T.getLevel()),
                REASON,
                ruleForAnyLevel,
                newAnnotationForAtLeast(T, REASON));
        assertGetRequiredRangeForAnnotatedTest(
                AndroidSdkRange.forAtLeast(U.getLevel()),
                REASON,
                ruleForAnyLevel,
                newAnnotationForAtLeast(U, REASON));
        // TODO(b/295269584): replace lessThatT test for required range annotation
        assertGetRequiredRangeForAnnotatedTest(
                AndroidSdkRange.forAtMost(S2.getLevel()),
                REASON,
                ruleForAnyLevel,
                newAnnotationForLessThanT(REASON));

        // Then for well-defined range (min and max)
        var ruleForClosedRange = AndroidSdkRange.forRange(R.getLevel(), U.getLevel());
        assertGetRequiredRangeForAnnotatedTest(
                AndroidSdkRange.forRange(R.getLevel(), U.getLevel()),
                REASON,
                ruleForClosedRange,
                newAnnotationForAtLeast(R, REASON));
        assertGetRequiredRangeForAnnotatedTest(
                AndroidSdkRange.forRange(S.getLevel(), U.getLevel()),
                REASON,
                ruleForClosedRange,
                newAnnotationForAtLeast(S, REASON));
        assertGetRequiredRangeForAnnotatedTest(
                AndroidSdkRange.forRange(S2.getLevel(), U.getLevel()),
                REASON,
                ruleForClosedRange,
                newAnnotationForAtLeast(S2, REASON));
        assertGetRequiredRangeForAnnotatedTest(
                AndroidSdkRange.forRange(T.getLevel(), U.getLevel()),
                REASON,
                ruleForClosedRange,
                newAnnotationForAtLeast(T, REASON));
        assertGetRequiredRangeForAnnotatedTest(
                AndroidSdkRange.forRange(U.getLevel(), U.getLevel()),
                REASON,
                ruleForClosedRange,
                newAnnotationForAtLeast(U, REASON));
        //      // TODO(b/295269584): replace lessThatT test for required range annotation
        assertGetRequiredRangeForAnnotatedTest(
                AndroidSdkRange.forRange(R.getLevel(), S2.getLevel()),
                REASON,
                ruleForClosedRange,
                newAnnotationForLessThanT(REASON));
    }

    private void assertGetRequiredRangeForAnnotatedTest(
            AndroidSdkRange expectedRange,
            String expectedReason,
            AndroidSdkRange ruleRange,
            Annotation... annotations) {
        var rule = new FakeSdkLevelSupportedRule(ruleRange);
        var expectedRequiredRange = new RequiredRange(expectedRange, expectedReason);

        assertGetRequiredRange(expectedRequiredRange, rule, newTestMethod(annotations));
    }

    @Test
    public void testGetRequiredRange_annotatedTestClass_unannotatedTest() throws Throwable {
        // Test with any-range constructor first
        var ruleForAnyLevel = new FakeSdkLevelSupportedRule(AndroidSdkRange.forAnyLevel());
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forAtLeast(R.getLevel()), REASON_R),
                ruleForAnyLevel,
                newTestMethod(ClassThatRequiresAtLeastR.class));
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forAtLeast(S.getLevel()), REASON_S),
                ruleForAnyLevel,
                newTestMethod(ClassThatRequiresAtLeastS.class));
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forAtLeast(S2.getLevel()), REASON_S2),
                ruleForAnyLevel,
                newTestMethod(ClassThatRequiresAtLeastS2.class));
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forAtLeast(T.getLevel()), REASON_T),
                ruleForAnyLevel,
                newTestMethod(ClassThatRequiresAtLeastT.class));
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forAtLeast(U.getLevel()), REASON_U),
                ruleForAnyLevel,
                newTestMethod(ClassThatRequiresAtLeastU.class));
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forAtMost(S2.getLevel()), REASON_LESS_THAN_T),
                ruleForAnyLevel,
                newTestMethod(ClassThatRequiresLessThanT.class));

        // Then for well-defined range (min and max)
        var ruleForClosedRange =
                new FakeSdkLevelSupportedRule(AndroidSdkRange.forRange(R.getLevel(), U.getLevel()));
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forRange(R.getLevel(), U.getLevel()), REASON_R),
                ruleForClosedRange,
                newTestMethod(ClassThatRequiresAtLeastR.class));
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forRange(S.getLevel(), U.getLevel()), REASON_S),
                ruleForClosedRange,
                newTestMethod(ClassThatRequiresAtLeastS.class));
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forRange(S2.getLevel(), U.getLevel()), REASON_S2),
                ruleForClosedRange,
                newTestMethod(ClassThatRequiresAtLeastS2.class));
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forRange(T.getLevel(), U.getLevel()), REASON_T),
                ruleForClosedRange,
                newTestMethod(ClassThatRequiresAtLeastT.class));
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forExactly(U.getLevel()), REASON_U),
                ruleForClosedRange,
                newTestMethod(ClassThatRequiresAtLeastU.class));
        assertGetRequiredRange(
                new RequiredRange(
                        AndroidSdkRange.forRange(R.getLevel(), S2.getLevel()), REASON_LESS_THAN_T),
                ruleForClosedRange,
                newTestMethod(ClassThatRequiresLessThanT.class));
    }

    @Test
    public void testGetRequiredRange_annotatedTestClass_annotatedTest() throws Throwable {
        // Test with any-range constructor first
        var ruleForAnyLevel = new FakeSdkLevelSupportedRule(AndroidSdkRange.forAnyLevel());
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forAtLeast(S.getLevel()), REASON, REASON_R),
                ruleForAnyLevel,
                newTestMethod(ClassThatRequiresAtLeastR.class, newAnnotationForAtLeast(S, REASON)));
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forAtLeast(S.getLevel()), REASON, REASON_S),
                ruleForAnyLevel,
                newTestMethod(ClassThatRequiresAtLeastS.class, newAnnotationForAtLeast(S, REASON)));
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forAtLeast(S2.getLevel()), REASON, REASON_S2),
                ruleForAnyLevel,
                newTestMethod(
                        ClassThatRequiresAtLeastS2.class, newAnnotationForAtLeast(S, REASON)));
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forAtLeast(T.getLevel()), REASON, REASON_T),
                ruleForAnyLevel,
                newTestMethod(ClassThatRequiresAtLeastT.class, newAnnotationForAtLeast(S, REASON)));
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forAtLeast(U.getLevel()), REASON, REASON_U),
                ruleForAnyLevel,
                newTestMethod(ClassThatRequiresAtLeastU.class, newAnnotationForAtLeast(S, REASON)));
        assertGetRequiredRange(
                new RequiredRange(
                        AndroidSdkRange.forRange(S.getLevel(), S2.getLevel()),
                        REASON,
                        REASON_LESS_THAN_T),
                ruleForAnyLevel,
                newTestMethod(
                        ClassThatRequiresLessThanT.class, newAnnotationForAtLeast(S, REASON)));

        var ruleForClosedRange =
                new FakeSdkLevelSupportedRule(AndroidSdkRange.forRange(R.getLevel(), U.getLevel()));
        assertGetRequiredRange(
                new RequiredRange(
                        AndroidSdkRange.forRange(S.getLevel(), U.getLevel()), REASON, REASON_R),
                ruleForClosedRange,
                newTestMethod(ClassThatRequiresAtLeastR.class, newAnnotationForAtLeast(S, REASON)));
        assertGetRequiredRange(
                new RequiredRange(
                        AndroidSdkRange.forRange(S.getLevel(), U.getLevel()), REASON, REASON_S),
                ruleForClosedRange,
                newTestMethod(ClassThatRequiresAtLeastS.class, newAnnotationForAtLeast(S, REASON)));
        assertGetRequiredRange(
                new RequiredRange(
                        AndroidSdkRange.forRange(S2.getLevel(), U.getLevel()), REASON, REASON_S2),
                ruleForClosedRange,
                newTestMethod(
                        ClassThatRequiresAtLeastS2.class, newAnnotationForAtLeast(S, REASON)));
        assertGetRequiredRange(
                new RequiredRange(
                        AndroidSdkRange.forRange(T.getLevel(), U.getLevel()), REASON, REASON_T),
                ruleForClosedRange,
                newTestMethod(ClassThatRequiresAtLeastT.class, newAnnotationForAtLeast(S, REASON)));
        assertGetRequiredRange(
                new RequiredRange(AndroidSdkRange.forExactly(U.getLevel()), REASON, REASON_U),
                ruleForClosedRange,
                newTestMethod(ClassThatRequiresAtLeastU.class, newAnnotationForAtLeast(S, REASON)));
        assertGetRequiredRange(
                new RequiredRange(
                        AndroidSdkRange.forRange(S.getLevel(), S2.getLevel()),
                        REASON,
                        REASON_LESS_THAN_T),
                ruleForClosedRange,
                newTestMethod(
                        ClassThatRequiresLessThanT.class, newAnnotationForAtLeast(S, REASON)));
    }

    @Test
    public void testGetRequiredRange_annotatedSuperClass_annotatedTestClass_annotatedTest()
            throws Throwable {
        // NOTE: this method  is "pragmatically" testing just one combination of those annotations,
        // otherwise it would not scale.

        assertGetRequiredRange(
                new RequiredRange(
                        AndroidSdkRange.forAtLeast(U.getLevel()), REASON_R, REASON_T, REASON_U),
                new FakeSdkLevelSupportedRule(AndroidSdkRange.forAtLeast(S.getLevel())),
                newTestMethod(
                        ClassThatRequiresAtLeastTWhenSuperClassRequiresAtLeastU.class,
                        newAnnotationForAtLeast(R, REASON_R)));
    }

    @Test
    public void testAnnotationToRequiredRangeConversion() throws Throwable {
        expectAnnotationConvertedToRequiredRange(
                newAnnotationForAtLeast(R, REASON),
                AndroidSdkRange.forAtLeast(R.getLevel()),
                REASON);
        expectAnnotationConvertedToRequiredRange(
                newAnnotationForAtLeast(S, REASON),
                AndroidSdkRange.forAtLeast(S.getLevel()),
                REASON);
        expectAnnotationConvertedToRequiredRange(
                newAnnotationForAtLeast(S2, REASON),
                AndroidSdkRange.forAtLeast(S2.getLevel()),
                REASON);
        expectAnnotationConvertedToRequiredRange(
                newAnnotationForAtLeast(T, REASON),
                AndroidSdkRange.forAtLeast(T.getLevel()),
                REASON);
        expectAnnotationConvertedToRequiredRange(
                newAnnotationForAtLeast(U, REASON),
                AndroidSdkRange.forAtLeast(U.getLevel()),
                REASON);
        // TODO(b/295269584): replace lessThatT test for required range annotation
        expectAnnotationConvertedToRequiredRange(
                newAnnotationForLessThanT(REASON),
                AndroidSdkRange.forAtMost(S2.getLevel()),
                REASON);
    }

    private void expectAnnotationConvertedToRequiredRange(
            Annotation annotation, AndroidSdkRange expectedRange, String expectedReason) {
        var rule = newRuleForDeviceLevelAndRuleAtLeastLevel(ANY);
        var expectedRequiredRange = new RequiredRange(expectedRange, expectedReason);
        RequiredRange actualRequiredRange = rule.getRequiredRange(Arrays.asList(annotation));

        expect.withMessage("getRequiredRange(%s)", annotation)
                .that(actualRequiredRange)
                .isEqualTo(expectedRequiredRange);

        expect.withMessage("getRequiredRange(%s).reason", annotation)
                .that(actualRequiredRange.reasons)
                .containsExactlyElementsIn(expectedRequiredRange.reasons);
    }

    @Test
    public void testGetRequiredRange_multipleAnnotationsWithReason() throws Throwable {
        var rule = newRuleForDeviceLevelAndRuleAtLeastLevel(ANY);

        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                rule.getRequiredRange(
                                        Arrays.asList(
                                                newAnnotationForAtLeast(R, REASON),
                                                newAnnotationForAtLeast(S, ANOTHER_REASON))));

        expect.withMessage("exception message").that(e).hasMessageThat().contains(REASON);
        expect.withMessage("exception message").that(e).hasMessageThat().contains(ANOTHER_REASON);
    }


    /***************************************************************************************
     * NOTE: tests below are "legacy", when the rule was hardcoded to check for specific   *
     * annotations. They're kept here as an extra safety net, but pragmatically, the tests *
     * above - based in the required range - are enough.                                   *
     ***************************************************************************************/

    /*
     * Tests for rule constructed for any level.
     */
    @Test
    public void testRuleIsAtLeastAny_deviceIsR_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ ANY, /* deviceLevel= */ R);
    }

    @Test
    public void testRuleIsAtLeastAny_deviceIsS_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ ANY, /* deviceLevel= */ S);
    }

    @Test
    public void testRuleIsAtLeastAny_deviceIsS2_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ ANY, /* deviceLevel= */ S2);
    }

    @Test
    public void testRuleIsAtLeastAny_deviceIsT_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ ANY, /* deviceLevel= */ T);
    }

    @Test
    public void testRuleIsAtLeastAny_deviceIsU_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ ANY, /* deviceLevel= */ U);
    }

    @Test
    public void testRuleIsAtLeastAny_deviceIsR_testAnnotatedWithR_runs() throws Throwable {
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ ANY, /* deviceLevel= */ R, /* annotationLevel=*/ S);
    }

    /*
     * Tests for rule constructed for at least R.
     */
    @Test
    public void testRuleIsAtLeastR_deviceIsR_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ R, /* deviceLevel= */ R);
    }

    @Test
    public void testRuleIsAtLeastR_deviceIsS_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ R, /* deviceLevel= */ S);
    }

    @Test
    public void testRuleIsAtLeastR_deviceIsT_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ R, /* deviceLevel= */ T);
    }

    @Test
    public void testRuleIsAtLeastR_deviceIsU_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ R, /* deviceLevel= */ U);
    }

    @Test
    public void testRuleIsAtLeastR_deviceIsR_testAnnotatedWithR_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ R, /* deviceLevel= */ R, /* annotationLevel=*/ R);
    }

    /*
     * Tests for rule constructed for at least S.
     */
    @Test
    public void testRuleIsAtLeastS_deviceIsR_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S, /* deviceLevel= */ R);
    }

    @Test
    public void testRuleIsAtLeastS_deviceIsS_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S, /* deviceLevel= */ S);
    }

    @Test
    public void testRuleIsAtLeastS_deviceIsS2_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S, /* deviceLevel= */ S2);
    }

    @Test
    public void testRuleIsAtLeastS_deviceIsS_testAnnotatedWithT_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ S, /* deviceLevel= */ S, /* annotationLevel=*/ T);
    }

    @Test
    public void testRuleIsAtLeastS_deviceIsS_testAnnotatedWithU_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ S, /* deviceLevel= */ S, /* annotationLevel=*/ U);
    }

    @Test
    public void testRuleIsAtLeastS_deviceIsT_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S, /* deviceLevel= */ T);
    }

    @Test
    public void testRuleIsAtLeastS_deviceIsU_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S, /* deviceLevel= */ U);
    }

    @Test
    public void testRuleIsAtLeastS_deviceIsS_testAnnotatedWithS_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ S, /* deviceLevel= */ S, /* annotationLevel=*/ S);
    }

    /*
     * Tests for rule constructed for at least S2.
     */
    @Test
    public void testRuleIsAtLeastS2_deviceIsR_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S2, /* deviceLevel= */ R);
    }

    @Test
    public void testRuleIsAtLeastS2_deviceIsS_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S2, /* deviceLevel= */ S);
    }

    @Test
    public void testRuleIsAtLeastS2_deviceIsS2_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S2, /* deviceLevel= */ S2);
    }

    @Test
    public void testRuleIsAtLeastS2_deviceIsS2_testAnnotatedWithT_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ S2, /* deviceLevel= */ S2, /* annotationLevel=*/ T);
    }

    @Test
    public void testRuleIsAtLeastS2_deviceIsS2_testAnnotatedWithU_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ S2, /* deviceLevel= */ S2, /* annotationLevel=*/ U);
    }

    @Test
    public void testRuleIsAtLeastS2_deviceIsT_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S2, /* deviceLevel= */ T);
    }

    @Test
    public void testRuleIsAtLeastS2_deviceIsU_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S2, /* deviceLevel= */ U);
    }

    @Test
    public void testRuleIsAtLeastS2_deviceIsS2_testAnnotatedWithS2_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ S2, /* deviceLevel= */ S2, /* annotationLevel=*/ S2);
    }

    /*
     * Tests for rule constructed for at least T.
     */
    @Test
    public void testRuleIsAtLeastT_deviceIsR_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ T, /* deviceLevel= */ R);
    }

    @Test
    public void testRuleIsAtLeastT_deviceIsS_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ T, /* deviceLevel= */ S);
    }

    @Test
    public void testRuleIsAtLeastT_deviceIsT_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ T, /* deviceLevel= */ T);
    }

    @Test
    public void testRuleIsAtLeastT_deviceIsT_testAnnotatedWithU_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ T, /* deviceLevel= */ T, /* annotationLevel=*/ U);
    }

    @Test
    public void testRuleIsAtLeastT_deviceIsU_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ T, /* deviceLevel= */ U);
    }

    @Test
    public void testRuleIsAtLeastT_deviceIsT_testAnnotatedWithT_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ T, /* deviceLevel= */ T, /* annotationLevel=*/ T);
    }

    /*
     * Tests for rule constructed for at least U.
     */
    @Test
    public void testRuleIsAtLeastU_deviceIsR_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ U, /* deviceLevel= */ R);
    }

    @Test
    public void testRuleIsAtLeastU_deviceIsS_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ U, /* deviceLevel= */ S);
    }

    @Test
    public void testRuleIsAtLeastU_deviceIsT_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ U, /* deviceLevel= */ T);
    }

    @Test
    public void testRuleIsAtLeastU_deviceIsU_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ U, /* deviceLevel= */ U);
    }

    @Test
    public void testRuleIsAtLeastU_deviceIsU_testAnnotatedWithU_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ U, /* deviceLevel= */ U, /* annotationLevel=*/ U);
    }

    /*
     * Tests for tests annotated with @RequiresSdkLevelLessThanT - the rule is currently hard-coded
     * to handle that annotation, so we need unit tests as a safety net to refactor that logic (even
     * if this annotation is removed / deprecated later).
     */

    @Test
    public void testAnnotatedWithRequiresSdkLevelLessThanT_skips() {
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ ANY, /* deviceLevel= */ T);
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ ANY, /* deviceLevel= */ U);
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ R, /* deviceLevel= */ T);
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ R, /* deviceLevel= */ U);
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ S, /* deviceLevel= */ T);

        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ S, /* deviceLevel= */ U);
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ S2, /* deviceLevel= */ T);
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ S2, /* deviceLevel= */ U);
    }

    // Should fail because the annotation is out of the range of the rule (i.e. < S2 and >= T)
    @Test
    public void testAnnotatedWithRequiresSdkLevelLessThanT_invalid() {
        testThrewWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ T, /* deviceLevel= */ T);
        testThrewWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ T, /* deviceLevel= */ U);
        testThrewWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ U, /* deviceLevel= */ T);
        testThrewWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ U, /* deviceLevel= */ U);
    }

    @Test
    public void testAnnotatedWithRequiresSdkLevelLessThanT_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ ANY, /* deviceLevel= */ R);
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ ANY, /* deviceLevel= */ S);
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ ANY, /* deviceLevel= */ S2);

        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ R, /* deviceLevel= */ R);
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ R, /* deviceLevel= */ S);
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ R, /* deviceLevel= */ S2);

        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ S, /* deviceLevel= */ S);
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ S, /* deviceLevel= */ S2);

        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ S2, /* deviceLevel= */ S2);
    }

    private void testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
            AndroidSdkLevel ruleLevel, AndroidSdkLevel deviceLevel) {
        var rule = newRule(ruleLevel, deviceLevel);
        Description testMethod = newTestMethod(newAnnotationForLessThanT(REASON));

        AssumptionViolatedException e =
                assertThrows(
                        AssumptionViolatedException.class,
                        () -> rule.apply(mBaseStatement, testMethod).evaluate());

        StringSubject exceptionMessage =
                expect.withMessage("exception message").that(e).hasMessageThat();
        exceptionMessage.contains(
                AndroidSdkRange.forRange(ruleLevel.getLevel(), S2.getLevel()).toString());
        exceptionMessage.contains(REASON);

        mBaseStatement.assertNotEvaluated();
    }

    private void testThrewWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
            AndroidSdkLevel ruleLevel, AndroidSdkLevel deviceLevel) {
        var rule = newRule(ruleLevel, deviceLevel);
        Description testMethod = newTestMethod(newAnnotationForLessThanT(REASON));

        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> rule.apply(mBaseStatement, testMethod).evaluate());

        StringSubject exceptionMessage =
                expect.withMessage("exception message").that(e).hasMessageThat();
        exceptionMessage.matches(
                ".*Invalid range.*minLevel=" + ruleLevel.getLevel() + ".*" + REASON + ".*");

        mBaseStatement.assertNotEvaluated();
    }

    private void testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
            AndroidSdkLevel ruleLevel, AndroidSdkLevel deviceLevel) throws Throwable {
        var rule = newRule(ruleLevel, deviceLevel);
        Description testMethod = newTestMethod(newAnnotationForLessThanT(REASON));

        try {
            rule.apply(mBaseStatement, testMethod).evaluate();
        } catch (AssumptionViolatedException e) {
            throw new Exception(
                    "test should not throw AssumptionViolatedException: " + e.getMessage(), e);
        }

        mBaseStatement.assertEvaluated();
    }

    /**
     * Creates a rule that is constructed for at least {@code level} and also mocks that rule's
     * {@link AbstractSdkLevelSupportedRule#getDeviceApiLevel()} to return {@code level}.
     */
    protected AbstractSdkLevelSupportedRule newRuleForDeviceLevelAndRuleAtLeastLevel(
            AndroidSdkLevel level) {
        return new FakeSdkLevelSupportedRule(/* ruleLevel= */ level, /* deviceLevel= */ level);
    }

    /**
     * Creates a rule that is constructed for at least {@code ruleLevel} and also mocks that rule's
     * {@link AbstractSdkLevelSupportedRule#getDeviceApiLevel()} to return {@code deviceLevel}.
     */
    protected AbstractSdkLevelSupportedRule newRule(
            AndroidSdkLevel ruleLevel, AndroidSdkLevel deviceLevel) {
        return new FakeSdkLevelSupportedRule(ruleLevel, deviceLevel);
    }

    /**
     * Creates a rule that is constructed for the given {@code ruleRange} and also mocks that rule's
     * {@link AbstractSdkLevelSupportedRule#getDeviceApiLevel()} to return {@code deviceLevel}.
     */
    protected AbstractSdkLevelSupportedRule newRule(
            AndroidSdkRange ruleRange, AndroidSdkLevel deviceLevel) {
        return new FakeSdkLevelSupportedRule(ruleRange, deviceLevel);
    }

    // NOTE: eventually there will be releases X, Y, Z, but other names would make these methods
    // even longer than what they already are

    private void testRanWhenRuleIsAtLeastXAndDeviceIsY(
            AndroidSdkLevel ruleLevel, AndroidSdkLevel deviceLevel) throws Throwable {
        var rule = newRuleForDeviceLevelAndRuleAtLeastLevel(ruleLevel);
        Description testMethod = newTestMethod();

        try {
            rule.apply(mBaseStatement, testMethod).evaluate();
        } catch (AssumptionViolatedException e) {
            throw new Exception("test should not throw", e);
        }

        mBaseStatement.assertEvaluated();
    }

    private void testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
            AndroidSdkLevel ruleLevel, AndroidSdkLevel deviceLevel, AndroidSdkLevel annotationLevel)
            throws Throwable {
        var rule = newRuleForDeviceLevelAndRuleAtLeastLevel(ruleLevel);
        Description testMethod = newTestMethod(newAnnotationForAtLeast(annotationLevel, REASON));

        try {
            rule.apply(mBaseStatement, testMethod).evaluate();
        } catch (AssumptionViolatedException e) {
            throw new Exception(
                    "test should not throw AssumptionViolatedException: " + e.getMessage(), e);
        }

        mBaseStatement.assertEvaluated();
    }

    private void testSkippedWhenRuleIsAtLeastXAndDeviceIsY(
            AndroidSdkLevel ruleLevel, AndroidSdkLevel deviceLevel) {
        var rule = newRule(ruleLevel, deviceLevel);
        Description testMethod = newTestMethod();

        AssumptionViolatedException e =
                assertThrows(
                        AssumptionViolatedException.class,
                        () -> rule.apply(mBaseStatement, testMethod).evaluate());

        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .contains(AndroidSdkRange.forAtLeast(ruleLevel.getLevel()).toString());

        mBaseStatement.assertNotEvaluated();
    }

    private void testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
            AndroidSdkLevel ruleLevel,
            AndroidSdkLevel deviceLevel,
            AndroidSdkLevel annotationLevel) {
        var rule = newRule(ruleLevel, deviceLevel);
        Description testMethod = newTestMethod(newAnnotationForAtLeast(annotationLevel, REASON));

        AssumptionViolatedException e =
                assertThrows(
                        AssumptionViolatedException.class,
                        () -> rule.apply(mBaseStatement, testMethod).evaluate());

        StringSubject exceptionMessage =
                expect.withMessage("exception message").that(e).hasMessageThat();
        exceptionMessage.contains(
                AndroidSdkRange.forAtLeast(annotationLevel.getLevel()).toString());
        exceptionMessage.contains(REASON);

        mBaseStatement.assertNotEvaluated();
    }

    private static Description newTestMethod(Annotation... annotations) {
        return newTestMethod(TEST_METHOD_BEING_EXECUTED, annotations);
    }

    private static Description newTestMethod(String methodName, Annotation... annotations) {
        return Description.createTestDescription(
                AbstractSdkLevelSupportedRuleTest.class, methodName, annotations);
    }

    private static Description newTestMethod(Class<?> clazz, Annotation... annotations) {
        return Description.createTestDescription(clazz, TEST_METHOD_BEING_EXECUTED, annotations);
    }

    /**
     * Bogus implementation of {@link AbstractSdkLevelSupported}.
     *
     * <p>It's returned by default by {@link
     * AbstractSdkLevelSupportedRuleTest#newRule(AndroidSdkLevel, AndroidSdkLevel)} and {@link
     * AbstractSdkLevelSupportedRuleTest#newRuleForDeviceLevelAndRuleAtLeastLevel(AndroidSdkLevel)},
     * so this test class can run independently of the real implementation, which makes it possible
     * to run this test directly from an IDE.
     */
    private static final class FakeSdkLevelSupportedRule extends AbstractSdkLevelSupportedRule {

        @Nullable private final AndroidSdkLevel mDeviceLevel;

        private FakeSdkLevelSupportedRule(AndroidSdkLevel ruleLevel, AndroidSdkLevel deviceLevel) {
            this(AndroidSdkRange.forAtLeast(ruleLevel.getLevel()), deviceLevel);
        }

        private FakeSdkLevelSupportedRule(AndroidSdkRange ruleRange) {
            this(ruleRange, /* deviceLevel= */ null);
        }

        private FakeSdkLevelSupportedRule(AndroidSdkRange ruleRange, AndroidSdkLevel deviceLevel) {
            super(StandardStreamsLogger.getInstance(), ruleRange);
            mDeviceLevel = deviceLevel;
        }

        @Override
        public AndroidSdkLevel getDeviceApiLevel() {
            if (mDeviceLevel == null) {
                throw new UnsupportedOperationException(
                        "Rule created with constructor that doesn't provide the device level");
            }
            return mDeviceLevel;
        }

        @Override
        public String toString() {
            return super.toString() + "[mDeviceLevel=" + mDeviceLevel + "]";
        }
    }

    @RequiresSdkLevelAtLeastR(reason = REASON_R)
    private static class ClassThatRequiresAtLeastR {}

    @RequiresSdkLevelAtLeastS(reason = REASON_S)
    private static class ClassThatRequiresAtLeastS {}

    @RequiresSdkLevelAtLeastS2(reason = REASON_S2)
    private static class ClassThatRequiresAtLeastS2 {}

    @RequiresSdkLevelAtLeastU(reason = REASON_U)
    private static class ClassThatRequiresAtLeastU {}

    @RequiresSdkLevelAtLeastT(reason = REASON_T)
    private static class ClassThatRequiresAtLeastT {}

    @RequiresSdkLevelLessThanT(reason = REASON_LESS_THAN_T)
    private static class ClassThatRequiresLessThanT {}

    @RequiresSdkLevelAtLeastT(reason = REASON_T)
    private static final class ClassThatRequiresAtLeastTWhenSuperClassRequiresAtLeastU
            extends ClassThatRequiresAtLeastU {}
}
