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

import android.annotation.NonNull;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.util.Objects;

/** Fuctions for testing and debugging. */
public class DebugUtils {
    /** Returns true if the device is debuggable. */
    public static boolean isDeveloperModeEnabled(@NonNull Context context) {
        ContentResolver resolver = Objects.requireNonNull(context.getContentResolver());
        return Build.isDebuggable()
                || Settings.Global.getInt(
                    resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
    }

    private DebugUtils() {}
}
