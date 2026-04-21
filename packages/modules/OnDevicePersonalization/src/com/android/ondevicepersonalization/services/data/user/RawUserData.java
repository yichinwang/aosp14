/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.data.user;

import android.adservices.ondevicepersonalization.UserData;
import android.content.res.Configuration;
import android.net.NetworkCapabilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A singleton class that holds all most recent in-memory user signals.
 */
public final class RawUserData {

    private static RawUserData sUserData = null;

    // The device time zone +/- offset in minute from UTC.
    public int utcOffset = 0;

    // The device orientation.
    public int orientation = Configuration.ORIENTATION_PORTRAIT;

    // Available storage in bytes.
    public long availableStorageBytes = 0;

    // Battery percentage.
    public int batteryPercentage = 0;

    // Mobile carrier.
    public Carrier carrier = Carrier.UNKNOWN;

    public NetworkCapabilities networkCapabilities;

    @UserData.NetworkType public int dataNetworkType;

    // installed packages.
    public List<AppInfo> appsInfo = new ArrayList<>();

    // A histogram of app usage: total times used per app in the last 30 days.
    public HashMap<String, Long> appUsageHistory = new HashMap<>();

    // User's most recently available location information.
    public LocationInfo currentLocation = new LocationInfo();

    /**
     * A histogram of location history: total time spent per location in the last 30 days.
     * Default precision level of locations is set to E4.
     */
    public HashMap<LocationInfo, Long> locationHistory = new HashMap<>();

    private RawUserData() { }

    /** Returns an instance of UserData. */
    public static RawUserData getInstance() {
        synchronized (RawUserData.class) {
            if (sUserData == null) {
                sUserData = new RawUserData();
            }
            return sUserData;
        }
    }
}
