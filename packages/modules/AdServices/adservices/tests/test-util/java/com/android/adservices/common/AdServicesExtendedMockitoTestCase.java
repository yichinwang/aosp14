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

import static com.android.adservices.mockito.ExtendedMockitoInlineCleanerRule.Mode.CLEAR_AFTER_TEST_CLASS;

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.mockito.ExtendedMockitoInlineCleanerRule;
import com.android.adservices.mockito.ExtendedMockitoInlineCleanerRule.ClearInlineMocksMode;

import org.junit.ClassRule;
import org.junit.Rule;
import org.mockito.quality.Strictness;

/**
 * Base class for all unit tests that use {@code ExtendedMockito} - for "regular Mockito" use {@link
 * AdServicesMockitoTestCase} instead).
 *
 * <p><b>NOTE:</b> subclasses MUST use
 * {@link com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic} and/or
 * (@link com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic} to set which static
 * classes are mocked ad/or spied.
 */
@ClearInlineMocksMode(CLEAR_AFTER_TEST_CLASS)
public abstract class AdServicesExtendedMockitoTestCase extends AdServicesUnitTestCase {

    // NOTE: must use CLEAR_AFTER_TEST_CLASS by default (defined as a class annotation, so it's used
    // by both ExtendedMockitoInlineCleanerRule and AdServicesExtendedMockitoRule), as some tests
    // performing complicated static class initialization on @Before methods, which often cause test
    // failure when called after the mocks are cleared (for example, DialogFragmentTest would fail
    // after the first method was executed)
    @ClassRule
    public static final ExtendedMockitoInlineCleanerRule sInlineCleaner =
            new ExtendedMockitoInlineCleanerRule();

    @Rule(order = 10)
    public final AdServicesExtendedMockitoRule extendedMockito =
            new AdServicesExtendedMockitoRule.Builder(this).setStrictness(getStrictness()).build();

    /**
     * Allows subclasses to override the default strictness of the {@link #extendedMockito} rule.
     *
     * <p><b>NOTE: </b>ideally the strictness shouldn't be matter and this method should only be
     * temporarily overridden (for example, to return {@code STRICT_STUBS}) to debug / improve the
     * test (for example, to remove unnecessary expectations after some refactoring).
     *
     * @return {@link Strictness#LENIENT} by default
     */
    protected Strictness getStrictness() {
        return Strictness.LENIENT;
    }
}
