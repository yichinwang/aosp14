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

package com.android.adservices.service.consent;

import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_TRUE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_MANUAL_INTERACTIONS_RECORDED;

import android.adservices.extdata.AdServicesExtDataParams;
import android.annotation.NonNull;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.service.appsearch.AppSearchConsentManager;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.adservices.service.extdata.AdServicesExtDataStorageServiceManager;
import com.android.modules.utils.build.SdkLevel;

import java.util.Objects;

/** Utility class for consent migration related tasks. */
public final class ConsentMigrationUtils {
    private ConsentMigrationUtils() {
        // prevent instantiation
    }

    /**
     * This method handles migration of consent data to AppSearch post-OTA R -> S. Consent data is
     * written to AdServicesExtDataStorageService on R and ported over to AppSearch after OTA to S
     * as it's the new consent source of truth. If any new data is written for consent, we need to
     * make sure it is migrated correctly post-OTA in this method.
     */
    public static void handleConsentMigrationToAppSearchIfNeeded(
            @NonNull Context context,
            @NonNull BooleanFileDatastore datastore,
            @Nullable AppSearchConsentManager appSearchConsentManager,
            @Nullable AdServicesExtDataStorageServiceManager adExtDataManager) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(datastore);
        LogUtil.d("Check if consent migration to AppSearch is needed.");

        // TODO (b/306753680): Add consent migration logging.
        try {
            SharedPreferences sharedPreferences =
                    FileCompatUtils.getSharedPreferencesHelper(
                            context, ConsentConstants.SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);

            if (!isMigrationToAppSearchNeeded(
                    context, sharedPreferences, appSearchConsentManager, adExtDataManager)) {
                LogUtil.d("Skipping consent migration to AppSearch");
                return;
            }

            // Reduce number of read calls by fetching all the AdExt data at once.
            AdServicesExtDataParams dataFromR = adExtDataManager.getAdServicesExtData();
            if (dataFromR.getIsNotificationDisplayed() != BOOLEAN_TRUE) {
                LogUtil.d("Skipping consent migration to AppSearch; notification not shown on R");
                return;
            }

            migrateDataToAppSearch(appSearchConsentManager, dataFromR, datastore);

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED_TO_APP_SEARCH, true);
            if (editor.commit()) {
                LogUtil.d("Finished migrating consent to AppSearch.");
            } else {
                LogUtil.e("Finished migrating consent to AppSearch. Shared prefs not updated.");
            }

            // No longer need access to Android R data. Safe to clear here.
            adExtDataManager.clearDataOnOtaAsync();
        } catch (Exception e) {
            LogUtil.e("Consent migration to AppSearch failed: ", e);
        }
    }

    private static boolean isMigrationToAppSearchNeeded(
            Context context,
            SharedPreferences sharedPreferences,
            AppSearchConsentManager appSearchConsentManager,
            AdServicesExtDataStorageServiceManager adExtDataManager) {
        if (SdkLevel.isAtLeastT() || !SdkLevel.isAtLeastS()) {
            LogUtil.d("Not S device. Consent migration to AppSearch not needed");
            return false;
        }

        // Cannot be null on S since the consent source of truth has to be APPSEARCH_ONLY.
        Objects.requireNonNull(appSearchConsentManager);

        // There could be a case where we may need to ramp down enable_adext_service_consent_data
        // flag on S, in which case we should gracefully handle consent migration by skipping.
        if (adExtDataManager == null) {
            LogUtil.d("AdExtDataManager is null. Consent migration to AppSearch not needed");
            return false;
        }

        boolean isMigrationToAppSearchDone =
                sharedPreferences.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED_TO_APP_SEARCH,
                        /* defValue= */ false);
        if (isMigrationToAppSearchDone) {
            LogUtil.d(
                    "Consent migration to AppSearch is already done for user %d.",
                    context.getUser().getIdentifier());
            return false;
        }

        // Just in case, check all notification types to ensure notification is not shown. We do not
        // want to override consent if notification is already shown.
        boolean isNotificationDisplayedOnS =
                appSearchConsentManager.wasU18NotificationDisplayed()
                        || appSearchConsentManager.wasNotificationDisplayed()
                        || appSearchConsentManager.wasGaUxNotificationDisplayed();
        LogUtil.d(
                "Notification shown status on S for migrating consent to AppSearch: "
                        + isNotificationDisplayedOnS);

        // If notification is not shown, we will need to perform another check to ensure
        // notification was shown on R before performing migration. This check will be performed
        // later in order to reduce number of calls to AdExtDataService in the consent migration
        // process.
        return !isNotificationDisplayedOnS;
    }

    @TargetApi(Build.VERSION_CODES.S)
    private static void migrateDataToAppSearch(
            AppSearchConsentManager appSearchConsentManager,
            AdServicesExtDataParams dataFromR,
            BooleanFileDatastore datastore) {
        // Default measurement consent is stored using PPAPI_ONLY source on R.
        Boolean measurementDefaultConsent =
                datastore.get(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT);
        if (measurementDefaultConsent != null) {
            appSearchConsentManager.setConsent(
                    ConsentConstants.MEASUREMENT_DEFAULT_CONSENT, measurementDefaultConsent);
        }

        boolean isMeasurementConsented = dataFromR.getIsMeasurementConsented() == BOOLEAN_TRUE;
        appSearchConsentManager.setConsent(
                AdServicesApiType.MEASUREMENTS.toPpApiDatastoreKey(), isMeasurementConsented);

        appSearchConsentManager.setU18NotificationDisplayed(
                dataFromR.getIsNotificationDisplayed() == BOOLEAN_TRUE);

        // Record interaction data only if we recorded an interaction in
        // AdServicesExtDataStorageService.
        int manualInteractionRecorded = dataFromR.getManualInteractionWithConsentStatus();
        if (manualInteractionRecorded == STATE_MANUAL_INTERACTIONS_RECORDED) {
            appSearchConsentManager.recordUserManualInteractionWithConsent(
                    manualInteractionRecorded);
        }

        if (dataFromR.getIsU18Account() != BOOLEAN_UNKNOWN) {
            appSearchConsentManager.setU18Account(dataFromR.getIsU18Account() == BOOLEAN_TRUE);
        }

        if (dataFromR.getIsAdultAccount() != BOOLEAN_UNKNOWN) {
            appSearchConsentManager.setAdultAccount(dataFromR.getIsAdultAccount() == BOOLEAN_TRUE);
        }
    }
}
