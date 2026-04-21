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

import com.android.adservices.common.Logger.RealLogger;

import com.google.common.annotations.VisibleForTesting;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// TODO(b/295269584): add examples
/**
 * Rule used to skip a test when it's not supported by the device's SDK version.
 *
 * <p>This rule is abstract so subclass can define what a "feature" means. It also doesn't have any
 * dependency on Android code, so it can be used both on device-side and host-side tests.
 *
 * <p><b>NOTE: </b>this class should NOT be used as {@code ClassRule}, as it would result in a "no
 * tests run" scenario if it throws a {@link AssumptionViolatedException}.
 */
abstract class AbstractSdkLevelSupportedRule implements TestRule {

    private static final String TAG = "SdkLevelSupportRule";

    @VisibleForTesting static final String DEFAULT_REASON = "N/A";

    private final AndroidSdkRange mDefaultRequiredRange;
    protected final Logger mLog;

    AbstractSdkLevelSupportedRule(RealLogger logger, AndroidSdkRange defaultRange) {
        mLog = new Logger(Objects.requireNonNull(logger), TAG);
        mDefaultRequiredRange = Objects.requireNonNull(defaultRange);
        mLog.d("Constructor: logger=%s, defaultRange=%s", logger, defaultRange);
    }

    AbstractSdkLevelSupportedRule(RealLogger logger) {
        this(logger, /* defaultRange= */ AndroidSdkRange.forAnyLevel());
    }

