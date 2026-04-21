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

import static com.android.adservices.common.DeviceSideDeviceConfigHelper.callWithDeviceConfigPermissions;
import static com.android.adservices.service.FlagsConstants.KEY_APP_CONFIG_RETURNS_ENABLED_BY_DEFAULT;
import static com.android.adservices.service.FlagsConstants.KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SELECT_ADS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_STATUS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ENABLE_SESSION_STABLE_KILL_SWITCHES;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;

import android.os.Build;

import com.android.adservices.experimental.AbstractFlagsRouletteRunner;
import com.android.adservices.experimental.AbstractFlagsRouletteRunner.FlagsRouletteState;
import com.android.adservices.service.Flags;
import com.android.adservices.service.PhFlags;
import com.android.modules.utils.build.SdkLevel;

public final class AdServicesFlagsSetterRule
        extends AbstractAdServicesFlagsSetterRule<AdServicesFlagsSetterRule> {

    private AdServicesFlagsSetterRule() {
        super(
                AndroidLogger.getInstance(),
                DeviceSideDeviceConfigHelper::new,
                DeviceSideSystemPropertiesHelper.getInstance());
    }

    private static AdServicesFlagsSetterRule withDefaultLogcatTags() {
        return new AdServicesFlagsSetterRule().setDefaultLogcatTags();
    }

    private static AdServicesFlagsSetterRule withAllLogcatTags() {
        return new AdServicesFlagsSetterRule().setAllLogcatTags();
    }

    /** Factory method that only disables the global kill switch. */
    public static AdServicesFlagsSetterRule forGlobalKillSwitchDisabledTests() {
        return withDefaultLogcatTags().setGlobalKillSwitch(false);
    }

    /** Factory method that disables all major API kill switches. */
    public static AdServicesFlagsSetterRule forAllApisEnabledTests() {
        return withAllLogcatTags()
                .setGlobalKillSwitch(false)
                .setTopicsKillSwitch(false)
                .setAdIdKillSwitchForTests(false)
                .setSystemProperty(KEY_MEASUREMENT_KILL_SWITCH, false)
                .setSystemProperty(KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH, false)
                .setSystemProperty(KEY_FLEDGE_SELECT_ADS_KILL_SWITCH, false);
    }

    // TODO(b/297085722): pass clearFlags() on forGlobalKillSwitchDisabledTests() by default?
    /** Factory method that clears all flags then disables the global kill switch. */
    public static AdServicesFlagsSetterRule forGlobalKillSwitchDisabledOnClearSlateTests() {
        return withDefaultLogcatTags().clearFlags().setGlobalKillSwitch(false);
    }

    /** Factory method for Topics end-to-end CTS tests. */
    public static AdServicesFlagsSetterRule forTopicsE2ETests() {
        return forGlobalKillSwitchDisabledOnClearSlateTests()
                .setLogcatTag(LOGCAT_TAG_TOPICS, LOGCAT_LEVEL_VERBOSE)
                .setTopicsKillSwitch(false)
                .setTopicsOnDeviceClassifierKillSwitch(false)
                .setFlag(KEY_APP_CONFIG_RETURNS_ENABLED_BY_DEFAULT, true)
                .setFlag(KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES, true)
                .setFlag(KEY_ENABLE_ENROLLMENT_TEST_SEED, true)
                .setDisableTopicsEnrollmentCheckForTests(true)
                .setConsentManagerDebugMode(true)
                .setCompatModeFlags();
    }

    /** Factory method for Measurement E2E CTS tests */
    public static AdServicesFlagsSetterRule forMeasurementE2ETests(String packageName) {
        return forGlobalKillSwitchDisabledTests()
                .setLogcatTag(LOGCAT_TAG_MEASUREMENT, LOGCAT_LEVEL_VERBOSE)
                .setCompatModeFlags()
                .setMsmtApiAppAllowList(packageName)
                .setMsmtWebContextClientAllowList(packageName)
                .setConsentManagerDebugMode(true)
                .setConsentNotifiedDebugMode(true)
                .setSystemProperty(KEY_GLOBAL_KILL_SWITCH, false)
                .setSystemProperty(KEY_MEASUREMENT_KILL_SWITCH, false)
                .setSystemProperty(KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH, false)
                .setSystemProperty(KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH, false)
                .setSystemProperty(KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH, false)
                .setSystemProperty(KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH, false)
                .setSystemProperty(KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH, false)
                .setSystemProperty(KEY_MEASUREMENT_API_STATUS_KILL_SWITCH, false)
                .setFlag(KEY_MEASUREMENT_ENABLE_SESSION_STABLE_KILL_SWITCHES, false)
                .setAdIdKillSwitchForTests(false);
    }

    /** Factory method for Topics CB tests */
    public static AdServicesFlagsSetterRule forTopicsPerfTests(
            long epochPeriodMs, int pctRandomTopic) {
        return forGlobalKillSwitchDisabledTests()
                .setLogcatTag(LOGCAT_TAG_TOPICS, LOGCAT_LEVEL_VERBOSE)
                .setTopicsKillSwitch(false)
                .setConsentManagerDebugMode(true)
                .setDisableTopicsEnrollmentCheckForTests(true)
                .setTopicsEpochJobPeriodMsForTests(epochPeriodMs)
                .setTopicsPercentageForRandomTopicForTests(pctRandomTopic)
                .setCompatModeFlags();
    }

    /** Factory method for AdservicesCommonManager end-to-end CTS tests. */
    public static AdServicesFlagsSetterRule forCommonManagerE2ETests(String packageName) {
        return withDefaultLogcatTags().setCompatModeFlags().setPpapiAppAllowList(packageName);
    }

    // NOTE: add more factory methods as needed

    @Override
    protected int getDeviceSdk() {
        return Build.VERSION.SDK_INT;
    }

    @Override
    protected boolean isAtLeastS() {
        return SdkLevel.isAtLeastS();
    }

    @Override
    protected boolean isAtLeastT() {
        return SdkLevel.isAtLeastT();
    }

    @Override
    protected boolean isFlagManagedByRunner(String flag) {
        FlagsRouletteState roulette = AbstractFlagsRouletteRunner.getFlagsRouletteState();
        if (roulette == null || !roulette.flagNames.contains(flag)) {
            return false;
        }
        mLog.w(
                "Not setting flag %s as it's managed by %s (which manages %s)",
                flag, roulette.runnerName, roulette.flagNames);
        return true;
    }

    // NOTE: currently only used by device-side tests, so it's added directly here in order to use
    // the logic defined by PhFlags - if needed by hostside, we'll have to move it up and
    // re-implement that logic there.
    /** Calls {@link PhFlags#getAdIdRequestPerSecond()} with the proper permissions. */
    public float getAdIdRequestPerSecond() {
        try {
            return callWithDeviceConfigPermissions(
                    () -> PhFlags.getInstance().getAdIdRequestPermitsPerSecond());
        } catch (Throwable t) {
            float defaultValue = Flags.ADID_REQUEST_PERMITS_PER_SECOND;
            mLog.e(
                    t,
                    "FlagsConstants.getAdIdRequestPermitsPerSecond() failed, returning default"
                            + " value (%f)",
                    defaultValue);
            return defaultValue;
        }
    }
}
