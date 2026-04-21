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

import static android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.data.user.LocationInfo.LocationProvider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * A collector for getting user data signals.
 * This class only exposes two public operations: periodic update, and
 * real-time update.
 * Periodic update operation will be run every 4 hours in the background,
 * given several on-device resource constraints are satisfied.
 * Real-time update operation will be run before any ads serving request
 * and update a few time-sensitive signals in UserData to the latest version.
 */
public class UserDataCollector {
    private static final int MILLISECONDS_IN_MINUTE = 60000;

    private static volatile UserDataCollector sUserDataCollector = null;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "UserDataCollector";

    @VisibleForTesting
    public static final Set<Integer> ALLOWED_NETWORK_TYPE =
            Set.of(
                    TelephonyManager.NETWORK_TYPE_UNKNOWN,
                    TelephonyManager.NETWORK_TYPE_GPRS,
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_UMTS,
                    TelephonyManager.NETWORK_TYPE_CDMA,
                    TelephonyManager.NETWORK_TYPE_EVDO_0,
                    TelephonyManager.NETWORK_TYPE_EVDO_A,
                    TelephonyManager.NETWORK_TYPE_1xRTT,
                    TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_HSUPA,
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_EVDO_B,
                    TelephonyManager.NETWORK_TYPE_LTE,
                    TelephonyManager.NETWORK_TYPE_EHRPD,
                    TelephonyManager.NETWORK_TYPE_HSPAP,
                    TelephonyManager.NETWORK_TYPE_GSM,
                    TelephonyManager.NETWORK_TYPE_TD_SCDMA,
                    TelephonyManager.NETWORK_TYPE_IWLAN,
                    TelephonyManager.NETWORK_TYPE_NR
            );

    @NonNull
    private final Context mContext;
    @NonNull
    private final TelephonyManager mTelephonyManager;
    @NonNull final ConnectivityManager mConnectivityManager;
    @NonNull
    private final LocationManager mLocationManager;
    @NonNull
    private final UserDataDao mUserDataDao;
    // Metadata to keep track of the latest ending timestamp of app usage collection.
    @NonNull
    private long mLastTimeMillisAppUsageCollected;
    // Metadata to track the expired app usage entries, which are to be evicted.
    @NonNull
    private Deque<AppUsageEntry> mAllowedAppUsageEntries;
    // Metadata to track the expired location entries, which are to be evicted.
    @NonNull
    private Deque<LocationInfo> mAllowedLocationEntries;
    // Metadata to track whether UserData has been initialized.
    @NonNull
    private boolean mInitialized;

    private UserDataCollector(Context context, UserDataDao userDataDao) {
        mContext = context;

        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mLocationManager = mContext.getSystemService(LocationManager.class);
        mUserDataDao = userDataDao;
        mLastTimeMillisAppUsageCollected = 0L;
        mAllowedAppUsageEntries = new ArrayDeque<>();
        mAllowedLocationEntries = new ArrayDeque<>();
        mInitialized = false;
    }

    /** Returns an instance of UserDataCollector. */
    public static UserDataCollector getInstance(Context context) {
        if (sUserDataCollector == null) {
            synchronized (UserDataCollector.class) {
                if (sUserDataCollector == null) {
                    sUserDataCollector = new UserDataCollector(
                            context.getApplicationContext(),
                            UserDataDao.getInstance(context.getApplicationContext()));
                }
            }
        }
        return sUserDataCollector;
    }

    /**
     * Returns an instance of the UserDataCollector given a context. This is used
     * for testing only.
     */
    @VisibleForTesting
    public static UserDataCollector getInstanceForTest(Context context) {
        synchronized (UserDataCollector.class) {
            if (sUserDataCollector == null) {
                sUserDataCollector = new UserDataCollector(context,
                        UserDataDao.getInstanceForTest(context));
            }
            return sUserDataCollector;
        }
    }

    /** Update real-time user data to the latest per request. */
    public void getRealTimeData(@NonNull RawUserData userData) {
        /**
         * Ads serving requires real-time latency. If user data has not been initialized,
         * we will skip user data collection for the incoming request and wait until the first
         * {@link UserDataCollectionJobService} to be scheduled.
         */
        if (!mInitialized) {
            return;
        }
        getUtcOffset(userData);
        getOrientation(userData);
    }

