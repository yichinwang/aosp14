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

package com.android.adservices.service.common.compat;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.atLeastOnce;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertWithMessage;

import android.os.Build;
import android.os.SystemProperties;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Assume;
import org.junit.Test;
import org.mockito.MockitoSession;

public class BuildCompatUtilsTest {

    @Test
    public void testComputeIsDebuggable_SPlus_debuggable() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        // Call Build.isDebuggable() prior to tracking usages so that the class is pre-initialized.
        Build.isDebuggable();

        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .mockStatic(Build.class)
                        .spyStatic(SystemProperties.class)
                        .startMocking();

        try {
            doReturn(true).when(Build::isDebuggable);
            assertWithMessage("BuildCompatUtils.computeIsDebuggable")
                    .that(BuildCompatUtils.computeIsDebuggable())
                    .isTrue();
            verify(Build::isDebuggable, atLeastOnce());
            verify(() -> SystemProperties.getInt(anyString(), anyInt()), never());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testComputeIsDebuggable_SPlus_notDebuggable() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        // Call Build.isDebuggable() prior to tracking usages so that the class is pre-initialized.
        Build.isDebuggable();

        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .mockStatic(Build.class)
                        .spyStatic(SystemProperties.class)
                        .startMocking();

        try {
            doReturn(false).when(Build::isDebuggable);
            assertWithMessage("BuildCompatUtils.computeIsDebuggable")
                    .that(BuildCompatUtils.computeIsDebuggable())
                    .isFalse();
            verify(Build::isDebuggable, atLeastOnce());
            verify(() -> SystemProperties.getInt(anyString(), anyInt()), never());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testComputeIsDebuggable_RMinus_debuggable() {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .mockStatic(SdkLevel.class)
                        .spyStatic(SystemProperties.class)
                        .startMocking();

        try {
            doReturn(false).when(SdkLevel::isAtLeastS);

            doReturn(1).when(() -> SystemProperties.getInt(anyString(), anyInt()));
            assertWithMessage("BuildCompatUtils.computeIsDebuggable")
                    .that(BuildCompatUtils.computeIsDebuggable())
                    .isTrue();
            verify(() -> SystemProperties.getInt(eq("ro.debuggable"), eq(0)), atLeastOnce());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testComputeIsDebuggable_RMinus_notDebuggable() {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .mockStatic(SdkLevel.class)
                        .spyStatic(SystemProperties.class)
                        .startMocking();

        try {
            doReturn(false).when(SdkLevel::isAtLeastS);

            doReturn(0).when(() -> SystemProperties.getInt(anyString(), anyInt()));
            assertWithMessage("BuildCompatUtils.computeIsDebuggable")
                    .that(BuildCompatUtils.computeIsDebuggable())
                    .isFalse();
            verify(() -> SystemProperties.getInt(eq("ro.debuggable"), eq(0)), atLeastOnce());
        } finally {
            session.finishMocking();
        }
    }
}
