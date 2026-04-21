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

import android.app.Service;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import android.annotation.NonNull;
import android.annotation.Nullable;
import androidx.core.util.Preconditions;

import static com.android.tv.feedbackconsent.TvFeedbackConstants.CONSENT_RECEIVER;
import static com.android.tv.feedbackconsent.TvFeedbackConstants.BUGREPORT_REQUESTED;
import static com.android.tv.feedbackconsent.TvFeedbackConstants.SYSTEM_LOG_REQUESTED;

public final class TvFeedbackConsentService extends Service {

    private static final String TAG = TvFeedbackConsentService.class.getSimpleName();

    final TvDiagnosticInformationManagerBinder tvDiagnosticInformationBinder =
            new TvDiagnosticInformationManagerBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return tvDiagnosticInformationBinder;
    }

    private final class TvDiagnosticInformationManagerBinder extends
            ITvDiagnosticInformationManager.Stub {
        boolean mBugreportRequested = false;
        boolean mSystemLogsRequested = false;

        @Override
        public void getDiagnosticInformation(
                @Nullable ParcelFileDescriptor bugreportFd,
                @Nullable ParcelFileDescriptor systemLogsFd,
                @NonNull ITvDiagnosticInformationManagerCallback tvFeedbackConsentCallback) {
            Preconditions.checkNotNull(tvFeedbackConsentCallback);
            if (bugreportFd != null) {
                mBugreportRequested = true;
            }
            if (systemLogsFd != null) {
                mSystemLogsRequested = true;
            }
            Preconditions.checkArgument(mBugreportRequested || mSystemLogsRequested,
                    "No Diagnostic information requested: " +
                            "Both bugreportFd and systemLogsFd cannot be null");

            ResultReceiver mResultReceiver = createResultReceiver(tvFeedbackConsentCallback);
            displayConsentScreen(mResultReceiver);
        }

        private ResultReceiver createResultReceiver(
                ITvDiagnosticInformationManagerCallback mTvFeedbackConsentCallback) {
            return new ResultReceiver(
                    new Handler(Looper.getMainLooper())) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    if (mTvFeedbackConsentCallback == null) {
                        Log.w(TAG, "Diagnostic information requested without a callback");
                        return;
                    }

                    try {
                        if (mBugreportRequested) {
                            mTvFeedbackConsentCallback.onBugreportError("NOT_IMPLEMENTED");
                        }
                        if (mSystemLogsRequested) {
                            mTvFeedbackConsentCallback.onSystemLogsError("NOT_IMPLEMENTED");
                        }
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        }

        private void displayConsentScreen(ResultReceiver mResultReceiver) {
            Intent consentIntent = new Intent(
                    TvFeedbackConsentService.this, TvFeedbackConsentActivity.class);
            consentIntent.putExtra(CONSENT_RECEIVER, mResultReceiver);
            consentIntent.putExtra(BUGREPORT_REQUESTED, mBugreportRequested);
            consentIntent.putExtra(SYSTEM_LOG_REQUESTED, mSystemLogsRequested);
            consentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                TvFeedbackConsentService.this.startActivity(consentIntent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Error starting activity", e);
            }
        }
    }
}