    /** Update user data per periodic job servce. */
    public void updateUserData(@NonNull RawUserData userData) {
        if (!mInitialized) {
            initializeUserData(userData);
            return;
        }
        getAvailableStorageBytes(userData);
        getBatteryPercentage(userData);
        getCarrier(userData);
        getNetworkCapabilities(userData);
        getDataNetworkType(userData);

        getInstalledApps(userData.appsInfo);
        getAppUsageStats(userData.appUsageHistory);
        getLastknownLocation(userData.locationHistory, userData.currentLocation);
        getCurrentLocation(userData.locationHistory, userData.currentLocation);
    }

    /**
     * Collects in-memory user data signals and stores in a UserData object
     * for the schedule of {@link UserDataCollectionJobService}
     */
    private void initializeUserData(@NonNull RawUserData userData) {
        getUtcOffset(userData);
        getOrientation(userData);
        getAvailableStorageBytes(userData);
        getBatteryPercentage(userData);
        getCarrier(userData);
        getNetworkCapabilities(userData);
        getDataNetworkType(userData);

        getInstalledApps(userData.appsInfo);

        recoverAppUsageHistogram(userData.appUsageHistory);

        getAppUsageStats(userData.appUsageHistory);
        // TODO (b/261748573): add non-trivial tests for location collection and histogram updates.
        recoverLocationHistogram(userData.locationHistory);

        getLastknownLocation(userData.locationHistory, userData.currentLocation);

        getCurrentLocation(userData.locationHistory, userData.currentLocation);
        mInitialized = true;
    }

