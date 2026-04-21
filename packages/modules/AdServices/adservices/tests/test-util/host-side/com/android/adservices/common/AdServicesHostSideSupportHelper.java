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

import static com.android.adservices.common.TestDeviceHelper.call;

/** Helper to check if AdServices is supported / enabled in a device. */
final class AdServicesHostSideSupportHelper extends AbstractDeviceSupportHelper {

    private static final AdServicesHostSideSupportHelper sInstance =
            new AdServicesHostSideSupportHelper();

    public static final AdServicesHostSideSupportHelper getInstance() {
        return sInstance;
    }

    @Override
    protected boolean hasPackageManagerFeature(String feature) {
        return call(device -> device.hasFeature(feature));
    }

    @Override
    protected boolean isLowRamDeviceByDefault() {
        return "true".equals(call(device -> device.getProperty("ro.config.low_ram")));
    }

    @Override
    protected boolean isDebuggable() {
        return "1".equals(call(device -> device.getProperty("ro.debuggable")));
    }

    private AdServicesHostSideSupportHelper() {
        super(ConsoleLogger.getInstance(), HostSideSystemPropertiesHelper.getInstance());
    }
}
