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

package com.android.tests.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.ConfigListener;
import android.app.sdksandbox.testutils.DeviceConfigUtils;
import android.app.sdksandbox.testutils.EmptyActivity;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.DeviceConfig;
import android.util.ArraySet;
import android.webkit.WebViewUpdateService;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.tests.sdkprovider.restrictions.contentproviders.IContentProvidersSdkApi;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;

@RunWith(JUnit4.class)
public class ContentProviderRestrictionsTestApp {
    private SdkSandboxManager mSdkSandboxManager;

    // Keep the value consistent with SdkSandboxmanagerService.ENFORCE_RESTRICTIONS
    private static final String ENFORCE_RESTRICTIONS = "sdksandbox_enforce_restrictions";

    // Keep the value consistent with SdkSandboxmanagerService.PROPERTY_CONTENTPROVIDER_ALLOWLIST.
    private static final String PROPERTY_CONTENTPROVIDER_ALLOWLIST =
            "contentprovider_allowlist_per_targetSdkVersion";

    // Keep the value consistent with
    // SdkSandboxManagerService.PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS.
    private static final String PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS =
            "apply_sdk_sandbox_next_restrictions";

    // Keep the value consistent with
    // SdkSandboxManagerService.PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST.
    private static final String PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST =
            "sdksandbox_next_contentprovider_allowlist";

    private static final String SDK_PACKAGE =
            "com.android.tests.sdkprovider.restrictions.contentproviders";

    private static final String NAMESPACE = DeviceConfig.NAMESPACE_ADSERVICES;

    static final ArraySet<String> DEFAULT_CONTENTPROVIDER_ALLOWED_AUTHORITIES =
            new ArraySet<>(
                    Arrays.asList(
                            "settings/system",
                            "com.android.textclassifier.icons",
                            "downloads/my_downloads"));

    /** This rule is defined to start an activity in the foreground to call the sandbox APIs */
    @Rule public final ActivityScenarioRule mRule = new ActivityScenarioRule<>(EmptyActivity.class);

    private String mEnforceRestrictions;
    private String mInitialContentProviderAllowlistValue;
    private String mInitialApplyNextContentProviderAllowlistValue;
    private String mInitialNextContentProviderAllowlistValue;

    private IContentProvidersSdkApi mContentProvidersSdkApi;
    private ConfigListener mConfigListener;
    private DeviceConfigUtils mDeviceConfigUtils;