    /** Collects current device's time zone in +/- offset of minutes from UTC. */
    @VisibleForTesting
    public void getUtcOffset(RawUserData userData) {
        try {
            userData.utcOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis())
                    / MILLISECONDS_IN_MINUTE;
        } catch (Exception e) {
            sLogger.w(TAG + ": Failed to collect timezone offset.");
        }
    }

    /** Collects the current device orientation. */
    @VisibleForTesting
    public void getOrientation(RawUserData userData) {
        try {
            userData.orientation = mContext.getResources().getConfiguration().orientation;
        } catch (Exception e) {
            sLogger.w(TAG + ": Failed to collect device orientation.");
        }
    }

    /** Collects available bytes and converts to MB. */
    @VisibleForTesting
    public void getAvailableStorageBytes(RawUserData userData) {
        try {
            StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
            userData.availableStorageBytes = statFs.getAvailableBytes();
        } catch (Exception e) {
            sLogger.w(TAG + ": Failed to collect availableStorageBytes.");
        }
    }

    /** Collects the battery percentage of the device. */
    @VisibleForTesting
    public void getBatteryPercentage(RawUserData userData) {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = mContext.registerReceiver(null, ifilter);

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                userData.batteryPercentage = Math.round(level * 100.0f / (float) scale);
            }
        } catch (Exception e) {
            sLogger.w(TAG + ": Failed to collect batteryPercentage.");
        }
    }

    /** Collects carrier info. */
    @VisibleForTesting
    public void getCarrier(RawUserData userData) {
        // TODO (b/307158231): handle i18n later if the carrier's name is in non-English script.
        try {
            switch (mTelephonyManager.getSimOperatorName().toUpperCase(Locale.US)) {
                case "RELIANCE JIO" -> userData.carrier = Carrier.RELIANCE_JIO;
                case "VODAFONE" -> userData.carrier = Carrier.VODAFONE;
                case "T-MOBILE - US", "T-MOBILE" -> userData.carrier = Carrier.T_MOBILE;
                case "VERIZON WIRELESS" -> userData.carrier = Carrier.VERIZON_WIRELESS;
                case "AIRTEL" -> userData.carrier = Carrier.AIRTEL;
                case "ORANGE" -> userData.carrier = Carrier.ORANGE;
                case "NTT DOCOMO" -> userData.carrier = Carrier.NTT_DOCOMO;
                case "MOVISTAR" -> userData.carrier = Carrier.MOVISTAR;
                case "AT&T" -> userData.carrier = Carrier.AT_T;
                case "TELCEL" -> userData.carrier = Carrier.TELCEL;
                case "VIVO" -> userData.carrier = Carrier.VIVO;
                case "VI" -> userData.carrier = Carrier.VI;
                case "TIM" -> userData.carrier = Carrier.TIM;
                case "O2" -> userData.carrier = Carrier.O2;
                case "TELEKOM" -> userData.carrier = Carrier.TELEKOM;
                case "CLARO BR" -> userData.carrier = Carrier.CLARO_BR;
                case "SK TELECOM" -> userData.carrier = Carrier.SK_TELECOM;
                case "MTC" -> userData.carrier = Carrier.MTC;
                case "AU" -> userData.carrier = Carrier.AU;
                case "TELE2" -> userData.carrier = Carrier.TELE2;
                case "SFR" -> userData.carrier = Carrier.SFR;
                case "ETECSA" -> userData.carrier = Carrier.ETECSA;
                case "IR-MCI (HAMRAHE AVVAL)" -> userData.carrier = Carrier.IR_MCI;
                case "KT" -> userData.carrier = Carrier.KT;
                case "TELKOMSEL" -> userData.carrier = Carrier.TELKOMSEL;
                case "IRANCELL" -> userData.carrier = Carrier.IRANCELL;
                case "MEGAFON" -> userData.carrier = Carrier.MEGAFON;
                case "TELEFONICA" -> userData.carrier = Carrier.TELEFONICA;
                default -> userData.carrier = Carrier.UNKNOWN;
            }
        } catch (Exception e) {
            sLogger.w(TAG + "Failed to collect carrier info.");
        }
    }

    /** Collects network capabilities. */
    @VisibleForTesting
    public void getNetworkCapabilities(RawUserData userData) {
        try {
            userData.networkCapabilities = mConnectivityManager.getNetworkCapabilities(
                            mConnectivityManager.getActiveNetwork());
        } catch (Exception e) {
            sLogger.w(TAG + ": Failed to collect networkCapabilities.");
        }
    }

    @VisibleForTesting
    public void getDataNetworkType(RawUserData userData) {
        try {
            int dataNetworkType = mTelephonyManager.getDataNetworkType();
            if (!ALLOWED_NETWORK_TYPE.contains(dataNetworkType)) {
                userData.dataNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            } else {
                userData.dataNetworkType = dataNetworkType;
            }
        } catch (Exception e) {
            sLogger.w(TAG + ": Failed to collect data network type.");
        }
    }

    /** Get app install and uninstall record. */
    @VisibleForTesting
    public void getInstalledApps(@NonNull List<AppInfo> appsInfo) {
        try {
            appsInfo.clear();
            PackageManager packageManager = mContext.getPackageManager();
            for (ApplicationInfo appInfo :
                    packageManager.getInstalledApplications(MATCH_UNINSTALLED_PACKAGES)) {
                AppInfo app = new AppInfo();
                app.packageName = appInfo.packageName;
                if ((appInfo.flags & ApplicationInfo.FLAG_INSTALLED) != 0) {
                    app.installed = true;
                } else {
                    app.installed = false;
                }
                appsInfo.add(app);
            }
            sLogger.d(TAG + ": Finished collecting AppInfo.");
        } catch (Exception e) {
            sLogger.w(TAG + ": Failed to collect installed AppInfo.");
        }
    }

    /**
     * Get 24-hour app usage stats from [yesterday's midnight] to [tonight's midnight],
     * write them to database, and update the [appUsageHistory] histogram.
     * Skip the current collection cycle if yesterday's stats has been collected.
     */
    @VisibleForTesting
    public void getAppUsageStats(HashMap<String, Long> appUsageHistory) {
        try {
            Calendar cal = Calendar.getInstance();
            // Obtain the 24-hour query range between [yesterday midnight] and [today midnight].
            cal.set(Calendar.MILLISECOND, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            final long endTimeMillis = cal.getTimeInMillis();

            // Skip the current collection cycle.
            if (endTimeMillis == mLastTimeMillisAppUsageCollected) {
                return;
            }

            // Collect yesterday's app usage stats.
            cal.add(Calendar.DATE, -1);
            final long startTimeMillis = cal.getTimeInMillis();
            UsageStatsManager usageStatsManager =
                            mContext.getSystemService(UsageStatsManager.class);
            final List<UsageStats> statsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST, startTimeMillis, endTimeMillis);

            List<AppUsageEntry> appUsageEntries = new ArrayList<>();
            for (UsageStats stats : statsList) {
                if (stats.getTotalTimeVisible() == 0) {
                    continue;
                }
                appUsageEntries.add(new AppUsageEntry(stats.getPackageName(),
                        startTimeMillis, endTimeMillis, stats.getTotalTimeVisible()));
            }

            // TODO(267678607): refactor the business logic when no stats is available.
            if (appUsageEntries.size() == 0) {
                return;
            }

            // Update database.
            if (!mUserDataDao.batchInsertAppUsageStatsData(appUsageEntries)) {
                return;
            }
            // Update in-memory histogram.
            updateAppUsageHistogram(appUsageHistory, appUsageEntries);
            // Update metadata if all steps succeed as a transaction.
            mLastTimeMillisAppUsageCollected = endTimeMillis;
        } catch (Exception e) {
            sLogger.w(TAG + ": Failed to collect app usage.");
        }
    }

    /**
     * Update histogram and handle TTL deletion for app usage (30 days).
     */
    private void updateAppUsageHistogram(HashMap<String, Long> appUsageHistory,
            List<AppUsageEntry> entries) {
        for (AppUsageEntry entry : entries) {
            mAllowedAppUsageEntries.add(entry);
            appUsageHistory.put(entry.packageName, appUsageHistory.getOrDefault(
                    entry.packageName, 0L) + entry.totalTimeUsedMillis);
        }
        // Backtrack 30 days
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1 * UserDataDao.TTL_IN_MEMORY_DAYS);
        final long thresholdTimeMillis = cal.getTimeInMillis();

        // TTL deletion algorithm
        while (!mAllowedAppUsageEntries.isEmpty()
                && mAllowedAppUsageEntries.peekFirst().endTimeMillis < thresholdTimeMillis) {
            AppUsageEntry evictedEntry = mAllowedAppUsageEntries.removeFirst();
            if (appUsageHistory.containsKey(evictedEntry.packageName)) {
                final long updatedTotalTime = appUsageHistory.get(
                        evictedEntry.packageName) - evictedEntry.totalTimeUsedMillis;
                if (updatedTotalTime == 0) {
                    appUsageHistory.remove(evictedEntry.packageName);
                } else {
                    appUsageHistory.put(evictedEntry.packageName, updatedTotalTime);
                }
            }
        }
    }

    /** Get last known location information. The result is immediate. */
    @VisibleForTesting
    public void getLastknownLocation(@NonNull HashMap<LocationInfo, Long> locationHistory,
            @NonNull LocationInfo locationInfo) {
        try {
            // TODO(b/290256559): Fix permissions issue.
            Location location = mLocationManager.getLastKnownLocation(
                    LocationManager.FUSED_PROVIDER);
            if (location != null) {
                if (!setLocationInfo(location, locationInfo)) {
                    return;
                }
                updateLocationHistogram(locationHistory, locationInfo);
            }
        } catch (Exception e) {
            // TODO(b/290256559): Fix permissions issue.
            sLogger.e(TAG + ": getLastKnownLocation() failed.", e);
        }
    }

    /** Get current location information. The result takes some time to generate. */
    @VisibleForTesting
    public void getCurrentLocation(@NonNull HashMap<LocationInfo, Long> locationHistory,
            @NonNull LocationInfo locationInfo) {
        try {
            // TODO(b/290256559): Fix permissions issue.
            String currentProvider = LocationManager.GPS_PROVIDER;
            if (mLocationManager.getProvider(currentProvider) == null) {
                currentProvider = LocationManager.FUSED_PROVIDER;
            }
            mLocationManager.getCurrentLocation(
                    currentProvider,
                    null,
                    mContext.getMainExecutor(),
                    location -> {
                        if (location != null) {
                            if (!setLocationInfo(location, locationInfo)) {
                                return;
                            }
                            updateLocationHistogram(locationHistory, locationInfo);
                        }
                    }
            );
        } catch (Exception e) {
            sLogger.e(TAG + ": getCurrentLocation() failed.", e);
        }
    }

    /**
     * Persist collected location info and populate the in-memory current location.
     * The method should succeed or fail as a transaction to avoid discrepancies between
     * database and memory.
     *
     * @return true if location info collection is successful, false otherwise.
     */
    private boolean setLocationInfo(Location location, LocationInfo locationInfo) {
        long timeMillis = System.currentTimeMillis() - location.getElapsedRealtimeAgeMillis();
        double truncatedLatitude = Math.round(location.getLatitude() * 10000.0) / 10000.0;
        double truncatedLongitude = Math.round(location.getLongitude() * 10000.0) / 10000.0;
        LocationInfo.LocationProvider locationProvider = LocationProvider.UNKNOWN;
        boolean isPrecise = false;

        String provider = location.getProvider();
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            locationProvider = LocationInfo.LocationProvider.GPS;
            isPrecise = true;
        } else {
            if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
                locationProvider = LocationInfo.LocationProvider.NETWORK;
            }
        }

        if (!mUserDataDao.insertLocationHistoryData(timeMillis, Double.toString(truncatedLatitude),
                Double.toString(truncatedLongitude), locationProvider.ordinal(), isPrecise)) {
            return false;
        }
        // update user's current location
        locationInfo.timeMillis = timeMillis;
        locationInfo.latitude = truncatedLatitude;
        locationInfo.longitude = truncatedLongitude;
        locationInfo.provider = locationProvider;
        locationInfo.isPreciseLocation = isPrecise;
        return true;
    }

    /**
     * Update histogram and handle TTL deletion for location history (30 days).
     */
    private void updateLocationHistogram(HashMap<LocationInfo, Long> locationHistory,
            LocationInfo newLocation) {
        LocationInfo curLocation = mAllowedLocationEntries.peekLast();
        // must be a deep copy
        mAllowedLocationEntries.add(new LocationInfo(newLocation));
        if (curLocation != null) {
            long durationMillis = newLocation.timeMillis - curLocation.timeMillis;
            locationHistory.put(curLocation,
                    locationHistory.getOrDefault(curLocation, 0L) + durationMillis);
        }

        // Backtrack 30 days
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1 * UserDataDao.TTL_IN_MEMORY_DAYS);
        final long thresholdTimeMillis = cal.getTimeInMillis();

        // TTL deletion algorithm for locations
        while (!mAllowedLocationEntries.isEmpty()
                && mAllowedLocationEntries.peekFirst().timeMillis < thresholdTimeMillis) {
            LocationInfo evictedLocation = mAllowedLocationEntries.removeFirst();
            if (!mAllowedLocationEntries.isEmpty()) {
                long evictedDuration = mAllowedLocationEntries.peekFirst().timeMillis
                        - evictedLocation.timeMillis;
                if (locationHistory.containsKey(evictedLocation)) {
                    long updatedDuration = locationHistory.get(evictedLocation) - evictedDuration;
                    if (updatedDuration == 0) {
                        locationHistory.remove(evictedLocation);
                    } else {
                        locationHistory.put(evictedLocation, updatedDuration);
                    }
                }
            }
        }
    }

    /**
     * Util to reset all fields in [UserData] to default for testing purpose
     */
    public void clearUserData(@NonNull RawUserData userData) {
        userData.utcOffset = 0;
        userData.orientation = Configuration.ORIENTATION_PORTRAIT;
        userData.availableStorageBytes = 0;
        userData.batteryPercentage = 0;
        userData.carrier = Carrier.UNKNOWN;
        userData.networkCapabilities = null;
        userData.appsInfo.clear();
        userData.appUsageHistory.clear();
        userData.locationHistory.clear();
    }

    /**
     * Util to reset all in-memory metadata for testing purpose.
     */
    public void clearMetadata() {
        mInitialized = false;
        mLastTimeMillisAppUsageCollected = 0L;
        mAllowedAppUsageEntries = new ArrayDeque<>();
        mAllowedLocationEntries = new ArrayDeque<>();
    }

    /**
     * Reset app usage histogram and metadata in case of system crash.
     * Only used during initial data collection.
     */
    @VisibleForTesting
    public void recoverAppUsageHistogram(HashMap<String, Long> appUsageHistory) {
        Cursor cursor = mUserDataDao.readAppUsageInLastXDays(UserDataDao.TTL_IN_MEMORY_DAYS);
        if (cursor == null) {
            return;
        }
        // Metadata to be reset.
        appUsageHistory.clear();
        mLastTimeMillisAppUsageCollected = 0L;
        mAllowedAppUsageEntries.clear();

        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                String packageName = cursor.getString(cursor.getColumnIndex(
                        UserDataTables.AppUsageHistory.PACKAGE_NAME));
                long startTimeMillis = cursor.getLong(cursor.getColumnIndex(
                        UserDataTables.AppUsageHistory.STARTING_TIME_SEC));
                long endTimeMillis = cursor.getLong(cursor.getColumnIndex(
                        UserDataTables.AppUsageHistory.ENDING_TIME_SEC));
                long totalTimeUsedMillis = cursor.getLong(cursor.getColumnIndex(
                        UserDataTables.AppUsageHistory.TOTAL_TIME_USED_SEC));
                mAllowedAppUsageEntries.add(new AppUsageEntry(packageName,
                        startTimeMillis, endTimeMillis, totalTimeUsedMillis));
                appUsageHistory.put(packageName, appUsageHistory.getOrDefault(packageName,
                        0L) + totalTimeUsedMillis);
                cursor.moveToNext();
            }
        }

        if (cursor.moveToLast()) {
            mLastTimeMillisAppUsageCollected = cursor.getLong(cursor.getColumnIndex(
                    UserDataTables.AppUsageHistory.ENDING_TIME_SEC));
        }
    }

    /**
     * Reset location histogram and metadata in case of system crash.
     */
    @VisibleForTesting
    public void recoverLocationHistogram(HashMap<LocationInfo, Long> locationHistory) {
        Cursor cursor = mUserDataDao.readLocationInLastXDays(mUserDataDao.TTL_IN_MEMORY_DAYS);
        if (cursor == null) {
            return;
        }
        // Metadata to be reset.
        locationHistory.clear();
        mAllowedLocationEntries.clear();

        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                long timeMillis = cursor.getLong(cursor.getColumnIndex(
                        UserDataTables.LocationHistory.TIME_SEC));
                String latitude = cursor.getString(cursor.getColumnIndex(
                        UserDataTables.LocationHistory.LATITUDE));
                String longitude = cursor.getString(cursor.getColumnIndex(
                        UserDataTables.LocationHistory.LONGITUDE));
                int source = cursor.getInt(cursor.getColumnIndex(
                        UserDataTables.LocationHistory.SOURCE));
                boolean isPrecise = cursor.getInt(cursor.getColumnIndex(
                        UserDataTables.LocationHistory.IS_PRECISE)) > 0;
                mAllowedLocationEntries.add(new LocationInfo(timeMillis,
                        Double.parseDouble(latitude),
                        Double.parseDouble(longitude),
                        LocationProvider.fromInteger(source),
                        isPrecise));
                cursor.moveToNext();
            }
        }

        Iterator<LocationInfo> iterator = mAllowedLocationEntries.iterator();
        while (iterator.hasNext()) {
            LocationInfo currentLocation = iterator.next();
            if (!iterator.hasNext()) {
                return;
            }
            LocationInfo nextLocation = iterator.next();
            final long duration = nextLocation.timeMillis - currentLocation.timeMillis;
            if (duration < 0) {
                sLogger.v(TAG + ": LocationInfo entries are retrieved with wrong order.");
            }
            locationHistory.put(currentLocation,
                    locationHistory.getOrDefault(currentLocation, 0L) + duration);
        }
    }

    @VisibleForTesting
    public boolean isInitialized() {
        return mInitialized;
    }

    @VisibleForTesting
    public long getLastTimeMillisAppUsageCollected() {
        return mLastTimeMillisAppUsageCollected;
    }

    @VisibleForTesting
    public Deque<AppUsageEntry> getAllowedAppUsageEntries() {
        return mAllowedAppUsageEntries;
    }

    @VisibleForTesting
    public Deque<LocationInfo> getAllowedLocationEntries() {
        return mAllowedLocationEntries;
    }

    /**
     * Clear all user data in database for testing purpose.
     */
    public void clearDatabase() {
        mUserDataDao.clearUserData();
    }
}
