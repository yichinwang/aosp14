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
package com.android.adservices.common;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

// TODO(b/295269584): remove when callers use @RequiresSdkRange
/**
 * Used by {@link AbstractSdkLevelSupportedRule SdkLevelSupportedRule} to skip a test if the Android
 * SDK level of the device is {@code T} or higher.
 *
 * @deprecated will be eventually replaced by {@code RequiresSdkRange}.
 */
@Retention(RUNTIME)
@Target({METHOD, TYPE})
@Deprecated
public @interface RequiresSdkLevelLessThanT {
    /** Reason why the test should be skipped. */
    String reason() default "";
}
