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

package com.android.ondevicepersonalization.testing.sampleservice;

import android.adservices.ondevicepersonalization.ExecuteInput;
import android.adservices.ondevicepersonalization.ExecuteOutput;
import android.adservices.ondevicepersonalization.IsolatedService;
import android.adservices.ondevicepersonalization.IsolatedWorker;
import android.adservices.ondevicepersonalization.RenderInput;
import android.adservices.ondevicepersonalization.RenderOutput;
import android.adservices.ondevicepersonalization.RenderingConfig;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.adservices.ondevicepersonalization.RequestToken;
import android.content.ContentValues;

import java.util.function.Consumer;

public class SampleService extends IsolatedService {
    class SampleWorker implements IsolatedWorker {
        @Override public void onExecute(ExecuteInput input, Consumer<ExecuteOutput> consumer) {
            ContentValues logData = new ContentValues();
            logData.put("id", "ad1");
            logData.put("pr", 5.0);
            ExecuteOutput result = new ExecuteOutput.Builder()
                    .setRequestLogRecord(new RequestLogRecord.Builder().addRow(logData).build())
                    .addRenderingConfig(
                        new RenderingConfig.Builder().addKey("bid1").build()
                    )
                    .build();
            consumer.accept(result);
        }

        @Override public void onRender(RenderInput input, Consumer<RenderOutput> consumer) {
            var keys = input.getRenderingConfig().getKeys();
            if (keys.size() > 0) {
                String html = "<body>" + input.getRenderingConfig().getKeys().get(0) + "</body>";
                consumer.accept(new RenderOutput.Builder().setContent(html).build());
            } else {
                consumer.accept(null);
            }
        }
    }

    @Override public IsolatedWorker onRequest(RequestToken requestToken) {
        return new SampleWorker();
    }
}
