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
package com.android.adservices.shared.testing.common;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Provides helpers for file-related operations. */
public final class FileHelper {

    private static final String TAG = FileHelper.class.getSimpleName();

    private static final String SD_CARD_DIR = "/sdcard";
    private static final String ADSERVICES_TEST_DIR =
            Environment.DIRECTORY_DOCUMENTS + "/adservices-tests";

    // TODO(b/313646338): add unit tests
    /** Writes a text file to {@link #getAdServicesTestsOutputDir()}. */
    public static void writeFile(String filename, String contents) {
        String userFriendlyFilename = filename;
        try {
            File dir = getAdServicesTestsOutputDir();
            Path filePath = Paths.get(dir.getAbsolutePath(), filename);
            userFriendlyFilename = filePath.toString();
            Log.i(TAG, "Creating file " + userFriendlyFilename);
            Files.createFile(filePath);
            byte[] bytes = contents.getBytes();
            Log.d(TAG, "Writing " + bytes.length + " bytes to " + filePath);
            Files.write(filePath, bytes);
            Log.d(TAG, "Saul Goodman!");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save " + userFriendlyFilename, e);
        }
    }

    // TODO(b/313646338): add unit tests
    /**
     * Writes a file to {@value #SD_CARD_DIR} under {@value #ADSERVICES_TEST_DIR}
     *
     * <p>NOTE: add a {@code com.android.tradefed.device.metric.FilePullerLogCollector} in the test
     * manifest, pointing to {@code /sdcard/Documents/adservices-tests}, so tests written to this
     * directory surface as artifacts when the test fails in the cloud.
     */
    public static File getAdServicesTestsOutputDir() {
        String path = SD_CARD_DIR + "/" + ADSERVICES_TEST_DIR;
        File dir = new File(path);
        if (dir.exists()) {
            return dir;
        }
        Log.d(TAG, "Directory " + path + " doesn't exist, creating it");
        if (dir.mkdirs()) {
            Log.i(TAG, "Created directory " + path);
            return dir;
        }
        throw new IllegalStateException("Could not create directory " + path);
    }

    private FileHelper() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
