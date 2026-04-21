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
package com.android.adservices.service.common;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

// NOTE: public because of dump()
/** Class used to log metrics of how apps are using the app config on manifest. */
public final class AppManifestConfigMetricsLogger {

    @VisibleForTesting
    static final String PREFS_NAME =
            FileCompatUtils.getAdservicesFilename("AppManifestConfigMetricsLogger");

    private static final int NOT_SET = -1;
    private static final int FLAG_APP_EXISTS = 0x1;
    private static final int FLAG_APP_HAS_CONFIG = 0x2;
    private static final int FLAG_ENABLED_BY_DEFAULT = 0x4;

    /** Logs the app usage. */
    @VisibleForTesting // TODO(b/310270746): remove public when TopicsServiceImplTest is refactored
    public static void logUsage(AppManifestConfigCall call) {
        Objects.requireNonNull(call, "call cannot be null");
        AdServicesExecutors.getBackgroundExecutor().execute(() -> handleLogUsage(call));
    }

    private static void handleLogUsage(AppManifestConfigCall call) {
        Context context = ApplicationContextSingleton.get();
        try {
            int newValue =
                    (call.appExists ? FLAG_APP_EXISTS : 0)
                            | (call.appHasConfig ? FLAG_APP_HAS_CONFIG : 0)
                            | (call.enabledByDefault ? FLAG_ENABLED_BY_DEFAULT : 0);
            LogUtil.d(
                    "AppManifestConfigMetricsLogger.logUsage(): app=[name=%s, exists=%b,"
                            + " hasConfig=%b], enabledByDefault=%b, newValue=%d",
                    call.packageName,
                    call.appExists,
                    call.appHasConfig,
                    call.enabledByDefault,
                    newValue);

            SharedPreferences prefs = getPrefs(context);
            String key = call.packageName;

            int currentValue = prefs.getInt(key, NOT_SET);
            if (currentValue == NOT_SET) {
                LogUtil.v("Logging for the first time (value=%d)", newValue);
            } else if (currentValue != newValue) {
                LogUtil.v("Logging as value change (was %d)", currentValue);
            } else {
                LogUtil.v("Value didn't change, don't need to log");
                return;
            }

            // TODO(b/306417555): upload metrics first (and unit test it) - it should mask the
            // package name
            Editor editor = prefs.edit().putInt(key, newValue);

            if (editor.commit()) {
                LogUtil.v("Changes committed");
            } else {
                LogUtil.e(
                        "logUsage(ctx, file=%s, app=%s, appExist=%b, appHasConfig=%b,"
                                + " enabledByDefault=%b, newValue=%d): failed to commit",
                        PREFS_NAME,
                        call.packageName,
                        call.appExists,
                        call.appHasConfig,
                        call.enabledByDefault,
                        newValue);
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            }
        } catch (Exception e) {
            LogUtil.e(
                    e,
                    "logUsage(ctx, file=%s, app=%s, appExist=%b, appHasConfig=%b,"
                            + " enabledByDefault=%b) failed",
                    PREFS_NAME,
                    call.packageName,
                    call.appExists,
                    call.appHasConfig,
                    call.enabledByDefault);
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_EXCEPTION,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        }
    }

    /** Dumps the internal state. */
    public static void dump(Context context, PrintWriter pw) {
        pw.println("AppManifestConfigMetricsLogger");

        String prefix = "  ";
        @SuppressWarnings("NewAdServicesFile") // PREFS_NAME already called FileCompatUtils
        // NOTE: shared_prefs is hard-coded on ContextImpl, but unfortunately Context doesn't offer
        // any API we could use here to get that path (getSharedPreferencesPath() is @removed and
        // the available APIs return a SharedPreferences, not a File).
        String path =
                new File(context.getDataDir() + "/shared_prefs", PREFS_NAME).getAbsolutePath();
        pw.printf("%sPreferences file: %s.xml\n", prefix, path);

        boolean flagEnabledByDefault =
                FlagsFactory.getFlags().getAppConfigReturnsEnabledByDefault();
        pw.printf("%s(Currently) enabled by default: %b\n", prefix, flagEnabledByDefault);

        SharedPreferences prefs = getPrefs(context);
        Map<String, ?> appPrefs = prefs.getAll();
        pw.printf("%s%d entries:\n", prefix, appPrefs.size());

        String prefix2 = prefix + "  ";
        for (Entry<String, ?> pref : appPrefs.entrySet()) {
            String app = pref.getKey();
            Object value = pref.getValue();
            if (value instanceof Integer) {
                int flags = (Integer) value;
                boolean appExists = (flags & FLAG_APP_EXISTS) != 0;
                boolean appHasConfig = (flags & FLAG_APP_HAS_CONFIG) != 0;
                boolean enabledByDefault = (flags & FLAG_ENABLED_BY_DEFAULT) != 0;
                pw.printf(
                        "%s%s: rawValue=%d, appExists=%b, appHasConfig=%b, enabledByDefault=%b\n",
                        prefix2, app, flags, appExists, appHasConfig, enabledByDefault);
            } else {
                // Shouldn't happen
                pw.printf("  %s: unexpected value %s (class %s):\n", app, value, value.getClass());
            }
        }
    }

    @SuppressWarnings("NewAdServicesFile") // PREFS_NAME already called FileCompatUtils
    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private AppManifestConfigMetricsLogger() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}
