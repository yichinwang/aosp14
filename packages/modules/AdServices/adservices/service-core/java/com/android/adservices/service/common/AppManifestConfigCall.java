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
package com.android.adservices.service.common;

import java.util.Objects;

// TODO(b/310270746): make it package-protected when TopicsServiceImplTest is refactored
/** Represents a call to a public {@link AppManifestConfigHelper} method. */
public final class AppManifestConfigCall {
    public String packageName;
    public boolean appExists;
    public boolean appHasConfig;
    public boolean enabledByDefault;

    public AppManifestConfigCall(String packageName) {
        this.packageName = Objects.requireNonNull(packageName, "packageName cannot be null");
    }

    @Override
    public String toString() {
        return "AppManifestConfigCall[pkg="
                + packageName
                + ", appExists="
                + appExists
                + ", appHasConfig="
                + appHasConfig
                + ", enabledByDefault="
                + enabledByDefault
                + "]";
    }
}
