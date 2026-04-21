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

package android.adservices.test.scenario.adservices.ui;

import android.content.Context;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.tests.ui.libs.AdservicesWorkflows;
import com.android.adservices.tests.ui.libs.UiConstants;
import com.android.adservices.tests.ui.libs.UiUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Crystalball test for Topics API to collect System Heath metrics. */
@Scenario
@RunWith(JUnit4.class)
public class UiSettingsMainPage {
    private static final String TAG = "UiTestLabel";
    private static final String UI_SETTINGS_LATENCY_METRIC = "UI_SETTINGS_LATENCY_METRIC";

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static UiDevice sDevice;

    @Rule
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests().setCompatModeFlags();

    @Before
    public void setup() throws Exception {
        UiUtils.setFlipFlow(false);
        UiUtils.setAsEuDevice();
        UiUtils.enableGa();
        AdservicesTestHelper.killAdservicesProcess(sContext);

        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @After
    public void teardown() {
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @Test
    public void testSettingsPage() throws Exception {
        final long start = System.currentTimeMillis();
        AdservicesWorkflows.testSettingsPageFlow(
                sContext,
                sDevice,
                UiConstants.UX.GA_UX,
                /* isOptIn= */ true,
                /* isFlipConsent= */ true,
                /* assertOptIn= */ false);
        final long duration = System.currentTimeMillis() - start;
        Log.i(TAG, "(" + UI_SETTINGS_LATENCY_METRIC + ": " + duration + ")");
    }
}
