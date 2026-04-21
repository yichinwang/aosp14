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

package com.android.federatedcompute.services.common;

import android.annotation.NonNull;
import android.os.SystemProperties;
import android.provider.DeviceConfig;

import com.android.internal.annotations.VisibleForTesting;

/** A placeholder class for PhFlag. */
public final class PhFlags implements Flags {
    /*
     * Keys for ALL the flags stored in DeviceConfig.
     */
    // Killswitch keys
    static final String KEY_FEDERATED_COMPUTE_KILL_SWITCH = "federated_compute_kill_switch";

    // SystemProperty prefix. SystemProperty is for overriding OnDevicePersonalization Configs.
    private static final String SYSTEM_PROPERTY_PREFIX = "debug.ondevicepersonalization.";

    // OnDevicePersonalization Namespace String from DeviceConfig class
    static final String NAMESPACE_ON_DEVICE_PERSONALIZATION = "on_device_personalization";

    static final String FEDERATED_COMPUTATION_ENCRYPTION_KEY_DOWNLOAD_URL =
            "fcp_encryption_key_download_url";
    private static final PhFlags sSingleton = new PhFlags();

    /** Returns the singleton instance of the PhFlags. */
    @NonNull
    public static PhFlags getInstance() {
        return sSingleton;
    }

    // Group of All Killswitches
    @Override
    public boolean getGlobalKillSwitch() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig),
        // then hard-coded value.
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_FEDERATED_COMPUTE_KILL_SWITCH),
                DeviceConfig.getBoolean(
                        /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                        /* name= */ KEY_FEDERATED_COMPUTE_KILL_SWITCH,
                        /* defaultValue= */ FEDERATED_COMPUTE_GLOBAL_KILL_SWITCH));
    }

    @VisibleForTesting
    static String getSystemPropertyName(String key) {
        return SYSTEM_PROPERTY_PREFIX + key;
    }

    @Override
    public String getEncryptionKeyFetchUrl() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FEDERATED_COMPUTATION_ENCRYPTION_KEY_DOWNLOAD_URL,
                /* defaultValue= */ ENCRYPTION_KEY_FETCH_URL
        );
    }
}
