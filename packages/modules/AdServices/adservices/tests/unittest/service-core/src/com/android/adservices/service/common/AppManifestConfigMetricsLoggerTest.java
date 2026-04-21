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

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockErrorLogUtilWithThrowable;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockErrorLogUtilWithoutThrowable;
import static com.android.adservices.service.common.AppManifestConfigMetricsLogger.dump;
import static com.android.adservices.service.common.AppManifestConfigMetricsLogger.PREFS_NAME;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;

import androidx.test.filters.FlakyTest;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.Nullable;
import com.android.adservices.common.SyncCallback;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.ExtendedMockitoExpectations.ErrorLogUtilCallback;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.testing.common.DumpHelper;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@SpyStatic(ErrorLogUtil.class)
@SpyStatic(FlagsFactory.class)
public final class AppManifestConfigMetricsLoggerTest extends AdServicesExtendedMockitoTestCase {

    private static final String PKG_NAME = "pkg.I.am";
    private static final String PKG_NAME2 = "or.not";

    static final boolean APP_EXISTS = true;
    static final boolean APP_DOES_NOT_EXIST = false;

    static final boolean APP_HAS_CONFIG = true;
    static final boolean APP_DOES_NOT_HAVE_CONFIG = false;

    static final boolean ENABLED_BY_DEFAULT = true;
    static final boolean NOT_ENABLED_BY_DEFAULT = false;

    @Mock private Context mMockContext;
    @Mock private Flags mMockFlags;

    private final FakeSharedPreferences mPrefs = new FakeSharedPreferences();

    private ErrorLogUtilCallback mErrorLogUtilWithThrowableCallback;
    private ErrorLogUtilCallback mErrorLogUtilWithoutThrowableCallback;

    @Before
    public void setExpectations() {
        appContext.set(mMockContext);
        extendedMockito.mockGetFlags(mMockFlags);
        when(mMockContext.getSharedPreferences(any(String.class), anyInt())).thenReturn(mPrefs);
        mErrorLogUtilWithThrowableCallback = mockErrorLogUtilWithThrowable();
        mErrorLogUtilWithoutThrowableCallback = mockErrorLogUtilWithoutThrowable();
    }

    @Test
    public void testLogUsage_nullArgs() throws Exception {
        assertThrows(
                NullPointerException.class,
                () ->
                        logUsageAndDontWait(
                                /* packageName= */ null,
                                APP_EXISTS,
                                APP_HAS_CONFIG,
                                ENABLED_BY_DEFAULT));
    }

    @Test
    public void testLogUsage_firstTime() throws Exception {
        logUsageAndWait(APP_EXISTS, APP_HAS_CONFIG, ENABLED_BY_DEFAULT);

        Map<String, ?> allProps = mPrefs.getAll();
        assertWithMessage("allProps").that(allProps).hasSize(1);
        assertWithMessage("properties keys").that(allProps.keySet()).containsExactly(PKG_NAME);
    }

    @Test
    public void testLogUsage_secondTimeSameArgs() throws Exception {
        // 1st time is fine
        logUsageAndWait(APP_EXISTS, APP_HAS_CONFIG, ENABLED_BY_DEFAULT);

        // 2nd time should not call edit
        mPrefs.onEditThrows(); // will throw if edit() is called
        logUsageAndDontWait(PKG_NAME, APP_EXISTS, APP_HAS_CONFIG, ENABLED_BY_DEFAULT);

        Map<String, ?> allProps = mPrefs.getAll();
        assertWithMessage("allProps").that(allProps).hasSize(1);
        assertWithMessage("properties keys").that(allProps.keySet()).containsExactly(PKG_NAME);
    }

