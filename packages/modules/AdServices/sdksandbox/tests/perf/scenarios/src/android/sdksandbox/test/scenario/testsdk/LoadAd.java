/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.sdksandbox.test.scenario.testsdk;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;


import android.os.Bundle;
import android.platform.test.scenario.annotation.Scenario;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

@Scenario
@RunWith(JUnit4.class)
public class LoadAd {
    private static final UiDevice sUiDevice = UiDevice.getInstance(getInstrumentation());
    private static final Bundle sArgsBundle = InstrumentationRegistry.getArguments();

    private static final String CLIENT_APP = "com.google.android.libraries.internal.exampleclient";
    private static final String MEDIATION_ENABLED_KEY = "mediation_enabled";
    private static final int WAIT_TIME_BEFORE_END_TEST_MS = 3000;

    private static final ClientAppUtils sClientAppUtils = new ClientAppUtils(CLIENT_APP);

    @AfterClass
    public static void tearDown() throws IOException {
        sUiDevice.executeShellCommand(sClientAppUtils.getStopAppCommand());
    }

    @Before
    public void setup() throws Exception {
        boolean mediationEnabled =
                Boolean.parseBoolean(sArgsBundle.getString(MEDIATION_ENABLED_KEY));
        sUiDevice.executeShellCommand(sClientAppUtils.getStartAppCommand(mediationEnabled));
    }

    @Test
    public void testLoadAd() throws Exception {
        sClientAppUtils.loadAd(sUiDevice);
        sClientAppUtils.assertAdLoaded(sUiDevice);
        Thread.sleep(WAIT_TIME_BEFORE_END_TEST_MS);
    }
}
