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

package com.android.federatedcompute.services.training.util;

import android.federatedcompute.common.ExampleConsumption;

import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.intelligence.fcp.client.FLRunnerResult.ContributionResult;

import java.util.ArrayList;

/** The result of federated computation. */
public class ComputationResult {
    private String mOutputCheckpointFile = "";
    private FLRunnerResult mFlRunnerResult = null;
    private ArrayList<ExampleConsumption> mExampleConsumptionList = null;

    public ComputationResult(
            String outputCheckpointFile,
            FLRunnerResult flRunnerResult,
            ArrayList<ExampleConsumption> exampleConsumptionList) {
        this.mOutputCheckpointFile = outputCheckpointFile;
        this.mFlRunnerResult = flRunnerResult;
        this.mExampleConsumptionList = exampleConsumptionList;
    }

    public ArrayList<ExampleConsumption> getExampleConsumptionList() {
        return mExampleConsumptionList;
    }

    public String getOutputCheckpointFile() {
        return mOutputCheckpointFile;
    }

    public FLRunnerResult getFlRunnerResult() {
        return mFlRunnerResult;
    }

    public boolean isResultSuccess() {
        return mFlRunnerResult.getContributionResult() == ContributionResult.SUCCESS;
    }
}
