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

import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Switch;

import static com.android.tv.feedbackconsent.TvFeedbackConstants.RESULT_CODE_OK;
import static com.android.tv.feedbackconsent.TvFeedbackConstants.BUGREPORT_REQUESTED;
import static com.android.tv.feedbackconsent.TvFeedbackConstants.BUGREPORT_CONSENT;
import static com.android.tv.feedbackconsent.TvFeedbackConstants.SYSTEM_LOG_CONSENT;
import static com.android.tv.feedbackconsent.TvFeedbackConstants.SYSTEM_LOG_REQUESTED;
import static com.android.tv.feedbackconsent.TvFeedbackConstants.CONSENT_RECEIVER;

public class TvFeedbackConsentActivity extends Activity {

    private static final String TAG = TvFeedbackConsentActivity.class.getSimpleName();
    private static ResultReceiver resultReceiver = null;
    private boolean systemLogRequested = false;
    private boolean bugreportRequested = true;
    private boolean resultSent = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        systemLogRequested = intent.getBooleanExtra(SYSTEM_LOG_REQUESTED, false);
        bugreportRequested = intent.getBooleanExtra(BUGREPORT_REQUESTED, false);

        if (!systemLogRequested && !bugreportRequested) {
            Log.e(TAG, "Consent screen requested without requesting any data.");
            this.onStop();
        }
        View view = getLayoutInflater().inflate(R.layout.tv_feedback_consent, null);
        setContentView(view);
        onViewCreated();
    }

    @Override
    public void onResume() {
        super.onResume();
        Intent intent = getIntent();
        resultReceiver = intent.getParcelableExtra(CONSENT_RECEIVER, ResultReceiver.class);
    }

    private void onViewCreated() {
        View nextButton = requireViewById(R.id.next_button);
        nextButton.setOnClickListener(this::onNextButtonClicked);
        nextButton.requestFocus();

        if (systemLogRequested) {
            View systemLogsRow = requireViewById(R.id.system_logs_row);
            systemLogsRow.setVisibility(View.VISIBLE);
            View systemLogsSwitch = requireViewById(R.id.system_logs_switch);
            systemLogsSwitch.setOnFocusChangeListener(
                    (v, focused) -> systemLogsRow.setSelected(focused));
        }

        if (bugreportRequested) {
            View bugreportRow = requireViewById(R.id.bugreport_row);
            bugreportRow.setVisibility(View.VISIBLE);
            View bugreportSwitch = requireViewById(R.id.bugreport_switch);
            bugreportSwitch.setOnFocusChangeListener(
                    (v, focused) -> bugreportRow.setSelected(focused));
        }
    }

    private void onNextButtonClicked(View view) {
        boolean sendLogs = ((Switch) requireViewById(R.id.system_logs_switch)).isChecked();
        boolean sendBugreport = ((Switch) requireViewById(R.id.bugreport_switch)).isChecked();
        sendResult(sendLogs, sendBugreport);
        finish();
    }

    private void sendResult(boolean sendLogs, boolean sendBugreport) {
        if (resultReceiver == null) {
            Log.w(TAG, "Activity intent does not contain a result receiver");
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putSerializable(SYSTEM_LOG_CONSENT, sendLogs);
        bundle.putSerializable(BUGREPORT_CONSENT, sendBugreport);
        try {
            resultReceiver.send(RESULT_CODE_OK, bundle);
            resultSent = true;
        } catch (Exception e) {
            Log.e(TAG, "Exception in sending result: ", e);
        }
    }

    @Override
    public void onStop() {
        // Activity dismissed without clicking on the next button.
        if (!resultSent) {
            sendResult(/* sendLogs = */ false, /* sendBugreport = */ false);
        }
        super.onStop();
    }
}

