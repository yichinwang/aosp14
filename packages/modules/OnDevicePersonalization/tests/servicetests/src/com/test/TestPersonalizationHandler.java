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

package com.test;

import android.adservices.ondevicepersonalization.DownloadCompletedInput;
import android.adservices.ondevicepersonalization.DownloadCompletedOutput;
import android.adservices.ondevicepersonalization.EventInput;
import android.adservices.ondevicepersonalization.EventLogRecord;
import android.adservices.ondevicepersonalization.EventOutput;
import android.adservices.ondevicepersonalization.ExecuteInput;
import android.adservices.ondevicepersonalization.ExecuteOutput;
import android.adservices.ondevicepersonalization.IsolatedWorker;
import android.adservices.ondevicepersonalization.KeyValueStore;
import android.adservices.ondevicepersonalization.RenderInput;
import android.adservices.ondevicepersonalization.RenderOutput;
import android.adservices.ondevicepersonalization.RenderingConfig;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.adservices.ondevicepersonalization.TrainingExamplesInput;
import android.adservices.ondevicepersonalization.TrainingExamplesOutput;
import android.annotation.NonNull;
import android.content.ContentValues;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

// TODO(b/249345663) Move this class and related manifest to separate APK for more realistic testing
public class TestPersonalizationHandler implements IsolatedWorker {
    public final String TAG = "TestPersonalizationHandler";
    private final KeyValueStore mRemoteData;

    TestPersonalizationHandler(KeyValueStore remoteData) {
        mRemoteData = remoteData;
    }

    @Override
    public void onDownloadCompleted(
            DownloadCompletedInput input, Consumer<DownloadCompletedOutput> consumer) {
        try {
            Log.d(TAG, "Starting filterData.");
            Log.d(TAG, "Data: " + input.getData());

            Log.d(TAG, "Existing keyExtra: " + Arrays.toString(mRemoteData.get("keyExtra")));
            Log.d(TAG, "Existing keySet: " + mRemoteData.keySet());

            List<String> keysToRetain = getFilteredKeys(input.getData());
            keysToRetain.add("keyExtra");
            // Get the keys to keep from the downloaded data
            DownloadCompletedOutput result =
                    new DownloadCompletedOutput.Builder().setRetainedKeys(keysToRetain).build();
            consumer.accept(result);
        } catch (Exception e) {
            Log.e(TAG, "Error occurred in onDownload", e);
        }
    }

    @Override
    public void onExecute(@NonNull ExecuteInput input, @NonNull Consumer<ExecuteOutput> consumer) {
        Log.d(TAG, "onExecute() started.");
        ContentValues logData = new ContentValues();
        logData.put("id", "bid1");
        logData.put("pr", 5.0);
        ExecuteOutput result =
                new ExecuteOutput.Builder()
                        .setRequestLogRecord(new RequestLogRecord.Builder().addRow(logData).build())
                        .addRenderingConfig(new RenderingConfig.Builder().addKey("bid1").build())
                        .addEventLogRecord(
                                new EventLogRecord.Builder()
                                        .setData(logData)
                                        .setRequestLogRecord(
                                                new RequestLogRecord.Builder()
                                                        .addRow(logData)
                                                        .addRow(logData)
                                                        .setRequestId(1)
                                                        .build())
                                        .setType(1)
                                        .setRowIndex(1)
                                        .build())
                        .build();
        consumer.accept(result);
    }

    @Override
    public void onRender(@NonNull RenderInput input, @NonNull Consumer<RenderOutput> consumer) {
        Log.d(TAG, "onRender() started.");
        RenderOutput result =
                new RenderOutput.Builder()
                        .setContent(
                                "<p>RenderResult: "
                                        + String.join(",", input.getRenderingConfig().getKeys())
                                        + "<p>")
                        .build();
        consumer.accept(result);
    }

    @Override
    public void onEvent(@NonNull EventInput input, @NonNull Consumer<EventOutput> consumer) {
        Log.d(TAG, "onEvent() started.");
        long longValue = 0;
        if (input.getParameters() != null) {
            longValue = input.getParameters().getLong("x");
        }
        ContentValues logData = new ContentValues();
        logData.put("x", longValue);
        EventOutput result =
                new EventOutput.Builder()
                        .setEventLogRecord(
                                new EventLogRecord.Builder()
                                        .setType(1)
                                        .setRowIndex(0)
                                        .setData(logData)
                                        .build())
                        .build();
        Log.d(TAG, "onEvent() result: " + result.toString());
        consumer.accept(result);
    }

    private List<String> getFilteredKeys(Map<String, byte[]> data) {
        Set<String> filteredKeys = data.keySet();
        filteredKeys.remove("key3");
        return new ArrayList<>(filteredKeys);
    }

    @Override
    public void onTrainingExamples(
            @NonNull TrainingExamplesInput input,
            @NonNull Consumer<TrainingExamplesOutput> consumer) {
        Log.d(TAG, "onTrainingExamples() started.");
        Log.d(TAG, "Population name: " + input.getPopulationName());
        Log.d(TAG, "Task name: " + input.getTaskName());

        List<byte[]> examples = new ArrayList<>();
        List<byte[]> tokens = new ArrayList<>();
        examples.add(new byte[] {10});
        examples.add(new byte[] {20});
        tokens.add("token1".getBytes());
        tokens.add("token2".getBytes());

        TrainingExamplesOutput output =
                new TrainingExamplesOutput.Builder()
                        .setTrainingExamples(examples)
                        .setResumptionTokens(tokens)
                        .build();
        consumer.accept(output);
    }
}
