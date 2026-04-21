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
package android.ondevicepersonalization.test.scenario.ondevicepersonalization;

import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.Assert;

import java.io.IOException;

/** Helper class for interacting with download flow. */
public class DownloadHelper {
    private static UiDevice sUiDevice;

    // Download Url in APK points to a local location
    private static final String VENDOR_APK_LOCAL_URL_PATH =
            "data/temp_files/OdpSampleNetworkLocal.apk";
    // Download Url in APK points to a public url with file size ~ 100KB
    private static final String VENDOR_APK_SERVER_SMALL_PATH =
            "data/temp_files/OdpSampleNetworkSmall.apk";
    // Download Url in APK points to a public url with file size ~ 1MB
    private static final String VENDOR_APK_SERVER_MEDIUM_PATH =
            "data/temp_files/OdpSampleNetworkMedium.apk";
    // Download Url in APK points to a public url with file size ~ 10MB
    private static final String VENDOR_APK_SERVER_LARGE_PATH =
            "data/temp_files/OdpSampleNetworkLarge.apk";

    private static final String MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID = "1003";
    private static final String DOWNLOAD_PROCESSING_TASK_JOB_ID = "1004";
    private static final String MAINTENANCE_TASK_JOB_ID = "1005";

    /** Commands to prepare the device and odp module before testing. */
    public static void initialize() throws IOException {
        executeShellCommand(
                "device_config set_sync_disabled_for_tests persistent");
        executeShellCommand(
                "device_config put on_device_personalization global_kill_switch false");
        executeShellCommand(
                "device_config put on_device_personalization "
                    + "enable_ondevicepersonalization_apis true");
        executeShellCommand(
                "device_config put on_device_personalization "
                    + "enable_personalization_status_override true");
        executeShellCommand(
                "device_config put on_device_personalization "
                    + "personalization_status_override_value true");
        executeShellCommand("setprop log.tag.ondevicepersonalization VERBOSE");
        executeShellCommand(
                "am broadcast -a android.intent.action.BOOT_COMPLETED -p "
                    + "com.google.android.ondevicepersonalization.services");
        executeShellCommand(
                "cmd jobscheduler run -f "
                    + "com.google.android.ondevicepersonalization.services 1000");
        SystemClock.sleep(5000);
        executeShellCommand(
                "cmd jobscheduler run -f "
                    + "com.google.android.ondevicepersonalization.services 1006");
        SystemClock.sleep(5000);
    }

    /** Kill running processes to get performance measurement under cold start */
    public static void killRunningProcess() throws IOException {
        executeShellCommand("am kill com.google.android.ondevicepersonalization.services");
        executeShellCommand("am kill com.google.android.ondevicepersonalization.services:"
                + "com.android.ondevicepersonalization."
                + "libraries.plugin.internal.PluginExecutorService");
        SystemClock.sleep(2000);
    }
    public static void pressHome() {
        getUiDevice().pressHome();
    }

    public void installVendorApkWithLocalUrl() throws IOException {
        installVendorApk(VENDOR_APK_LOCAL_URL_PATH);
    }

    public void installVendorApkWithServerSmallFile() throws IOException {
        installVendorApk(VENDOR_APK_SERVER_SMALL_PATH);
    }

    public void installVendorApkWithServerMediumFile() throws IOException {
        installVendorApk(VENDOR_APK_SERVER_MEDIUM_PATH);
    }

    public void installVendorApkWithServerLargeFile() throws IOException {
        installVendorApk(VENDOR_APK_SERVER_LARGE_PATH);
    }

    private void installVendorApk(String path) throws IOException {
        executeShellCommand("pm install -r " + path);
    }

    public void uninstallVendorApk() throws IOException {
        executeShellCommand("pm uninstall com.android.odpsamplenetwork");
    }

    public void cleanupDatabase() throws IOException {
        executeShellCommand(
                "cmd jobscheduler run -f com.google.android.ondevicepersonalization.services "
                        + MAINTENANCE_TASK_JOB_ID);
        SystemClock.sleep(5000);
    }

    public void cleanupDownloadedMetadata() throws IOException {
        executeShellCommand(
                "cmd jobscheduler run -f com.google.android.ondevicepersonalization.services "
                        + MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID);
        SystemClock.sleep(5000);
    }

    public void downloadVendorData() throws IOException {
        executeShellCommand(
                "cmd jobscheduler run -f com.google.android.ondevicepersonalization.services "
                        + MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID);
        SystemClock.sleep(60000);
    }

    public void processDownloadedVendorData() throws IOException {
        executeShellCommand(
                "cmd jobscheduler run -f com.google.android.ondevicepersonalization.services "
                        + DOWNLOAD_PROCESSING_TASK_JOB_ID);
        SystemClock.sleep(5000);
    }

    private static void executeShellCommand(String cmd) {
        try {
            getUiDevice().executeShellCommand(cmd);
        } catch (IOException e) {
            Assert.fail("Failed to execute shell command: " + cmd + ". error: " + e);
        }
    }

    private static UiDevice getUiDevice() {
        if (sUiDevice == null) {
            sUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        }
        return sUiDevice;
    }

    /** Commands to return device to original state */
    public static void wrapUp() throws IOException {
        executeShellCommand(
                "device_config set_sync_disabled_for_tests none");
    }

}
