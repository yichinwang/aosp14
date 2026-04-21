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

package com.android.adservices.service.measurement.inputverification;

import android.annotation.NonNull;
import android.content.Context;
import android.hardware.input.InputManager;
import android.view.InputEvent;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.MeasurementClickVerificationStats;
import com.android.internal.annotations.VisibleForTesting;

/** Class for handling navigation event verification. */
public class ClickVerifier {
    @NonNull private final InputManager mInputManager;
    @NonNull private final Flags mFlags;

    @NonNull private final AdServicesLogger mAdServicesLogger;

    public ClickVerifier(Context context) {
        mInputManager = context.getSystemService(InputManager.class);
        mFlags = FlagsFactory.getFlags();
        mAdServicesLogger = AdServicesLoggerImpl.getInstance();
    }

    @VisibleForTesting
    ClickVerifier(
            @NonNull InputManager inputManager,
            @NonNull Flags flags,
            @NonNull AdServicesLogger adServicesLogger) {
        mInputManager = inputManager;
        mFlags = flags;
        mAdServicesLogger = adServicesLogger;
    }

    /**
     * Checks if the {@link InputEvent} passed with a click registration can be verified. In order
     * for an InputEvent to be verified:
     *
     * <p>1. The event time of the InputEvent has to be within {@link
     * com.android.adservices.service.PhFlags#MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS }
     * of the API call.
     *
     * <p>2. The InputEvent has to be verified by the system {@link InputManager}.
     *
     * @param event The InputEvent passed with the registration call.
     * @param registerTimestamp The time of the registration call.
     * @return Whether the InputEvent can be verified.
     */
    public boolean isInputEventVerifiable(
            @NonNull InputEvent event, long registerTimestamp, String sourceRegistrant) {
        boolean isInputEventVerified = true;
        MeasurementClickVerificationStats.Builder clickVerificationStatsBuilder =
                MeasurementClickVerificationStats.builder();
        clickVerificationStatsBuilder.setInputEventPresent(true);

        if (!isInputEventVerifiableBySystem(event, clickVerificationStatsBuilder)) {
            isInputEventVerified = false;
        }

        if (!isInputEventWithinValidTimeRange(
                registerTimestamp, event, clickVerificationStatsBuilder)) {
            isInputEventVerified = false;
        }

        clickVerificationStatsBuilder.setSourceType(
                (isInputEventVerified ? Source.SourceType.NAVIGATION : Source.SourceType.EVENT)
                        .getIntValue());
        clickVerificationStatsBuilder.setSourceRegistrant(sourceRegistrant);

        logClickVerificationStats(clickVerificationStatsBuilder, mAdServicesLogger);

        return isInputEventVerified;
    }

    /** Checks whether the InputEvent can be verified by the system. */
    @VisibleForTesting
    boolean isInputEventVerifiableBySystem(
            InputEvent event, MeasurementClickVerificationStats.Builder stats) {
        boolean isVerifiedBySystem = mInputManager.verifyInputEvent(event) != null;

        stats.setSystemClickVerificationEnabled(mFlags.getMeasurementIsClickVerifiedByInputEvent());
        stats.setSystemClickVerificationSuccessful(isVerifiedBySystem);
        return !mFlags.getMeasurementIsClickVerifiedByInputEvent() || isVerifiedBySystem;
    }

    /**
     * Checks whether the timestamp on the InputEvent and the time of the API call are within the
     * accepted range defined at {@link
     * com.android.adservices.service.PhFlags#MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS}
     */
    @VisibleForTesting
    boolean isInputEventWithinValidTimeRange(
            long registerTimestamp,
            InputEvent event,
            MeasurementClickVerificationStats.Builder stats) {
        long inputEventDelay = registerTimestamp - event.getEventTime();
        stats.setInputEventDelayMillis(inputEventDelay);
        stats.setValidDelayWindowMillis(mFlags.getMeasurementRegistrationInputEventValidWindowMs());
        return inputEventDelay <= mFlags.getMeasurementRegistrationInputEventValidWindowMs();
    }

    private void logClickVerificationStats(
            MeasurementClickVerificationStats.Builder stats, AdServicesLogger adServicesLogger) {
        adServicesLogger.logMeasurementClickVerificationStats(stats.build());
    }
}