    @FlakyTest(bugId = 315979774, detail = "Might need to split it into multiple tests")
    @Test
    public void testLogUsage_secondTimeDifferentArgs() throws Exception {
        callOnceWithAllTrueThenSecondWith(APP_EXISTS, APP_HAS_CONFIG, NOT_ENABLED_BY_DEFAULT);
        callOnceWithAllTrueThenSecondWith(APP_EXISTS, APP_DOES_NOT_HAVE_CONFIG, ENABLED_BY_DEFAULT);
        callOnceWithAllTrueThenSecondWith(
                APP_EXISTS, APP_DOES_NOT_HAVE_CONFIG, NOT_ENABLED_BY_DEFAULT);
        callOnceWithAllTrueThenSecondWith(APP_DOES_NOT_EXIST, APP_HAS_CONFIG, ENABLED_BY_DEFAULT);
        callOnceWithAllTrueThenSecondWith(
                APP_DOES_NOT_EXIST, APP_HAS_CONFIG, NOT_ENABLED_BY_DEFAULT);
        callOnceWithAllTrueThenSecondWith(
                APP_DOES_NOT_EXIST, APP_DOES_NOT_HAVE_CONFIG, ENABLED_BY_DEFAULT);
        callOnceWithAllTrueThenSecondWith(
                APP_DOES_NOT_EXIST, APP_DOES_NOT_HAVE_CONFIG, NOT_ENABLED_BY_DEFAULT);
    }

    private void callOnceWithAllTrueThenSecondWith(
            boolean appExists, boolean appHasConfig, boolean enabledByDefault) throws Exception {
        Log.i(
                mTag,
                "callOnceWithAllTrueThenSecondWith(appExists="
                        + appExists
                        + ", appHasConfig="
                        + appHasConfig
                        + ", enabledByDefault="
                        + enabledByDefault
                        + ")");
        // Need to use a new prefs because it's called multiple times (so it starts in a clean
        // state) - life would be so much easier if JUnit provided an easy way to run parameterized
        // tests per method (not class)
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        when(mMockContext.getSharedPreferences(any(String.class), anyInt())).thenReturn(prefs);

        // 1st call
        Log.d(
                mTag,
                "1st call: appExists="
                        + APP_EXISTS
                        + ", appHasConfig="
                        + APP_HAS_CONFIG
                        + ", enabledByDefault="
                        + ENABLED_BY_DEFAULT
                        + ")");
        logUsageAndWait(prefs, PKG_NAME, APP_EXISTS, APP_HAS_CONFIG, ENABLED_BY_DEFAULT);

        int valueBefore = prefs.getInt(PKG_NAME, -1);
        expect.withMessage(
                        "stored value of %s after 1st call (appExists=%s, appHasConfig=%s,"
                                + " enabledByDefault=%s)",
                        PKG_NAME, APP_EXISTS, APP_EXISTS, ENABLED_BY_DEFAULT)
                .that(valueBefore)
                .isNotEqualTo(-1);

        // 2nd call
        Log.d(
                mTag,
                "2nd call: appExists="
                        + appExists
                        + ", appHasConfig="
                        + appHasConfig
                        + ", enabledByDefault="
                        + enabledByDefault
                        + ")");
        logUsageAndWait(prefs, PKG_NAME, appExists, appHasConfig, enabledByDefault);

        Map<String, ?> allProps = prefs.getAll();
        expect.withMessage("allProps").that(allProps).hasSize(1);
        expect.withMessage("properties keys").that(allProps.keySet()).containsExactly(PKG_NAME);

        int valueAfter = prefs.getInt(PKG_NAME, -1);
        expect.withMessage(
                        "stored value of %s after 2nd call (appExists=%s, appHasConfig=%s,"
                                + " enabledByDefault=%s)",
                        PKG_NAME, appExists, appHasConfig, enabledByDefault)
                .that(valueAfter)
                .isNotEqualTo(-1);
        expect.withMessage(
                        "stored value of %s after 2nd call (appExists=%s, appHasConfig=%s,"
                                + " enabledByDefault=%s)",
                        PKG_NAME, appExists, appHasConfig, enabledByDefault)
                .that(valueAfter)
                .isNotEqualTo(valueBefore);
    }

