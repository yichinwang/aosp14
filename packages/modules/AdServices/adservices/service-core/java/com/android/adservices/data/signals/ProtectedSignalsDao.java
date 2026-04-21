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

package com.android.adservices.data.signals;

import android.adservices.common.AdTechIdentifier;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import com.android.adservices.data.common.CleanupUtils;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * DAO abstract class used to access ProtectedSignal storage
 *
 * <p>Annotations will generate Room-based SQLite Dao impl.
 */
@Dao
public abstract class ProtectedSignalsDao {

    /**
     * Returns a list of all signals owned by the given buyer.
     *
     * @param buyer The buyer to retrieve signals for.
     * @return A list of all protected signals owned by the input buyer.
     */
    @Query("SELECT * FROM protected_signals WHERE buyer = :buyer")
    public abstract List<DBProtectedSignal> getSignalsByBuyer(AdTechIdentifier buyer);

    /**
     * Inserts signals into the database.
     *
     * @param signals The signals to insert.
     */
    @Insert
    public abstract void insertSignals(@NonNull List<DBProtectedSignal> signals);

    /**
     * Deletes signals from the database.
     *
     * @param signals The signals to delete.
     */
    @Delete
    public abstract void deleteSignals(@NonNull List<DBProtectedSignal> signals);

    /**
     * Inserts and deletes signals in a single transaction.
     *
     * @param signalsToInsert The signals to insert.
     * @param signalsToDelete The signals to delete.
     */
    @Transaction
    public void insertAndDelete(
            @NonNull List<DBProtectedSignal> signalsToInsert,
            @NonNull List<DBProtectedSignal> signalsToDelete) {
        insertSignals(signalsToInsert);
        deleteSignals(signalsToDelete);
    }

    /**
     * Deletes all signals {@code expiryTime}.
     *
     * @return the number of deleted signals
     */
    @Query("DELETE FROM protected_signals WHERE creationTime < :expiryTime")
    public abstract int deleteSignalsBeforeTime(@NonNull Instant expiryTime);

    /**
     * Deletes all signals belonging to disallowed buyer ad techs in a single transaction, where the
     * buyer ad techs cannot be found in the enrollment database.
     *
     * @return the number of deleted signals
     */
    @Transaction
    public int deleteDisallowedBuyerSignals(@NonNull EnrollmentDao enrollmentDao) {
        Objects.requireNonNull(enrollmentDao);

        List<AdTechIdentifier> buyersToRemove = getAllBuyers();
        if (buyersToRemove.isEmpty()) {
            return 0;
        }

        Set<AdTechIdentifier> enrolledAdTechs = enrollmentDao.getAllFledgeEnrolledAdTechs();
        buyersToRemove.removeAll(enrolledAdTechs);

        int numDeletedEvents = 0;
        if (!buyersToRemove.isEmpty()) {
            numDeletedEvents = deleteByBuyers(buyersToRemove);
        }

        return numDeletedEvents;
    }

    /**
     * Helper method for {@link #deleteDisallowedBuyerSignals}
     *
     * @return All buyers with signals in the DB.
     */
    @Query("SELECT DISTINCT buyer FROM protected_signals")
    protected abstract List<AdTechIdentifier> getAllBuyers();

    /**
     * Deletes all signals for the list of buyers. Helper method for {@link
     * #deleteDisallowedBuyerSignals}
     *
     * @return Number of buyers deleted.
     */
    @Query("DELETE FROM protected_signals where buyer in (:buyers)")
    protected abstract int deleteByBuyers(@NonNull List<AdTechIdentifier> buyers);

    /**
     * Deletes all signals belonging to disallowed source apps in a single transaction, where the
     * source apps cannot be found in the app package name allowlist or are not installed on the
     * device.
     *
     * @return the number of deleted signals
     */
    @Transaction
    public int deleteAllDisallowedPackageSignals(
            @NonNull PackageManager packageManager, @NonNull Flags flags) {
        Objects.requireNonNull(packageManager);
        Objects.requireNonNull(flags);

        List<String> sourceAppsToRemove = getAllPackages();
        if (sourceAppsToRemove.isEmpty()) {
            return 0;
        }

        CleanupUtils.removeAllowedPackages(sourceAppsToRemove, packageManager, flags);

        int numDeletedEvents = 0;
        if (!sourceAppsToRemove.isEmpty()) {
            numDeletedEvents = deleteSignalsByPackage(sourceAppsToRemove);
            // TODO(b/300661099): Collect and send telemetry on signal deletion
        }
        return numDeletedEvents;
    }

    /**
     * Returns the list of all unique packages in the signals table.
     *
     * <p>This method is not meant to be called externally, but is a helper for {@link
     * #deleteAllDisallowedPackageSignals}
     */
    @Query("SELECT DISTINCT packageName FROM protected_signals")
    protected abstract List<String> getAllPackages();

    /**
     * Deletes all signals generated from the given packages.
     *
     * <p>This method is not meant to be called externally, but is a helper for {@link
     * #deleteAllDisallowedPackageSignals}
     *
     * @return the number of deleted histogram events
     */
    @Query("DELETE FROM protected_signals WHERE packageName in (:packages)")
    protected abstract int deleteSignalsByPackage(@NonNull List<String> packages);
}
