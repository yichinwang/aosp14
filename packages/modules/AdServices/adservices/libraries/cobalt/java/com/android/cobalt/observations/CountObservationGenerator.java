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

import static com.android.cobalt.collect.ImmutableHelpers.toImmutableList;

import android.annotation.NonNull;

import com.android.cobalt.data.EventRecordAndSystemProfile;
import com.android.cobalt.data.ObservationGenerator;
import com.android.cobalt.system.SystemData;

import com.google.cobalt.IntegerObservation;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.Observation;
import com.google.cobalt.ObservationMetadata;
import com.google.cobalt.ObservationToEncrypt;
import com.google.cobalt.PrivateIndexObservation;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.PrivacyLevel;
import com.google.cobalt.ReportParticipationObservation;
import com.google.cobalt.SystemProfile;
import com.google.cobalt.UnencryptedObservationBatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.protobuf.ByteString;

import java.security.SecureRandom;
import java.util.Objects;

/** Observation generator for FLEETWIDE_OCCURRENCE_COUNTS. */
public final class CountObservationGenerator implements ObservationGenerator {
    private final SystemData mSystemData;
    private final PrivacyGenerator mPrivacyGenerator;
    private final SecureRandom mSecureRandom;
    private final int mCustomerId;
    private final int mProjectId;
    private final MetricDefinition mMetric;
    private final ReportDefinition mReport;

    public CountObservationGenerator(
            @NonNull SystemData systemData,
            @NonNull PrivacyGenerator privacyGenerator,
            @NonNull SecureRandom secureRandom,
            int customerId,
            int projectId,
            @NonNull MetricDefinition metric,
            @NonNull ReportDefinition report) {

        this.mSystemData = Objects.requireNonNull(systemData);
        this.mPrivacyGenerator = Objects.requireNonNull(privacyGenerator);
        this.mSecureRandom = Objects.requireNonNull(secureRandom);
        this.mCustomerId = customerId;
        this.mProjectId = projectId;
        this.mMetric = Objects.requireNonNull(metric);
        this.mReport = Objects.requireNonNull(report);
    }

    /**
     * Generate the observations that occurred for a single day and report.
     *
     * @param dayIndex the day to generate observations for
     * @param allEventData per-system profile event data being aggregated
     * @return observations to be stored in the database for later sending
     */
    @Override
    public ImmutableList<UnencryptedObservationBatch> generateObservations(
            int dayIndex,
            ImmutableListMultimap<SystemProfile, EventRecordAndSystemProfile> allEventData) {
        if (allEventData.isEmpty() && mReport.getPrivacyLevel() != PrivacyLevel.NO_ADDED_PRIVACY) {
            // Reports with privacy enabled need to send fabricated observations and a report
            // participation observation even if no real observations are present.
            return ImmutableList.of(
                    generateObservations(
                            dayIndex,
                            // Use the current system profile since none is provided.
                            mSystemData.filteredSystemProfile(mReport),
                            ImmutableList.of()));
        }

        return allEventData.keySet().stream()
                .map(
                        systemProfile ->
                                generateObservations(
                                        dayIndex, systemProfile, allEventData.get(systemProfile)))
                .collect(toImmutableList());
    }

    /**
     * Generate an observation batch from events for a given day and system profile.
     *
     * @param dayIndex the day observations are being generated for
     * @param systemProfile the system profile of the observations
     * @param eventData the events
     * @return an UnencryptedObservation batch holding the generated observations
     */
    private UnencryptedObservationBatch generateObservations(
            int dayIndex,
            SystemProfile systemProfile,
            ImmutableList<EventRecordAndSystemProfile> eventData) {
        if (mReport.getEventVectorBufferMax() != 0
                && eventData.size() > mReport.getEventVectorBufferMax()) {
            // Each EventRecordAndSystemProfile contains a unique event vector for the system
            // profile and day so the number of events can be compared to the event vector buffer
            // max of the report.
            eventData = eventData.subList(0, (int) mReport.getEventVectorBufferMax());
        }

        ImmutableList<ObservationToEncrypt> observations =
                mReport.getPrivacyLevel() != PrivacyLevel.NO_ADDED_PRIVACY
                        ? buildPrivateObservations(eventData)
                        : buildNonPrivateObservations(eventData);

        return UnencryptedObservationBatch.newBuilder()
                .setMetadata(
                        ObservationMetadata.newBuilder()
                                .setCustomerId(mCustomerId)
                                .setProjectId(mProjectId)
                                .setMetricId(mMetric.getId())
                                .setReportId(mReport.getId())
                                .setDayIndex(dayIndex)
                                .setSystemProfile(systemProfile))
                .addAllUnencryptedObservations(observations)
                .build();
    }

    /** Securely generate 8 random bytes. */
    private ByteString generateSecureRandomByteString() {
        byte[] randomId = new byte[8];
        mSecureRandom.nextBytes(randomId);
        return ByteString.copyFrom(randomId);
    }

