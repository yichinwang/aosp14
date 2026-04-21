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

import static android.adservices.ondevicepersonalization.Constants.KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Exception thrown by OnDevicePersonalization APIs.
 *
 */
@FlaggedApi(KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS)
public class OnDevicePersonalizationException extends Exception {
    /**
     * The {@link IsolatedService} that was invoked failed to run.
     */
    public static final int ERROR_ISOLATED_SERVICE_FAILED = 1;

    /**
     * Personalization is disabled.
     * @hide
     */
    public static final int ERROR_PERSONALIZATION_DISABLED = 2;

    /** @hide */
    @IntDef(prefix = "ERROR_", value = {
            ERROR_ISOLATED_SERVICE_FAILED,
            ERROR_PERSONALIZATION_DISABLED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ErrorCode {}

    private final @ErrorCode int mErrorCode;

    /** @hide */
    public OnDevicePersonalizationException(@ErrorCode int errorCode) {
        mErrorCode = errorCode;
    }

    /** Returns the error code for this exception. */
    public @ErrorCode int getErrorCode() {
        return mErrorCode;
    }
}
