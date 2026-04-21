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

import java.util.concurrent.TimeUnit;

/** FederatedCompute feature flags interface. This Flags interface hold the default values */
public interface Flags {
    /**
     * Global FederatedCompute APK Kill Switch. This overrides all other killswitches under
     * federatedcompute APK. The default value is false which means FederatedCompute is enabled.
     * This flag is used for emergency turning off.
     */
    boolean FEDERATED_COMPUTE_GLOBAL_KILL_SWITCH = true;

    default boolean getGlobalKillSwitch() {
        return FEDERATED_COMPUTE_GLOBAL_KILL_SWITCH;
    }

    /**
     * Flags for {@link
     * com.android.federatedcompute.services.scheduling.FederatedComputeJobManager}.
     */
    long DEFAULT_SCHEDULING_PERIOD_SECS = 60 * 5; // 5 minutes

    default long getDefaultSchedulingPeriodSecs() {
        return DEFAULT_SCHEDULING_PERIOD_SECS;
    }

    long MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION = 1 * 60; // 1 min

    default long getMinSchedulingIntervalSecsForFederatedComputation() {
        return MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION;
    }

    long MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION =
            6 * 24 * 60 * 60; // 6 days (< default ttl 7d)

    default long getMaxSchedulingIntervalSecsForFederatedComputation() {
        return MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION;
    }

    long MAX_SCHEDULING_PERIOD_SECS = 60 * 60 * 24 * 2; // 2 days

    default long getMaxSchedulingPeriodSecs() {
        return MAX_SCHEDULING_PERIOD_SECS;
    }

    long TRAINING_TIME_FOR_LIVE_SECONDS = 7 * 24 * 60 * 60; // one week

    default long getTrainingTimeForLiveSeconds() {
        return TRAINING_TIME_FOR_LIVE_SECONDS;
    }

    long TRAINING_SERVICE_RESULT_CALLBACK_TIMEOUT_SEC =
            60 * 9 + 45; // 9 minutes 45 seconds, leaving ~15 seconds to clean up.

    default long getTrainingServiceResultCallbackTimeoutSecs() {
        return TRAINING_SERVICE_RESULT_CALLBACK_TIMEOUT_SEC;
    }

    float TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT = 0.2f;

    default float getTransientErrorRetryDelayJitterPercent() {
        return TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT;
    }

    long TRANSIENT_ERROR_RETRY_DELAY_SECS = 15 * 60; // 15 minutes

    default long getTransientErrorRetryDelaySecs() {
        return TRANSIENT_ERROR_RETRY_DELAY_SECS;
    }

    /** Flags for ExampleStoreService. */
    long APP_HOSTED_EXAMPLE_STORE_TIMEOUT_SECS = 30;

    default long getAppHostedExampleStoreTimeoutSecs() {
        return APP_HOSTED_EXAMPLE_STORE_TIMEOUT_SECS;
    }

    /** Flags for ResultHandlingService. */
    long RESULT_HANDLING_BIND_SERVICE_TIMEOUT_SECS = 10;

    default long getResultHandlingBindServiceTimeoutSecs() {
        return RESULT_HANDLING_BIND_SERVICE_TIMEOUT_SECS;
    }

    // 9 minutes 45 seconds, leaving ~15 seconds to clean up.
    long RESULT_HANDLING_SERVICE_CALLBACK_TIMEOUT_SECS = 60 * 9 + 45;

    default long getResultHandlingServiceCallbackTimeoutSecs() {
        return RESULT_HANDLING_SERVICE_CALLBACK_TIMEOUT_SECS;
    }

    /**
     * The minimum percentage (expressed as an integer between 0 and 100) of battery charge that
     * must be remaining in order start training as well as continue it once started.
     */
    int TRAINING_MIN_BATTERY_LEVEL = 30;

    default int getTrainingMinBatteryLevel() {
        return TRAINING_MIN_BATTERY_LEVEL;
    }

    /**
     * The thermal status reported by `PowerManager#getCurrentThermalStatus()` at which to interrupt
     * training. Must be one of:
     *
     * <p>THERMAL_STATUS_NONE = 0;<br>
     * THERMAL_STATUS_LIGHT = 1; <br>
     * THERMAL_STATUS_MODERATE = 2; <br>
     * THERMAL_STATUS_SEVERE = 3; <br>
     * THERMAL_STATUS_CRITICAL = 4;<br>
     * THERMAL_STATUS_EMERGENCY = 5; <br>
     * THERMAL_STATUS_SHUTDOWN = 6; <br>
     */
    int THERMAL_STATUS_TO_THROTTLE = 2;

    default int getThermalStatusToThrottle() {
        return THERMAL_STATUS_TO_THROTTLE;
    }

    /** The minimum duration between two training condition checks in milliseconds. */
    long TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS = 1000;

    default long getTrainingConditionCheckThrottlePeriodMillis() {
        return TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS;
    }

    String ENCRYPTION_KEY_FETCH_URL =
            "https://fake-coordinator/v1alpha/publicKeys";

    /**
     * @return Url to fetch encryption key for federated compute.
     */
    default String getEncryptionKeyFetchUrl() {
        return ENCRYPTION_KEY_FETCH_URL;
    }

    Long FEDERATED_COMPUTE_ENCRYPTION_KEY_MAX_AGE_SECONDS =
            TimeUnit.DAYS.toSeconds(14/* duration= */);

    /**
     * @return default max age in seconds for federated compute ecryption keys.
     */
    default Long getFederatedComputeEncryptionKeyMaxAgeSeconds() {
        return FEDERATED_COMPUTE_ENCRYPTION_KEY_MAX_AGE_SECONDS;
    }

    Long ENCRYPTION_KEY_FETCH_PERIOD_SECONDS = 60 * 60 * 24L; // every 24 h

    default Long getEncryptionKeyFetchPeriodSeconds() {
        return ENCRYPTION_KEY_FETCH_PERIOD_SECONDS;
    }
}