    @Override
    public final Statement apply(Statement base, Description description) {
        if (!description.isTest()) {
            throw new IllegalStateException(
                    "This rule can only be applied to individual tests, it cannot be used as"
                            + " @ClassRule or in a test suite");
        }
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String testName = description.getDisplayName();
                RequiredRange requiredRange = getRequiredRange(description);
                mLog.v("required SDK range for %s: %s", testName, requiredRange);

                int deviceLevel = getDeviceApiLevel().mLevel;
                boolean skip = !requiredRange.range.isInRange(deviceLevel);
                if (skip) {
                    String message =
                            "requires "
                                    + requiredRange.range
                                    + " (and device level is "
                                    + deviceLevel
                                    + "). Reasons: "
                                    + requiredRange.reasons;
                    mLog.i("Skipping %s, as it %s", testName, message);
                    throw new AssumptionViolatedException("Test " + message);
                }
                base.evaluate();
            }
        };
    }

    @VisibleForTesting
    RequiredRange getRequiredRange(Description description) {
        // List of ranges defined in the test itself and its superclasses
        Set<AndroidSdkRange> ranges = new HashSet<>();
        List<String> reasons;

        // Start with the test class
        RequiredRange testRange =
                getRequiredRange(
                        description.getAnnotations(),
                        /* allowEmpty= */ false,
                        /* addDefaultRange= */ true,
                        /* setDefaultReason= */ false);
        reasons = testRange.reasons;
        ranges.add(testRange.range);

        // Then the superclasses
        Class<?> clazz = description.getTestClass();
        do {
            RequiredRange testClassRange = getRequiredRangeFromClass(clazz);
            if (testClassRange != null) {
                ranges.add(testClassRange.range);
                reasons.addAll(testClassRange.reasons);
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);

        if (reasons.isEmpty()) {
            reasons.add(DEFAULT_REASON);
        }
        AndroidSdkRange mergedRange = AndroidSdkRange.merge(ranges);
        return new RequiredRange(mergedRange, reasons);
    }

    @VisibleForTesting
    RequiredRange getRequiredRange(Collection<Annotation> annotations) {
        return getRequiredRange(
                annotations,
                /* allowEmpty= */ false,
                /* addDefaultRange= */ true,
                /* setDefaultReason= */ true);
    }

    @Nullable
    private RequiredRange getRequiredRangeFromClass(Class<?> testClass) {
        Annotation[] annotations = testClass.getAnnotations();
        if (annotations == null) {
            return null;
        }

        return getRequiredRange(
                Arrays.asList(annotations),
                /* allowEmpty= */ true,
                /* addDefaultRange= */ false,
                /* setDefaultReason= */ false);
    }

    @Nullable
    private RequiredRange getRequiredRange(
            Collection<Annotation> annotations,
            boolean allowEmpty,
            boolean addDefaultRange,
            boolean setDefaultReason) {
        Set<AndroidSdkRange> ranges = new HashSet<>();
        if (addDefaultRange) {
            ranges.add(mDefaultRequiredRange);
        }
        String reason = null;

        for (Annotation annotation : annotations) {
            if (annotation instanceof RequiresSdkLevelAtLeastR) {
                ranges.add(AndroidSdkRange.forAtLeast(AndroidSdkLevel.R.getLevel()));
                reason = getReason(reason, ((RequiresSdkLevelAtLeastR) annotation).reason());
            }
            if (annotation instanceof RequiresSdkLevelAtLeastS) {
                ranges.add(AndroidSdkRange.forAtLeast(AndroidSdkLevel.S.getLevel()));
                reason = getReason(reason, ((RequiresSdkLevelAtLeastS) annotation).reason());
            }
            if (annotation instanceof RequiresSdkLevelAtLeastS2) {
                ranges.add(AndroidSdkRange.forAtLeast(AndroidSdkLevel.S2.getLevel()));
                reason = getReason(reason, ((RequiresSdkLevelAtLeastS2) annotation).reason());
            }
            if (annotation instanceof RequiresSdkLevelAtLeastT) {
                ranges.add(AndroidSdkRange.forAtLeast(AndroidSdkLevel.T.getLevel()));
                reason = getReason(reason, ((RequiresSdkLevelAtLeastT) annotation).reason());
                reason = ((RequiresSdkLevelAtLeastT) annotation).reason();
            }
            if (annotation instanceof RequiresSdkLevelAtLeastU) {
                ranges.add(AndroidSdkRange.forAtLeast(AndroidSdkLevel.U.getLevel()));
                reason = getReason(reason, ((RequiresSdkLevelAtLeastU) annotation).reason());
            }
            if (annotation instanceof RequiresSdkLevelLessThanT) {
                ranges.add(AndroidSdkRange.forAtMost(AndroidSdkLevel.S2.getLevel()));
                reason = getReason(reason, ((RequiresSdkLevelLessThanT) annotation).reason());
            }
        }

        if (ranges.isEmpty() && allowEmpty) {
            return null;
        }

        if (reason == null && setDefaultReason) {
            reason = DEFAULT_REASON;
        }

        try {
            AndroidSdkRange mergedRange = AndroidSdkRange.merge(ranges);
            return new RequiredRange(mergedRange, reason);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid range when combining constructor range ("
                            + ranges
                            + ") and annotations ("
                            + annotations
                            + ")",
                    e);
        }
    }

    private String getReason(String currentReason, String newReason) {
        if (newReason == null) {
            return currentReason;
        }
        if (currentReason == null || currentReason.equals(newReason)) {
            return newReason;
        }
        throw new IllegalStateException(
                "Found annotation with reason ("
                        + newReason
                        + ") different from previous annotation reason ("
                        + currentReason
                        + ")");
    }

    @VisibleForTesting
    static final class RequiredRange {
        public final AndroidSdkRange range;
        public final List<String> reasons;

        RequiredRange(AndroidSdkRange range, @Nullable String... reasons) {
            // getRequiredRange() might call it with a null reason, hence the check for 1st element
            // being null
            this(
                    range,
                    reasons == null || (reasons.length == 1 && reasons[0] == null)
                            ? new ArrayList<>()
                            : Arrays.stream(reasons).collect(Collectors.toList()));
        }

        RequiredRange(AndroidSdkRange range, List<String> reasons) {
            this.range = range;
            this.reasons = reasons;
        }

        @Override
        public int hashCode() {
            return Objects.hash(range);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            RequiredRange other = (RequiredRange) obj;
            return Objects.equals(range, other.range);
        }

        @Override
        public String toString() {
            return "[range=" + range + ", reasons=" + reasons + "]";
        }
    }
    /** Gets the device API level. */
    public abstract AndroidSdkLevel getDeviceApiLevel();

    /** Gets whether the device supports at least Android {@code R}. */
    public final boolean isAtLeastR() {
        return getDeviceApiLevel().isAtLeast(AndroidSdkLevel.R);
    }

    /** Gets whether the device supports at least Android {@code S}. */
    public final boolean isAtLeastS() {
        return getDeviceApiLevel().isAtLeast(AndroidSdkLevel.S);
    }

    /** Gets whether the device supports at least Android {@code SC_V2}. */
    public final boolean isAtLeastS2() {
        return getDeviceApiLevel().isAtLeast(AndroidSdkLevel.S2);
    }

    /** Gets whether the device supports at least Android {@code T}. */
    public final boolean isAtLeastT() {
        return getDeviceApiLevel().isAtLeast(AndroidSdkLevel.T);
    }

    /** Gets whether the device supports at least Android {@code U}. */
    public final boolean isAtLeastU() {
        return getDeviceApiLevel().isAtLeast(AndroidSdkLevel.U);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[mDefaultRequiredRange=" + mDefaultRequiredRange + "]";
    }

    // NOTE: calling it AndroidSdkLevel to avoid conflict with SdkLevel
    protected enum AndroidSdkLevel {
        ANY(Integer.MIN_VALUE),
        R(30),
        S(31),
        S2(32),
        T(33),
        U(34);

        private final int mLevel;

        AndroidSdkLevel(int level) {
            mLevel = level;
        }

        boolean isAtLeast(AndroidSdkLevel level) {
            return mLevel >= level.mLevel;
        }

        int getLevel() {
            return mLevel;
        }

        public static AndroidSdkLevel forLevel(int level) {
            switch (level) {
                case 30:
                    return R;
                case 31:
                    return S;
                case 32:
                    return S2;
                case 33:
                    return T;
                case 34:
                    return U;
            }
            throw new IllegalArgumentException("Unsupported level: " + level);
        }
    }

    /** Represents a range of Android API levels. */
    static final class AndroidSdkRange {
        static final int NO_MIN = Integer.MIN_VALUE;
        static final int NO_MAX = Integer.MAX_VALUE;

        private final int mMinLevel;
        private final int mMaxLevel;

        private AndroidSdkRange(int minLevel, int maxLevel) {
            if (minLevel > maxLevel || minLevel == NO_MAX || maxLevel == NO_MIN) {
                throw new IllegalArgumentException(
                        "maxLevel ("
                                + maxLevel
                                + ") must equal or higher than minLevel ("
                                + minLevel
                                + ")");
            }
            mMinLevel = minLevel;
            mMaxLevel = maxLevel;
        }

        public static AndroidSdkRange forAtLeast(int level) {
            return new AndroidSdkRange(/* minLevel= */ level, NO_MAX);
        }

        public static AndroidSdkRange forAtMost(int level) {
            return new AndroidSdkRange(NO_MIN, /* maxLevel= */ level);
        }

        public static AndroidSdkRange forRange(int minLevel, int maxLevel) {
            return new AndroidSdkRange(minLevel, maxLevel);
        }

        public static AndroidSdkRange forExactly(int level) {
            return new AndroidSdkRange(/* minLevel= */ level, /* maxLevel= */ level);
        }

        public static AndroidSdkRange forAnyLevel() {
            return new AndroidSdkRange(NO_MIN, NO_MAX);
        }

        public boolean isInRange(int level) {
            return level >= mMinLevel && level <= mMaxLevel;
        }

        protected static AndroidSdkRange merge(AndroidSdkRange... ranges) {
            return merge(Arrays.asList(ranges));
        }

        protected static AndroidSdkRange merge(Collection<AndroidSdkRange> ranges) {
            Objects.requireNonNull(ranges, "ranges cannot be null");
            if (ranges.isEmpty()) {
                throw new IllegalArgumentException("ranges cannot be empty");
            }
            int minRange = NO_MIN;
            int maxRange = NO_MAX;
            for (AndroidSdkRange range : ranges) {
                if (range == null) {
                    throw new IllegalArgumentException("ranges cannot have null range: " + ranges);
                }
                minRange = Math.max(minRange, range.mMinLevel);
                maxRange = Math.min(maxRange, range.mMaxLevel);
            }
            return forRange(minRange, maxRange);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mMaxLevel, mMinLevel);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            AndroidSdkRange other = (AndroidSdkRange) obj;
            return mMaxLevel == other.mMaxLevel && mMinLevel == other.mMinLevel;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("AndroidSdkRange[minLevel=");
            if (mMinLevel == NO_MIN) {
                builder.append("OPEN");
            } else {
                builder.append(mMinLevel);
            }
            builder.append(", maxLevel=");
            if (mMaxLevel == NO_MAX) {
                builder.append("OPEN");
            } else {
                builder.append(mMaxLevel);
            }
            return builder.append(']').toString();
        }
    }
}