    @Test
    public void testLogUsage_handlesRuntimeException() throws Exception {
        RuntimeException exception = new RuntimeException("D'OH!");

        when(mMockContext.getSharedPreferences(any(String.class), anyInt())).thenThrow(exception);

        logUsageAndDontWait(PKG_NAME, APP_EXISTS, APP_HAS_CONFIG, ENABLED_BY_DEFAULT);

        mErrorLogUtilWithThrowableCallback.assertReceived(
                expect,
                exception,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_EXCEPTION,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
    }

    @Test
    public void testLogUsage_commitFailed() throws Exception {
        mPrefs.onCommitReturns(/* result= */ false);

        logUsageAndDontWait(PKG_NAME, APP_EXISTS, APP_HAS_CONFIG, ENABLED_BY_DEFAULT);

        Map<String, ?> allProps = mPrefs.getAll();
        assertWithMessage("allProps").that(allProps).isEmpty();

        mErrorLogUtilWithoutThrowableCallback.assertReceived(
                expect,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
    }

    @Test
    public void testLogUsage_handlesToBgThread() throws Exception {
        Thread currentThread = Thread.currentThread();
        AtomicReference<Thread> executionThread = new AtomicReference<>();

        when(mMockContext.getSharedPreferences(any(String.class), anyInt()))
                .thenAnswer(
                        (inv) -> {
                            executionThread.set(Thread.currentThread());
                            return mPrefs;
                        });

        logUsageAndWait(mPrefs, PKG_NAME, APP_EXISTS, APP_HAS_CONFIG, ENABLED_BY_DEFAULT);

        assertWithMessage("execution thread")
                .that(executionThread.get())
                .isNotSameInstanceAs(currentThread);
    }

    @Test
    public void testDump_empty() throws Exception {
        when(mMockFlags.getAppConfigReturnsEnabledByDefault()).thenReturn(true);
        when(mMockContext.getDataDir()).thenReturn(new File("/la/la/land"));

        String dump = DumpHelper.dump(pw -> AppManifestConfigMetricsLogger.dump(mMockContext, pw));

        expect.withMessage("empty dump")
                .that(dump)
                .matches(
                        Pattern.compile(
                                ".*file:.*/la/la/land/shared_prefs/"
                                        + PREFS_NAME
                                        + "\\.xml.*\n"
                                        + ".*enabled by default: "
                                        + true
                                        + ".*\n"
                                        + ".*0 entries:.*",
                                Pattern.DOTALL));
    }

    @Test
    public void testDump_multipleEntries() throws Exception {
        logUsageAndWait(mPrefs, PKG_NAME, APP_EXISTS, APP_HAS_CONFIG, ENABLED_BY_DEFAULT);
        logUsageAndWait(
                mPrefs,
                PKG_NAME2,
                APP_DOES_NOT_EXIST,
                APP_DOES_NOT_HAVE_CONFIG,
                NOT_ENABLED_BY_DEFAULT);

        String dump = DumpHelper.dump(pw -> AppManifestConfigMetricsLogger.dump(mMockContext, pw));

        expect.withMessage("dump")
                .that(dump)
                .matches(
                        Pattern.compile(
                                ".*2 entries.*\n"
                                        + ".*"
                                        + PKG_NAME
                                        + ":.*appExists="
                                        + APP_EXISTS
                                        + ".*appHasConfig="
                                        + APP_HAS_CONFIG
                                        + ".*enabledByDefault="
                                        + ENABLED_BY_DEFAULT
                                        + "\n"
                                        + ".*"
                                        + PKG_NAME2
                                        + ":.*appExists="
                                        + APP_DOES_NOT_EXIST
                                        + ".*appHasConfig="
                                        + APP_DOES_NOT_HAVE_CONFIG
                                        + ".*enabledByDefault="
                                        + NOT_ENABLED_BY_DEFAULT
                                        + "\n",
                                Pattern.DOTALL));
    }

    // Needs to wait until the shared prefs is committed() as it happens in a separated thread
    private void logUsageAndWait(boolean appExists, boolean appHasConfig, boolean enabledByDefault)
            throws InterruptedException {
        logUsageAndWait(mPrefs, PKG_NAME, appExists, appHasConfig, enabledByDefault);
    }

    // Needs to wait until the shared prefs is committed() as it happens in a separated thread
    private void logUsageAndWait(
            SharedPreferences prefs,
            String appName,
            boolean appExists,
            boolean appHasConfig,
            boolean enabledByDefault)
            throws InterruptedException {
        SyncOnSharedPreferenceChangeListener listener = new SyncOnSharedPreferenceChangeListener();
        prefs.registerOnSharedPreferenceChangeListener(listener);
        try {
            AppManifestConfigCall call = new AppManifestConfigCall(appName);
            call.appExists = appExists;
            call.appHasConfig = appHasConfig;
            call.enabledByDefault = enabledByDefault;
            Log.v(mTag, "logUsageAndWait(call=" + call + ", listener=" + listener + ")");

            AppManifestConfigMetricsLogger.logUsage(call);
            String result = listener.assertResultReceived();
            Log.v(mTag, "result: " + result);
        } finally {
            mPrefs.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    // Should only be used in cases where the call is expect to not change the shared preferences
    // (in which case a listener would not be called)
    private void logUsageAndDontWait(
            String appName, boolean appExists, boolean appHasConfig, boolean enabledByDefault) {
        AppManifestConfigCall call = new AppManifestConfigCall(appName);
        call.appExists = appExists;
        call.appHasConfig = appHasConfig;
        call.enabledByDefault = enabledByDefault;
        Log.v(mTag, "logUsageAndDontWait(call=" + call + ")");
        AppManifestConfigMetricsLogger.logUsage(call);
    }

    /** Gets a custom Mockito matcher for a {@link AppManifestConfigCall}. */
    static AppManifestConfigCall appManifestConfigCall(
            String packageName, boolean appExists, boolean appHasConfig, boolean enabledByDefault) {
        return argThat(
                new AppManifestConfigCallMatcher(
                        packageName, appExists, appHasConfig, enabledByDefault));
    }

    private static final class AppManifestConfigCallMatcher
            implements ArgumentMatcher<AppManifestConfigCall> {

        private final String mPackageName;
        private final boolean mAppExists;
        private final boolean mAppHasConfig;
        private final boolean mEnabledByDefault;

        private AppManifestConfigCallMatcher(
                String packageName,
                boolean appExists,
                boolean appHasConfig,
                boolean enabledByDefault) {
            mPackageName = packageName;
            mAppExists = appExists;
            mAppHasConfig = appHasConfig;
            mEnabledByDefault = enabledByDefault;
        }

        @Override
        public boolean matches(AppManifestConfigCall arg) {
            return arg != null
                    && arg.packageName.equals(mPackageName)
                    && arg.appExists == mAppExists
                    && arg.appHasConfig == mAppHasConfig
                    && arg.enabledByDefault == mEnabledByDefault;
        }

        @Override
        public String toString() {
            AppManifestConfigCall call = new AppManifestConfigCall(mPackageName);
            call.appExists = mAppExists;
            call.appHasConfig = mAppHasConfig;
            call.enabledByDefault = mEnabledByDefault;

            return call.toString();
        }
    }

    // TODO(b/309857141): move to its own class / common package (it will be done in a later CL so
    // this class can be easily cherry picked into older releases).
    // TODO(b/309857141): add unit tests when move
    /**
     * Fake implementation of {@link SharedPreferences}.
     *
     * <p><b>Note: </b>calls made to the {@link #edit() editor} are persisted right away, unless
     * disabled by calls to {@link #onCommitReturns(boolean) onCommitReturns(false)} or {@link
     * #disableApply}.
     *
     * <p>This class is not thread safe.
     */
    public static final class FakeSharedPreferences implements SharedPreferences {

        private static final String TAG = FakeSharedPreferences.class.getSimpleName();

        private static final IllegalStateException EDIT_DISABLED_EXCEPTION =
                new IllegalStateException("edit() is not available");
        private static final IllegalStateException COMMIT_DISABLED_EXCEPTION =
                new IllegalStateException("commit() is not available");
        private static final IllegalStateException APPLY_DISABLED_EXCEPTION =
                new IllegalStateException("apply() is not available");

        private final FakeEditor mEditor = new FakeEditor();

        @Nullable private RuntimeException mEditException;

        /**
         * Changes behavior of {@link #edit()} so it throws an exception.
         *
         * @return the exception that will be thrown by {@link #edit()}.
         */
        public RuntimeException onEditThrows() {
            mEditException = EDIT_DISABLED_EXCEPTION;
            Log.v(TAG, "onEditThrows(): edit() will return " + mEditException);
            return mEditException;
        }

        /**
         * Sets the result of calls to {@link Editor#commit()}.
         *
         * <p><b>Note: </b>when called with {@code false}, calls made to the {@link #edit() editor}
         * after this call will be ignored (until it's called again with {@code true}).
         */
        public void onCommitReturns(boolean result) {
            mEditor.mCommitResult = result;
            Log.v(TAG, "onCommitReturns(): commit() will return " + mEditor.mCommitResult);
        }

        @Override
        public Map<String, ?> getAll() {
            return mEditor.mProps;
        }

        @Override
        public String getString(String key, String defValue) {
            return mEditor.get(key, String.class, defValue);
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            @SuppressWarnings("unchecked")
            Set<String> value = mEditor.get(key, Set.class, defValues);
            return value;
        }

        @Override
        public int getInt(String key, int defValue) {
            return mEditor.get(key, Integer.class, defValue);
        }

        @Override
        public long getLong(String key, long defValue) {
            return mEditor.get(key, Long.class, defValue);
        }

        @Override
        public float getFloat(String key, float defValue) {
            return mEditor.get(key, Float.class, defValue);
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return mEditor.get(key, Boolean.class, defValue);
        }

        @Override
        public boolean contains(String key) {
            return mEditor.mProps.containsKey(key);
        }

        @Override
        public FakeEditor edit() {
            Log.v(TAG, "edit(): mEditException=" + mEditException);
            if (mEditException != null) {
                throw mEditException;
            }
            return mEditor;
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
            mEditor.mListeners.add(listener);
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
            mEditor.mListeners.remove(listener);
        }

        private final class FakeEditor implements Editor {

            private final Map<String, Object> mProps = new LinkedHashMap<>();
            private final Set<String> mKeysToNotify = new LinkedHashSet<>();
            private final List<OnSharedPreferenceChangeListener> mListeners = new ArrayList<>();

            private boolean mCommitResult = true;

            @Override
            public Editor putString(String key, String value) {
                return put(key, value);
            }

            @Override
            public Editor putStringSet(String key, Set<String> values) {
                return put(key, values);
            }

            @Override
            public Editor putInt(String key, int value) {
                return put(key, value);
            }

            @Override
            public Editor putLong(String key, long value) {
                return put(key, value);
            }

            @Override
            public Editor putFloat(String key, float value) {
                return put(key, value);
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                return put(key, value);
            }

            @Override
            public Editor remove(String key) {
                Log.v(TAG, "remove(" + key + ")");
                mProps.remove(key);
                mKeysToNotify.add(key);
                return this;
            }

            @Override
            public Editor clear() {
                Log.v(TAG, "clear()");
                mProps.clear();
                return this;
            }

            @Override
            public boolean commit() {
                Log.v(TAG, "commit(): mCommitResult=" + mCommitResult);
                try {
                    return mCommitResult;
                } finally {
                    notifyListeners();
                }
            }

            @Override
            public void apply() {
                Log.v(TAG, "apply(): mCommitResult=" + mCommitResult);
                notifyListeners();
            }

            private <T> T get(String key, Class<T> clazz, T defValue) {
                Object value = mProps.get(key);
                return value == null ? defValue : clazz.cast(value);
            }

            private FakeEditor put(String key, Object value) {
                Log.v(
                        TAG,
                        "put(): "
                                + key
                                + "="
                                + value
                                + " ("
                                + value.getClass().getSimpleName()
                                + "): mCommitResult="
                                + mCommitResult);
                if (mCommitResult) {
                    mProps.put(key, value);
                    mKeysToNotify.add(key);
                }
                return mEditor;
            }

            private void notifyListeners() {
                for (OnSharedPreferenceChangeListener listener : mListeners) {
                    for (String key : mKeysToNotify) {
                        Log.v(TAG, "Notifying key change (" + key + ") to " + listener);
                        listener.onSharedPreferenceChanged(FakeSharedPreferences.this, key);
                    }
                }
                Log.v(TAG, "Clearing keys to notify (" + mKeysToNotify + ")");
                mKeysToNotify.clear();
            }
        }
    }

    // TODO(b/309857141): move to its own class / common package (it will be done in a later CL so
    // this class can be easily cherry picked into older releases).
    /**
     * OnSharedPreferenceChangeListener implementation that blocks until the first key is received.
     */
    public static final class SyncOnSharedPreferenceChangeListener
            extends SyncCallback<String, RuntimeException>
            implements OnSharedPreferenceChangeListener {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            injectResult(key);
        }
    }
}
