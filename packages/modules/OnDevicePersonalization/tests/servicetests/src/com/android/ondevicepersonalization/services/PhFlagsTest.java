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

package com.android.ondevicepersonalization.services;

import static com.android.ondevicepersonalization.services.Flags.DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED;
import static com.android.ondevicepersonalization.services.Flags.DEFAULT_TRUSTED_PARTNER_APPS_LIST;
import static com.android.ondevicepersonalization.services.Flags.ENABLE_ONDEVICEPERSONALIZATION_APIS;
import static com.android.ondevicepersonalization.services.Flags.ENABLE_PERSONALIZATION_STATUS_OVERRIDE;
import static com.android.ondevicepersonalization.services.Flags.GLOBAL_KILL_SWITCH;
import static com.android.ondevicepersonalization.services.Flags.PERSONALIZATION_STATUS_OVERRIDE_VALUE;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_GLOBAL_KILL_SWITCH;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_TRUSTED_PARTNER_APPS_LIST;

import static com.google.common.truth.Truth.assertThat;

import android.provider.DeviceConfig;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link com.android.ondevicepersonalization.service.PhFlags} */
@RunWith(AndroidJUnit4.class)
public class PhFlagsTest {
    /**
     * Get necessary permissions to access Setting.Config API and set up context
     */
    @Before
    public void setUpContext() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
    }

    @Test
    public void testGetGlobalKillSwitch() {
        // Without any overriding, the value is the hard coded constant.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(GLOBAL_KILL_SWITCH),
                /* makeDefault */ false);
        assertThat(FlagsFactory.getFlags().getGlobalKillSwitch()).isEqualTo(GLOBAL_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getGlobalKillSwitch()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testIsOnDevicePersonalizationApisEnabled() {
        PhFlagsTestUtil.disableGlobalKillSwitch();
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS,
                Boolean.toString(ENABLE_ONDEVICEPERSONALIZATION_APIS),
                /* makeDefault */ false);
        assertThat(FlagsFactory.getFlags().isOnDevicePersonalizationApisEnabled()).isEqualTo(
                ENABLE_ONDEVICEPERSONALIZATION_APIS);

        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.isOnDevicePersonalizationApisEnabled()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testIsPersonalizationStatusOverrideEnabled() {
        PhFlagsTestUtil.disableGlobalKillSwitch();
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE,
                Boolean.toString(ENABLE_PERSONALIZATION_STATUS_OVERRIDE),
                /* makeDefault */ false);
        assertThat(FlagsFactory.getFlags().isPersonalizationStatusOverrideEnabled()).isEqualTo(
                ENABLE_PERSONALIZATION_STATUS_OVERRIDE);

        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.isPersonalizationStatusOverrideEnabled()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetPersonalizationStatusOverrideValue() {
        PhFlagsTestUtil.disableGlobalKillSwitch();
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE,
                Boolean.toString(PERSONALIZATION_STATUS_OVERRIDE_VALUE),
                /* makeDefault */ false);
        assertThat(FlagsFactory.getFlags().getPersonalizationStatusOverrideValue()).isEqualTo(
                PERSONALIZATION_STATUS_OVERRIDE_VALUE);

        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getPersonalizationStatusOverrideValue()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetTrustedPartnerAppsList() {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_TRUSTED_PARTNER_APPS_LIST,
                DEFAULT_TRUSTED_PARTNER_APPS_LIST,
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().getTrustedPartnerAppsList())
                .isEqualTo(DEFAULT_TRUSTED_PARTNER_APPS_LIST);

        final String testTrustedPartnerAppsList =
                "trusted_test_app_1, trusted_test_app_2, trusted_test_app_3";

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_TRUSTED_PARTNER_APPS_LIST,
                testTrustedPartnerAppsList,
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().getTrustedPartnerAppsList())
                .isEqualTo(testTrustedPartnerAppsList);
    }

    @Test
    public void testSharedIsolatedProcessFeature() {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED,
                Boolean.toString(DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED),
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().isSharedIsolatedProcessFeatureEnabled())
                .isEqualTo(DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED);

        final boolean testIsolatedProcessFeatureEnabled =
                !DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED,
                Boolean.toString(testIsolatedProcessFeatureEnabled),
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().isSharedIsolatedProcessFeatureEnabled())
                .isEqualTo(testIsolatedProcessFeatureEnabled);
    }
}
