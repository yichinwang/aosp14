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

package com.android.tv.feedbackconsent;

/**
 * This interface defines callback functions for the
 * {@link ITvDiagnosticInformationManager#getDiagnosticInformation} request.
 *
 * {@hide}
 */

interface ITvDiagnosticInformationManagerCallback {

    /**
    * Called when system logs are generated successfully,
    * user consents to sharing system logs, and the logs are available in the
    * respective ParcelFileDescriptor provided by the calling application.
    */
    oneway void onSystemLogsFinished();

    /**
    * Called when user denies consent to sharing system logs or system logs
    * generation results in failure.
    *
    * @param error Information regarding the error
    */
    oneway void onSystemLogsError(String error);

    /**
    * Called when user consents to sharing bugreport,
    * bugreport is generated successfully and is available in the
    * respective ParcelFileDescriptor provided by the calling application.
    */
    oneway void onBugreportFinished();

    /**
    * Called when user denies consent to sharing the bugreport or bugreport
    * generation results in failure.
    *
    * @param error Information regarding the error
    */
    oneway void onBugreportError(String error);
}
