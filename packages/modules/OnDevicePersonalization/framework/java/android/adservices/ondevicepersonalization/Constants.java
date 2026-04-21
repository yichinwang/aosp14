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

package android.adservices.ondevicepersonalization;

/**
 * Constants used internally in the OnDevicePersonalization Module and not used in public APIs.
 *
 * @hide
 */
public class Constants {
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_INTERNAL_ERROR = 100;
    public static final int STATUS_NAME_NOT_FOUND = 101;
    public static final int STATUS_CLASS_NOT_FOUND = 102;
    public static final int STATUS_SERVICE_FAILED = 103;
    public static final int STATUS_PERSONALIZATION_DISABLED = 104;
    // Operations implemented by personalization services.
    public static final int OP_EXECUTE = 1;
    public static final int OP_DOWNLOAD = 2;
    public static final int OP_RENDER = 3;
    public static final int OP_WEB_VIEW_EVENT = 4;
    public static final int OP_TRAINING_EXAMPLE = 5;

    // Keys for Bundle objects passed between processes.
    public static final String EXTRA_CALLEE_METADATA =
            "android.ondevicepersonalization.extra.callee_metadata";
    public static final String EXTRA_DATA_ACCESS_SERVICE_BINDER =
            "android.ondevicepersonalization.extra.data_access_service_binder";
    public static final String EXTRA_FEDERATED_COMPUTE_SERVICE_BINDER =
            "android.ondevicepersonalization.extra.federated_computation_service_binder";
    public static final String EXTRA_DESTINATION_URL =
            "android.ondevicepersonalization.extra.destination_url";
    public static final String EXTRA_EVENT_PARAMS =
            "android.ondevicepersonalization.extra.event_params";
    public static final String EXTRA_INPUT = "android.ondevicepersonalization.extra.input";
    public static final String EXTRA_LOOKUP_KEYS =
            "android.ondevicepersonalization.extra.lookup_keys";
    public static final String EXTRA_MIME_TYPE = "android.ondevicepersonalization.extra.mime_type";
    public static final String EXTRA_RESPONSE_DATA =
            "android.ondevicepersonalization.extra.response_data";
    public static final String EXTRA_USER_DATA = "android.ondevicepersonalization.extra.user_data";
    public static final String EXTRA_VALUE = "android.ondevicepersonalization.extra.value";
    public static final String EXTRA_RESULT = "android.ondevicepersonalization.extra.result";
    public static final String KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS =
            "enable_ondevicepersonalization_apis";

    // Data Access Service operations.
    public static final int DATA_ACCESS_OP_REMOTE_DATA_LOOKUP = 1;
    public static final int DATA_ACCESS_OP_REMOTE_DATA_KEYSET = 2;
    public static final int DATA_ACCESS_OP_GET_EVENT_URL = 3;
    public static final int DATA_ACCESS_OP_LOCAL_DATA_LOOKUP = 4;
    public static final int DATA_ACCESS_OP_LOCAL_DATA_KEYSET = 5;
    public static final int DATA_ACCESS_OP_LOCAL_DATA_PUT = 6;
    public static final int DATA_ACCESS_OP_LOCAL_DATA_REMOVE = 7;
    public static final int DATA_ACCESS_OP_GET_REQUESTS = 8;
    public static final int DATA_ACCESS_OP_GET_JOINED_EVENTS = 9;

    private Constants() {}
}
