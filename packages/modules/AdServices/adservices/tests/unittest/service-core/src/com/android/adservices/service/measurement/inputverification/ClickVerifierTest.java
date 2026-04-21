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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.input.InputManager;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.VerifiedMotionEvent;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementClickVerificationStats;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class ClickVerifierTest {
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    @Mock private InputManager mInputManager;
    @Mock private Flags mFlags;

    @Mock private AdServicesLogger mAdServicesLogger;

    @Mock private VerifiedMotionEvent mVerifiedMotionEvent;
    private ClickVerifier mClickVerifier;

    private static final String SOURCE_REGISTRANT = "source_registrant";

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        mClickVerifier = new ClickVerifier(mInputManager, mFlags, mAdServicesLogger);
    }

    @Test
    public void testInputEventOutsideTimeRangeReturnsFalse() {
        InputEvent eventOutsideRange = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        when(mFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mInputManager.verifyInputEvent(eventOutsideRange)).thenReturn(mVerifiedMotionEvent);
        long registerTimestamp =
                FlagsFactory.getFlagsForTest().getMeasurementRegistrationInputEventValidWindowMs()
                        + 1;
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                eventOutsideRange, registerTimestamp, SOURCE_REGISTRANT))
                .isFalse();
    }

    @Test
    public void testVerifiedInputEventInsideTimeRangeReturnsTrue() {
        InputEvent eventInsideRange = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        when(mFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mInputManager.verifyInputEvent(eventInsideRange)).thenReturn(mVerifiedMotionEvent);
        long registerTimestamp =
                FlagsFactory.getFlagsForTest().getMeasurementRegistrationInputEventValidWindowMs();
        when(mFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(registerTimestamp);
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                eventInsideRange, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();
    }

    @Test
    public void testInputEventNotValidatedBySystem() {
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        long registerTimestamp =
                FlagsFactory.getFlagsForTest().getMeasurementRegistrationInputEventValidWindowMs();
        when(mFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(registerTimestamp);
        when(mFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(null);
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isFalse();
    }

    @Test
    public void testInputEvent_verifyByInputEventFlagDisabled_Verified() {
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        long registerTimestamp =
                FlagsFactory.getFlagsForTest().getMeasurementRegistrationInputEventValidWindowMs();
        when(mFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(registerTimestamp);
        when(mFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(false);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(null);
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();
    }

    @Test
    public void testLog_inputEventVerified() {
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(mVerifiedMotionEvent);
        when(mFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mFlags.getMeasurementRegistrationInputEventValidWindowMs()).thenReturn(6000L);
        long registerTimestamp = mFlags.getMeasurementRegistrationInputEventValidWindowMs() / 2;
        mClickVerifier.isInputEventVerifiable(inputEvent, registerTimestamp, SOURCE_REGISTRANT);

        MeasurementClickVerificationStats stats =
                MeasurementClickVerificationStats.builder()
                        .setSourceType(Source.SourceType.NAVIGATION.getIntValue())
                        .setInputEventPresent(true)
                        .setSystemClickVerificationEnabled(
                                mFlags.getMeasurementIsClickVerifiedByInputEvent())
                        .setSystemClickVerificationSuccessful(true)
                        .setInputEventDelayMillis(registerTimestamp)
                        .setValidDelayWindowMillis(
                                mFlags.getMeasurementRegistrationInputEventValidWindowMs())
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .build();

        verify(mAdServicesLogger).logMeasurementClickVerificationStats(eq(stats));
    }

    @Test
    public void testLogClickVerification_inputEventNotVerifiedBySystem() {
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(null);
        when(mFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mFlags.getMeasurementRegistrationInputEventValidWindowMs()).thenReturn(6000L);
        long registerTimestamp = mFlags.getMeasurementRegistrationInputEventValidWindowMs() / 2;
        mClickVerifier.isInputEventVerifiable(inputEvent, registerTimestamp, SOURCE_REGISTRANT);

        MeasurementClickVerificationStats stats =
                MeasurementClickVerificationStats.builder()
                        .setSourceType(Source.SourceType.EVENT.getIntValue())
                        .setInputEventPresent(true)
                        .setSystemClickVerificationEnabled(
                                mFlags.getMeasurementIsClickVerifiedByInputEvent())
                        .setSystemClickVerificationSuccessful(false)
                        .setInputEventDelayMillis(registerTimestamp)
                        .setValidDelayWindowMillis(
                                mFlags.getMeasurementRegistrationInputEventValidWindowMs())
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .build();

        verify(mAdServicesLogger).logMeasurementClickVerificationStats(eq(stats));
    }

    @Test
    public void testLogClickVerification_inputEventOutsideValidWindow() {
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(mVerifiedMotionEvent);
        when(mFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mFlags.getMeasurementRegistrationInputEventValidWindowMs()).thenReturn(6000L);
        long registerTimestamp = mFlags.getMeasurementRegistrationInputEventValidWindowMs() * 2;
        mClickVerifier.isInputEventVerifiable(inputEvent, registerTimestamp, SOURCE_REGISTRANT);

        MeasurementClickVerificationStats stats =
                MeasurementClickVerificationStats.builder()
                        .setSourceType(Source.SourceType.EVENT.getIntValue())
                        .setInputEventPresent(true)
                        .setSystemClickVerificationEnabled(
                                mFlags.getMeasurementIsClickVerifiedByInputEvent())
                        .setSystemClickVerificationSuccessful(true)
                        .setInputEventDelayMillis(registerTimestamp)
                        .setValidDelayWindowMillis(
                                mFlags.getMeasurementRegistrationInputEventValidWindowMs())
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .build();

        verify(mAdServicesLogger).logMeasurementClickVerificationStats(eq(stats));
    }
}
