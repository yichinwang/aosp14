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

package com.android.adservices.spe;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Enum class to store background jobs metadata. */
public enum AdservicesJobInfo {
    MAINTENANCE_JOB("MAINTENANCE_JOB", 1),

    TOPICS_EPOCH_JOB("TOPICS_EPOCH_JOB", 2),

    MEASUREMENT_EVENT_MAIN_REPORTING_JOB("MEASUREMENT_EVENT_MAIN_REPORTING_JOB", 3),

    MEASUREMENT_DELETE_EXPIRED_JOB("MEASUREMENT_DELETE_EXPIRED_JOB", 4),

    MEASUREMENT_ATTRIBUTION_JOB("MEASUREMENT_ATTRIBUTION_JOB", 5),

    MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB("MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB", 6),

    MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB("MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB", 7),

    MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB("MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB", 8),

    FLEDGE_BACKGROUND_FETCH_JOB("FLEDGE_BACKGROUND_FETCH_JOB", 9),

    CONSENT_NOTIFICATION_JOB("CONSENT_NOTIFICATION_JOB", 10),

    MDD_MAINTENANCE_PERIODIC_TASK_JOB("MDD_MAINTENANCE_PERIODIC_TASK_JOB", 11),

    MDD_CHARGING_PERIODIC_TASK_JOB("MDD_CHARGING_PERIODIC_TASK_JOB", 12),

    MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB("MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB", 13),

    MDD_WIFI_CHARGING_PERIODIC_TASK_JOB("MDD_WIFI_CHARGING_PERIODIC_TASK_JOB", 14),

    @Deprecated
    DEPRECATED_ASYNC_REGISTRATION_QUEUE_JOB("DEPRECATED_ASYNC_REGISTRATION_QUEUE_JOB", 15),

    MEASUREMENT_DELETE_UNINSTALLED_JOB("MEASUREMENT_DELETE_UNINSTALLED_JOB", 16),

    MEASUREMENT_DEBUG_REPORT_JOB("MEASUREMENT_DEBUG_REPORT_JOB", 17),

    MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB("MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB", 18),

    MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB("MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB", 19),

    MEASUREMENT_ASYNC_REGISTRATION_JOB("MEASUREMENT_ASYNC_REGISTRATION_JOB", 20),

    MEASUREMENT_ATTRIBUTION_FALLBACK_JOB("MEASUREMENT_ATTRIBUTION_FALLBACK_JOB", 21),

    FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB(
            "FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB", 22),

    COBALT_LOGGING_JOB("COBALT_LOGGING_JOB", 23),
    MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB(
            "MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB", 24),
    MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB("MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB", 25),
    FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB("FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB", 26),

    EXT_APPSEARCH_DELETE_INITIAL_SCHEDULER_JOB("EXT_APPSEARCH_DELETE_INITIAL_SCHEDULER_JOB", 27),

    EXT_APPSEARCH_DELETE_PERIODIC_SCHEDULER_JOB("EXT_APPSEARCH_DELETE_PERIODIC_SCHEDULER_JOB", 28),

    EXT_APPSEARCH_DELETE_JOB("EXT_APPSEARCH_DELETE_JOB", 28),

    PERIODIC_SIGNALS_ENCODING_JOB("PERIODIC_SIGNALS_ENCODING_JOB", 29),

    ENCRYPTION_KEY_PERIODIC_JOB("ENCRYPTION_KEY_PERIODIC_JOB", 30);

    private final String mJobServiceName;
    private final int mJobId;

    // The reverse mapping to get Job Info by Job ID.
    private static final Map<Integer, AdservicesJobInfo> JOB_ID_TO_INFO_MAP =
            Collections.unmodifiableMap(initializeMapping());

    AdservicesJobInfo(String jobServiceName, int jobId) {
        mJobServiceName = jobServiceName;
        mJobId = jobId;
    }

    /**
     * Get the job name of a job info.
     *
     * @return the job name
     */
    public String getJobServiceName() {
        return mJobServiceName;
    }

    /**
     * Get the job id of a job info.
     *
     * @return the job id
     */
    public int getJobId() {
        return mJobId;
    }

    static Map<Integer, AdservicesJobInfo> getJobIdToInfoMap() {
        return JOB_ID_TO_INFO_MAP;
    }

    private static Map<Integer, AdservicesJobInfo> initializeMapping() {
        Map<Integer, AdservicesJobInfo> map = new HashMap<>();
        for (AdservicesJobInfo info : AdservicesJobInfo.values()) {
            map.put(info.getJobId(), info);
        }

        return map;
    }
}
