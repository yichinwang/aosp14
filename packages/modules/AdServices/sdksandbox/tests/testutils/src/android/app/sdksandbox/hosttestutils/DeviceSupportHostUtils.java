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

package android.app.sdksandbox.hosttestutils;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

public class DeviceSupportHostUtils {
    private final BaseHostJUnit4Test mTest;
    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";
    private static final String FEATURE_LEANBACK = "android.software.leanback";
    private static final String FEATURE_RAM_LOW = "android.hardware.ram.low";
    private static final String FEATURE_WATCH = "android.hardware.type.watch";

    public DeviceSupportHostUtils(BaseHostJUnit4Test test) {
        mTest = test;
    }

    public boolean isSdkSandboxSupported() throws DeviceNotAvailableException {
        return !isWatch() && !isTv() && !isAuto() && !isLowRamDevice();
    }

    private boolean isWatch() throws DeviceNotAvailableException {
        return mTest.getDevice().hasFeature(FEATURE_WATCH);
    }

    private boolean isTv() throws DeviceNotAvailableException {
        return mTest.getDevice().hasFeature(FEATURE_LEANBACK);
    }

    private boolean isAuto() throws DeviceNotAvailableException {
        return mTest.getDevice().hasFeature(FEATURE_AUTOMOTIVE);
    }

    private boolean isLowRamDevice() throws DeviceNotAvailableException {
        return mTest.getDevice().hasFeature(FEATURE_RAM_LOW);
    }
}
