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

import android.annotation.NonNull;
import android.provider.DeviceConfig;

/** Flags Implementation that delegates to DeviceConfig. */
// TODO(b/228037065): Add validation logics for Feature flags read from PH.
public final class PhFlags implements Flags {
    /*
     * Keys for ALL the flags stored in DeviceConfig.
     */
    // Killswitch keys
    static final String KEY_GLOBAL_KILL_SWITCH = "global_kill_switch";

    static final String KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS =
            "enable_ondevicepersonalization_apis";

    static final String KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE =
            "enable_personalization_status_override";

    static final String KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE =
            "personalization_status_override_value";

    static final String KEY_ISOLATED_SERVICE_DEADLINE_SECONDS =
            "isolated_service_deadline_seconds";

    static final String KEY_TRUSTED_PARTNER_APPS_LIST = "trusted_partner_apps_list";

    static final String KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED =
            "shared_isolated_process_feature_enabled";

    // OnDevicePersonalization Namespace String from DeviceConfig class
    static final String NAMESPACE_ON_DEVICE_PERSONALIZATION = "on_device_personalization";
    private static final PhFlags sSingleton = new PhFlags();

    /** Returns the singleton instance of the PhFlags. */
    @NonNull
    public static PhFlags getInstance() {
        return sSingleton;
    }

    // Group of All Killswitches
    @Override
    public boolean getGlobalKillSwitch() {
        // The priority of applying the flag values: PH (DeviceConfig), then hard-coded value.
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_GLOBAL_KILL_SWITCH,
                /* defaultValue= */ GLOBAL_KILL_SWITCH);
    }

    @Override
    public boolean isOnDevicePersonalizationApisEnabled() {
        if (getGlobalKillSwitch()) {
            return false;
        }
        // The priority of applying the flag values: PH (DeviceConfig), then user hard-coded value.
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS,
                /* defaultValue= */ ENABLE_ONDEVICEPERSONALIZATION_APIS);
    }

    @Override
    public boolean isPersonalizationStatusOverrideEnabled() {
        if (getGlobalKillSwitch()) {
            return false;
        }
        // The priority of applying the flag values: PH (DeviceConfig), then user hard-coded value.
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE,
                /* defaultValue= */ ENABLE_PERSONALIZATION_STATUS_OVERRIDE);
    }

    @Override
    public boolean getPersonalizationStatusOverrideValue() {
        if (getGlobalKillSwitch()) {
            return false;
        }
        // The priority of applying the flag values: PH (DeviceConfig), then user hard-coded value.
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE,
                /* defaultValue= */ PERSONALIZATION_STATUS_OVERRIDE_VALUE);
    }

    @Override
    public int getIsolatedServiceDeadlineSeconds() {
        // The priority of applying the flag values: PH (DeviceConfig), then user hard-coded value.
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ISOLATED_SERVICE_DEADLINE_SECONDS,
                /* defaultValue= */ ISOLATED_SERVICE_DEADLINE_SECONDS);
    }

    @Override
    public String getTrustedPartnerAppsList() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_TRUSTED_PARTNER_APPS_LIST,
                /* defaultValue */ DEFAULT_TRUSTED_PARTNER_APPS_LIST);
    }

    @Override
    public boolean isSharedIsolatedProcessFeatureEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED,
                /* defaultValue */ DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED);
    }
}