    @Before
    public void setup() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.READ_DEVICE_CONFIG);

        mConfigListener = new ConfigListener();
        DeviceConfig.addOnPropertiesChangedListener(
                NAMESPACE, context.getMainExecutor(), mConfigListener);
        mDeviceConfigUtils = new DeviceConfigUtils(mConfigListener, NAMESPACE);

        mEnforceRestrictions =
                DeviceConfig.getProperty(DeviceConfig.NAMESPACE_ADSERVICES, ENFORCE_RESTRICTIONS);
        mInitialContentProviderAllowlistValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_CONTENTPROVIDER_ALLOWLIST);
        mInitialApplyNextContentProviderAllowlistValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS);
        mInitialNextContentProviderAllowlistValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST);

        mDeviceConfigUtils.deleteProperty(ENFORCE_RESTRICTIONS);
        mDeviceConfigUtils.deleteProperty(PROPERTY_CONTENTPROVIDER_ALLOWLIST);
        mDeviceConfigUtils.deleteProperty(PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS);
        mDeviceConfigUtils.deleteProperty(PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST);

        mRule.getScenario();
    }

    @After
    public void teardown() throws Exception {
        mDeviceConfigUtils.resetToInitialValue(ENFORCE_RESTRICTIONS, mEnforceRestrictions);
        mDeviceConfigUtils.resetToInitialValue(
                PROPERTY_CONTENTPROVIDER_ALLOWLIST, mInitialContentProviderAllowlistValue);
        mDeviceConfigUtils.resetToInitialValue(
                PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS,
                mInitialApplyNextContentProviderAllowlistValue);
        mDeviceConfigUtils.resetToInitialValue(
                PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST, mInitialNextContentProviderAllowlistValue);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();

        // Greedily unload SDK to reduce flakiness
        mSdkSandboxManager.unloadSdk(SDK_PACKAGE);
    }

    @Test
    public void testGetContentProvider_restrictionsApplied() throws Exception {
        mDeviceConfigUtils.setProperty(ENFORCE_RESTRICTIONS, "true");
        loadSdk();
        assertThrows(SecurityException.class, () -> mContentProvidersSdkApi.getContentProvider());
    }

    @Test
    public void testRegisterContentObserver_restrictionsApplied() throws Exception {
        mDeviceConfigUtils.setProperty(ENFORCE_RESTRICTIONS, "true");
        loadSdk();
        assertThrows(
                SecurityException.class, () -> mContentProvidersSdkApi.registerContentObserver());
    }

    @Test
    public void testGetContentProvider_DeviceConfigAllowlistApplied() throws Exception {
        mDeviceConfigUtils.setProperty(ENFORCE_RESTRICTIONS, "true");

        /**
         * Base64 encoded proto ContentProviderAllowlists containing allowlist_per_target_sdk { key:
         * 34 value { authorities: "com.android.textclassifier.icons" authorities: "user_dictionary"
         * } }
         *
         * <p>allowlist_per_target_sdk { key: 35 value { authorities:
         * "com.android.textclassifier.icons" authorities: "user_dictionary" } }
         */
        String encodedAllowlist =
                "CjcIIhIzCiBjb20uYW5kcm9pZC50ZXh0Y2xhc3NpZmllci5pY29ucwoPdXNlcl9kaWN0aW9uYXJ5CjcII"
                        + "xIzCiBjb20uYW5kcm9pZC50ZXh0Y2xhc3NpZmllci5pY29ucwoPdXNlcl9kaWN0aW9uYXJ5";
        mDeviceConfigUtils.setProperty(PROPERTY_CONTENTPROVIDER_ALLOWLIST, encodedAllowlist);

        loadSdk();
        mContentProvidersSdkApi.getContentProviderByAuthority("com.android.textclassifier.icons");
        mContentProvidersSdkApi.getContentProvider();

        assertThrows(
                SecurityException.class,
                () ->
                        mContentProvidersSdkApi.getContentProviderByAuthority(
                                "com.android.contacts.dumpfile/a-contacts-db.zip"));
    }

    @Test
    public void testGetContentProvider_DeviceConfigNextAllowlistApplied() throws Exception {
        mDeviceConfigUtils.setProperty(ENFORCE_RESTRICTIONS, "true");
        mDeviceConfigUtils.setProperty(PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "true");

        // Base64 encoded proto AllowedContentProviders containing the string
        // 'com.android.textclassifier.icons'
        String encodedNextAllowlist = "CiBjb20uYW5kcm9pZC50ZXh0Y2xhc3NpZmllci5pY29ucw==";
        // Set the canary set.
        mDeviceConfigUtils.setProperty(
                PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST, encodedNextAllowlist);

        // Base64 encoded proto ContentProviderAllowlists containing mappings to the string
        // 'com.android.textclassifier.icons' and 'user_dictionary'.
        String encodedAllowlist =
                "CjcIIhIzCiBjb20uYW5kcm9pZC50ZXh0Y2xhc3NpZmllci5pY29ucwoPdXNlcl9kaWN0aW9uYXJ5";
        // Also set the non-canary allowlist to verify that this allowlist is not applied when the
        // canary flag is set.
        mDeviceConfigUtils.setProperty(PROPERTY_CONTENTPROVIDER_ALLOWLIST, encodedAllowlist);

        loadSdk();
        mContentProvidersSdkApi.getContentProviderByAuthority("com.android.textclassifier.icons");
        assertThrows(SecurityException.class, () -> mContentProvidersSdkApi.getContentProvider());
        assertThrows(
                SecurityException.class,
                () ->
                        mContentProvidersSdkApi.getContentProviderByAuthority(
                                "com.android.contacts.dumpfile/a-contacts-db.zip"));
    }

    @Test
    public void testGetContentProvider_DeviceConfigWildcardAllowlistApplied() throws Exception {
        mDeviceConfigUtils.setProperty(ENFORCE_RESTRICTIONS, "true");

        /*
         * Base64 encoded proto ContentProviderAllowlists in the following form:
         * allowlist_per_target_sdk {
         *   key: 34
         *   value {
         *     authorities: "*"
         *   }
         * }
         */
        String encodedAllowlist = "CgcIIhIDCgEq";
        mDeviceConfigUtils.setProperty(PROPERTY_CONTENTPROVIDER_ALLOWLIST, encodedAllowlist);

        loadSdk();
        // All kinds of ContentProviders should be accessible.
        mContentProvidersSdkApi.getContentProviderByAuthority("com.android.textclassifier.icons");
        mContentProvidersSdkApi.getContentProvider();
        mContentProvidersSdkApi.getContentProviderByAuthority(
                "com.android.contacts.dumpfile/a-contacts-db.zip");
    }

    @Test
    public void testGetContentProvider_DeviceConfigAllowlistWithWildcardApplied() throws Exception {
        mDeviceConfigUtils.setProperty(ENFORCE_RESTRICTIONS, "true");

        /*
         * Base64 encoded proto ContentProviderAllowlists in the following form:
         * allowlist_per_target_sdk {
         *   key: 34
         *   value {
         *     authorities: "com.android.contacts.*"
         *   }
         * }
         */
        String encodedAllowlist = "ChwIIhIYChZjb20uYW5kcm9pZC5jb250YWN0cy4q";
        mDeviceConfigUtils.setProperty(PROPERTY_CONTENTPROVIDER_ALLOWLIST, encodedAllowlist);
        loadSdk();
        mContentProvidersSdkApi.getContentProviderByAuthority(
                "com.android.contacts.dumpfile/a-contacts-db.zip");
        assertThrows(SecurityException.class, () -> mContentProvidersSdkApi.getContentProvider());
        assertThrows(
                SecurityException.class,
                () ->
                        mContentProvidersSdkApi.getContentProviderByAuthority(
                                "com.android.textclassifier.icons"));
    }

    @Test(expected = Test.None.class /* no exception expected */)
    public void testGetWebViewContentProvider_restrictionsApplied() throws Exception {
        mDeviceConfigUtils.setProperty(ENFORCE_RESTRICTIONS, "true");
        loadSdk();
        mContentProvidersSdkApi.getContentProviderByAuthority(
                WebViewUpdateService.getCurrentWebViewPackageName()
                        + ".DeveloperModeContentProvider");
        mContentProvidersSdkApi.getContentProviderByAuthority(
                WebViewUpdateService.getCurrentWebViewPackageName() + ".SafeModeContentProvider");
    }

    @Test(expected = Test.None.class /* no exception expected */)
    public void testGetContentProvider_restrictionsNotApplied() throws Exception {
        mDeviceConfigUtils.setProperty(ENFORCE_RESTRICTIONS, "false");
        loadSdk();
        mContentProvidersSdkApi.getContentProvider();
    }

    @Test(expected = Test.None.class /* no exception expected */)
    public void testRegisterContentObserver_restrictionsNotApplied() throws Exception {
        mDeviceConfigUtils.setProperty(ENFORCE_RESTRICTIONS, "false");
        loadSdk();
        mContentProvidersSdkApi.registerContentObserver();
    }

    @Test(expected = Test.None.class /* no exception expected */)
    public void testGetContentProvider_defaultValueRestrictionsApplied() throws Exception {
        loadSdk();
        assertThrows(SecurityException.class, () -> mContentProvidersSdkApi.getContentProvider());
    }

    @Test(expected = Test.None.class /* no exception expected */)
    public void testRegisterContentObserver_defaultValueRestrictionsApplied() throws Exception {
        loadSdk();
        assertThrows(
                SecurityException.class, () -> mContentProvidersSdkApi.registerContentObserver());
    }

    @Test
    public void testGetContentProvider_defaultAllowlist() throws Exception {
        loadSdk();
        verifyDefaultAllowlistAccess(/*isAccessExpected=*/ true);
        assertThrows(SecurityException.class, () -> mContentProvidersSdkApi.getContentProvider());
    }

    @Test
    public void
            testGetContentProvider_nextAllowlistApplied_allAllowlistsAbsent_appliesDefaultAllowlist()
                    throws Exception {
        mDeviceConfigUtils.setProperty(PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "true");
        mDeviceConfigUtils.deleteProperty(PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST);
        loadSdk();
        verifyDefaultAllowlistAccess(/*isAccessExpected=*/ true);
        assertThrows(SecurityException.class, () -> mContentProvidersSdkApi.getContentProvider());
    }

    @Test
    public void
            testGetContentProvider_nextAllowlistApplied_currentAllowlistPresent_appliesCurrentAllowlist_allowlistForTargetSdkVersion()
                    throws Exception {
        mDeviceConfigUtils.setProperty(PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "true");

        /*
         * Base64 encoded proto ContentProviderAllowlists in the following form:
         * allowlist_per_target_sdk {
         *   key: 34
         *   value {
         *     authorities: "com.android.contacts.*"
         *   }
         * }
         */
        String encodedAllowlist = "ChwIIhIYChZjb20uYW5kcm9pZC5jb250YWN0cy4q";
        mDeviceConfigUtils.setProperty(PROPERTY_CONTENTPROVIDER_ALLOWLIST, encodedAllowlist);
        loadSdk();
        mContentProvidersSdkApi.getContentProviderByAuthority(
                "com.android.contacts.dumpfile/a-contacts-db.zip");
        assertThrows(SecurityException.class, () -> mContentProvidersSdkApi.getContentProvider());
        verifyDefaultAllowlistAccess(/*isAccessExpected=*/ false);
    }

    private void loadSdk() {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        mContentProvidersSdkApi = IContentProvidersSdkApi.Stub.asInterface(binder);
    }

    private void verifyDefaultAllowlistAccess(boolean isAccessExpected) throws Exception {
        for (String authority : DEFAULT_CONTENTPROVIDER_ALLOWED_AUTHORITIES) {
            if (isAccessExpected) {
                mContentProvidersSdkApi.getContentProviderByAuthority(authority);
            } else {
                assertThrows(
                        SecurityException.class,
                        () -> mContentProvidersSdkApi.getContentProviderByAuthority(authority));
            }
        }
    }
}
