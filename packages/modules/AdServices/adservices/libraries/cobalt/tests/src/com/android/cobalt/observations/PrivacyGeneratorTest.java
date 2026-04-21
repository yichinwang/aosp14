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

package com.android.cobalt.observations;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.runner.AndroidJUnit4;

import com.android.cobalt.observations.testing.FakeSecureRandom;

import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.PrivacyLevel;
import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class PrivacyGeneratorTest {
    private static final ReportDefinition sReport =
            ReportDefinition.newBuilder()
                    .setId(5)
                    .setPrivacyLevel(PrivacyLevel.LOW_PRIVACY)
                    .setPoissonMean(0.01)
                    .build();

    private final PrivacyGenerator mPrivacyGenerator;

    public PrivacyGeneratorTest() {
        mPrivacyGenerator = new PrivacyGenerator(new FakeSecureRandom());
    }

    @Test
    public void testAddNoise_noEventsNoNoise_empty() throws Exception {
        ImmutableList<Integer> result = mPrivacyGenerator.addNoise(ImmutableList.of(), 0, sReport);
        // The report's lambda is too small to trigger a fabricated observation.
        assertThat(result).isEmpty();
    }

    @Test
    public void testAddNoise_noEventsButFabricatedObservation_oneIndex() throws Exception {
        // Use a larger Poisson mean that is guaranteed to cause a fabricated observation to be
        // created, due to the FakeSecureRandom implementation.
        ImmutableList<Integer> result =
                mPrivacyGenerator.addNoise(
                        ImmutableList.of(), 0, sReport.toBuilder().setPoissonMean(0.1).build());
        // A fabricated observation.
        assertThat(result).containsExactly(0);
    }

    @Test
    public void testAddNoise_noEventsButTwoFabricatedObservations_oneIndex() throws Exception {
        // Use an even larger Poisson mean that is guaranteed to cause two fabricated observations
        // to be created, due to the FakeSecureRandom implementation.
        ImmutableList<Integer> result =
                mPrivacyGenerator.addNoise(
                        ImmutableList.of(), 0, sReport.toBuilder().setPoissonMean(0.52).build());
        // Two fabricated observations.
        assertThat(result).containsExactly(0, 0);
    }

    @Test
    public void testAddNoise_oneEventNoNoise_oneIndex() throws Exception {
        ImmutableList<Integer> result = mPrivacyGenerator.addNoise(ImmutableList.of(0), 0, sReport);
        // Real index returned, as the report's lambda is too small to trigger a fabricated
        // observation.
        assertThat(result).containsExactly(0);
    }

    @Test
    public void testAddNoise_oneEventAndFabricatedObservation_twoIndices() throws Exception {
        // Use a larger Poisson mean that is guaranteed to cause a fabricated observation to be
        // created, due to the FakeSecureRandom implementation.
        ImmutableList<Integer> result =
                mPrivacyGenerator.addNoise(
                        ImmutableList.of(0), 0, sReport.toBuilder().setPoissonMean(0.1).build());
        // Real index returned, and a fabricated index are expected.
        assertThat(result).containsExactly(0, 0);
    }

    @Test
    public void testAddNoise_oneEventAndTwoFabricatedObservations_threeIndices() throws Exception {
        // Use an even larger Poisson mean that is guaranteed to cause two fabricated observations
        // to be created, due to the FakeSecureRandom implementation.
        ImmutableList<Integer> result =
                mPrivacyGenerator.addNoise(
                        ImmutableList.of(0), 0, sReport.toBuilder().setPoissonMean(0.52).build());
        // Real index returned, and two fabricated indices are expected.
        assertThat(result).containsExactly(0, 0, 0);
    }

    @Test
    public void testAddNoise_oneEventForMetricWithDimensions_threeObservations() throws Exception {
        // Use a larger Poisson mean that is guaranteed to cause a single fabricated observation to
        // be created, due to the FakeSecureRandom implementation. This is smaller than other tests,
        // because the poisson mean is multiplied by the number of indices, which is larger here due
        // the metric dimensions.
        ImmutableList<Integer> result =
                mPrivacyGenerator.addNoise(
                        ImmutableList.of(2, 3),
                        5,
                        sReport.toBuilder().setPoissonMean(0.02).build());
        // Real indices returned, and a fabricated index are expected.
        assertThat(result).containsExactly(2, 3, 5);
    }

    @Test
    public void testAddNoise_negativeMaxIndex_throwsException() throws Exception {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mPrivacyGenerator.addNoise(ImmutableList.of(), -1, sReport));
        assertThat(thrown).hasMessageThat().contains("maxIndex value cannot be negative");
    }

    @Test
    public void testAddNoise_negativeLambda_throwsException() throws Exception {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mPrivacyGenerator.addNoise(
                                        ImmutableList.of(),
                                        0,
                                        sReport.toBuilder().setPoissonMean(-0.1).build()));
        assertThat(thrown).hasMessageThat().contains("poisson_mean must be positive");
    }
}
