/*
 * Copyright 2023 The Android Open Source Project
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

package android.app.appsearch.flags;


/**
 * Flags to control different features.
 *
 * <p>In Jetpack, those values can't be changed during runtime.
 *
 * @hide
 */
public final class Flags {
    private Flags() {}

    // The prefix of all the flags defined for AppSearch. The prefix has
    // "com.android.appsearch.flags", aka the package name for generated AppSearch flag classes in
    // the framework, plus an additional trailing '.'.
    private static final String FLAG_PREFIX = "com.android.appsearch.flags.";

    // The full string values for flags defined in the framework.
    //
    // The values of the static variables are the names of the flag defined in the framework's
    // aconfig files. E.g. "enable_safe_parcelable", with FLAG_PREFIX as the prefix.
    //
    // The name of the each static variable should be "FLAG_" + capitalized value of the flag.

    /**
     * Enable SafeParcelable related features.
     *
     * @hide
     */
    public static final String FLAG_ENABLE_SAFE_PARCELABLE = FLAG_PREFIX + "enable_safe_parcelable";
}
