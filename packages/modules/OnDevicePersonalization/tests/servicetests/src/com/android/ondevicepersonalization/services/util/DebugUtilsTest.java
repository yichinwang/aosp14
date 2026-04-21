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

package com.android.ondevicepersonalization.services.util;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.quality.Strictness.LENIENT;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoSession;

public class DebugUtilsTest {
    private Context mContext = ApplicationProvider.getApplicationContext();
    private MockitoSession mSession;

    @Before
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(Build.class)
                .mockStatic(Settings.Global.class)
                .strictness(LENIENT)
                .startMocking();
    }

    @After
    public void tearDown() {
        mSession.finishMocking();
    }

    @Test
    public void isDeveloperModeEnabledReturnsTrueIfDeviceDebuggableAndDevOptionsOff() {
        doReturn(true).when(Build::isDebuggable);
        disableDeveloperOptions();
        assertTrue(DebugUtils.isDeveloperModeEnabled(mContext));
    }

    @Test
    public void isDeveloperModeEnabledReturnsTrueIfDeviceDebuggableAndDevOptionsOn() {
        doReturn(true).when(Build::isDebuggable);
        enableDeveloperOptions();
        assertTrue(DebugUtils.isDeveloperModeEnabled(mContext));
    }

    @Test
    public void isDeveloperModeEnabledReturnsFalseIfDeviceNotDebuggableAndDevOptionsOff() {
        doReturn(false).when(Build::isDebuggable);
        disableDeveloperOptions();
        assertFalse(DebugUtils.isDeveloperModeEnabled(mContext));
    }

    @Test
    public void isDeveloperModeEnabledReturnsTrueIfDeviceNotDebuggableAndDevOptionsOn() {
        doReturn(false).when(Build::isDebuggable);
        enableDeveloperOptions();
        assertTrue(DebugUtils.isDeveloperModeEnabled(mContext));
    }

    private void enableDeveloperOptions() {
        when(Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))
                .thenReturn(1);
    }

    private void disableDeveloperOptions() {
        when(Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))
                .thenReturn(0);
    }
}