    /**
     * Build a list of non-private observations from event data.
     *
     * @param eventData the event data to encode
     * @return ObservationToEncrypts containing non-private observations
     */
    private ImmutableList<ObservationToEncrypt> buildNonPrivateObservations(
            ImmutableList<EventRecordAndSystemProfile> eventData) {
        IntegerObservation.Builder integerBuilder = IntegerObservation.newBuilder();
        eventData.stream().map(this::buildIntegerValue).forEach(integerBuilder::addValues);
        Observation observation =
                Observation.newBuilder()
                        .setInteger(integerBuilder)
                        .setRandomId(generateSecureRandomByteString())
                        .build();
        return ImmutableList.of(
                // Reports without privacy only make a single contribution so the id is set.
                buildObservationToEncrypt(observation, /* setContributionId= */ true));
    }

    /**
     * Build an intger observation value from an event vector and aggregate value in an event.
     *
     * @param countEvent the event
     * @return an IntegerObservation.Value
     */
    private IntegerObservation.Value buildIntegerValue(EventRecordAndSystemProfile event) {
        return IntegerObservation.Value.newBuilder()
                .addAllEventCodes(event.eventVector().eventCodes())
                .setValue(event.aggregateValue().getIntegerValue())
                .build();
    }

    /**
     * Build a list of private observations to encrypt from a set of event indices.
     *
     * @param eventData the events which occurred
     * @return a list of observations to encrypt, including fabricated observations
     */
    private ImmutableList<ObservationToEncrypt> buildPrivateObservations(
            ImmutableList<EventRecordAndSystemProfile> eventData) {
        ImmutableList<Integer> eventIndices =
                eventData.stream()
                        .map(countEvent -> asIndex(countEvent))
                        .collect(toImmutableList());
        ImmutableList<Integer> allIndices =
                mPrivacyGenerator.addNoise(eventIndices, maxIndexForReport(), mReport);
        ImmutableList<Observation> observations =
                ImmutableList.<Observation>builder()
                        .addAll(allIndices.stream().map(i -> buildPrivateObservation(i)).iterator())
                        .add(buildParticipationObservation())
                        .build();
        ImmutableList.Builder<ObservationToEncrypt> toEncrypt = ImmutableList.builder();
        boolean setContributionId = true;
        for (int i = 0; i < observations.size(); ++i) {
            // Reports with privacy enabled split a single contribution across multiple
            // observations, both private and participation. However, only 1 needs the
            // contribution id set.
            toEncrypt.add(buildObservationToEncrypt(observations.get(i), setContributionId));
            setContributionId = false;
        }

        return toEncrypt.build();
    }

    /**
     * Turn an index into an observation.
     *
     * @param index the private index
     * @return an Observation that contains a private observation
     */
    private Observation buildPrivateObservation(int index) {
        return Observation.newBuilder()
                .setPrivateIndex(PrivateIndexObservation.newBuilder().setIndex(index).build())
                .setRandomId(generateSecureRandomByteString())
                .build();
    }

    /**
     * Create a report participation observation.
     *
     * @return an Observation that contains a report participation observation
     */
    private Observation buildParticipationObservation() {
        return Observation.newBuilder()
                .setReportParticipation(ReportParticipationObservation.getDefaultInstance())
                .setRandomId(generateSecureRandomByteString())
                .build();
    }

    /**
     * Create an observation to encrypt and optionally set the contribution id
     *
     * @param observation the observation
     * @param setContributionId whether the contribution id should be set
     * @return an ObservationToEncrypt
     */
    private ObservationToEncrypt buildObservationToEncrypt(
            Observation observation, boolean setContributionId) {
        ObservationToEncrypt.Builder builder = ObservationToEncrypt.newBuilder();
        builder.setObservation(observation);
        if (setContributionId) {
            builder.setContributionId(generateSecureRandomByteString());
        }

        return builder.build();
    }

    /**
     * Convert an event into a private index.
     *
     * @param event the event to convert
     * @return the index of the event
     */
    private int asIndex(EventRecordAndSystemProfile event) {
        int maxEventVectorIndex = maxEventVectorIndexForMetric();
        int eventVectorIndex =
                PrivateIndexCalculations.eventVectorToIndex(event.eventVector(), mMetric);

        long clippedValue =
                PrivateIndexCalculations.clipValue(
                        event.aggregateValue().getIntegerValue(), mReport);
        int clippedValueIndex =
                PrivateIndexCalculations.longToIndex(
                        clippedValue,
                        mReport.getMinValue(),
                        mReport.getMaxValue(),
                        mReport.getNumIndexPoints(),
                        mSecureRandom);
        return PrivateIndexCalculations.valueAndEventVectorIndicesToIndex(
                clippedValueIndex, eventVectorIndex, maxEventVectorIndex);
    }

    private int maxIndexForReport() {
        return PrivateIndexCalculations.getNumEventVectors(mMetric.getMetricDimensionsList())
                        * mReport.getNumIndexPoints()
                - 1;
    }

    private int maxEventVectorIndexForMetric() {
        return PrivateIndexCalculations.getNumEventVectors(mMetric.getMetricDimensionsList()) - 1;
    }
}
