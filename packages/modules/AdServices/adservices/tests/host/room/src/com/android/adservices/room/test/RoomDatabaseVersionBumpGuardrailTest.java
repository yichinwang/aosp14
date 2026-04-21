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

package com.android.adservices.room.test;

import android.cts.install.lib.host.InstallUtilsHost;

import com.android.tradefed.config.Option;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Asserts Room database version get bumped up between M-trains.
 *
 * <p>Since the Room json schema file only live in the UnitTest package, we need to download and
 * read the json from the zip.
 *
 * <p>The test will compare the highest version between mainline branch and M-train release build.
 *
 * <p>If M-train build contains a certain DB, then:
 *
 * <ol>
 *   <li>The DB must present in the new version;
 *   <li>If highest version in the new version is the same, the schema file should stay same; and
 *   <li>Database version should never go down.
 * </ol>
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class RoomDatabaseVersionBumpGuardrailTest extends BaseHostJUnit4Test {

    // This is not a perfect matcher as it could be fragile of file with other naming strategy.
    private static final Pattern SCHEMA_FILE_PATTERN =
            Pattern.compile("^assets/com\\.android\\..*/\\d*\\.json$");
    public static final String AD_SERVICES_SERVICE_CORE_UNIT_TESTS_APK_FILE_NAME =
            "AdServicesServiceCoreUnitTests.apk";
    protected final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);

    // The schema lib should be configured in gcl to point to the right package.
    @Option(name = "base-schema-lib")
    protected String mBaseSchemaLib;

    @Option(name = "new-schema-lib")
    protected String mNewSchemaLib;

    @Test
    public void roomDatabaseVersionBumpGuardrailTest() throws Exception {

        ZipFile baseSchemas = getTestPackageFromFile(mBaseSchemaLib);
        ZipFile newSchemas = getTestPackageFromFile(mNewSchemaLib);

        Map<String, ZipEntry> baseVersions = extractVersionMap(baseSchemas);
        Map<String, ZipEntry> newVersions = extractVersionMap(newSchemas);

        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, ZipEntry> e : baseVersions.entrySet()) {
            String name = e.getKey();
            ZipEntry baseFile = e.getValue();
            ZipEntry newFile = newVersions.get(name);

            if (newFile == null) {
                stringBuilder.append(
                        String.format("Database file '%s' missing in new version!\n", name));
                continue;
            }

            int baseFileVersion = getVersionFromFile(baseFile);
            int newFileVersion = getVersionFromFile(newFile);

            if (baseFileVersion == newFileVersion) {
                if (!Arrays.equals(
                        baseSchemas.getInputStream(baseFile).readAllBytes(),
                        newSchemas.getInputStream(newFile).readAllBytes())) {
                    stringBuilder.append(
                            String.format(
                                    "Database file '%s' changed between major build. Please bump up"
                                            + " DB version.\n",
                                    name));
                }
            }

            if (getVersionFromFile(baseFile) > getVersionFromFile(newFile)) {
                stringBuilder.append(
                        String.format(
                                "Database %s version was turned down from %d to %d\n",
                                name, baseFileVersion, newFileVersion));
            }
        }

        if (stringBuilder.length() != 0) {
            throw new IllegalStateException(stringBuilder.toString());
        }
    }

    private ZipFile getTestPackageFromFile(String lib) throws IOException {
        byte[] bytes;
        try (ZipFile zipFile = new ZipFile(mHostUtils.getTestFile(lib))) {
            ZipEntry entry =
                    getZipEntry(zipFile, AD_SERVICES_SERVICE_CORE_UNIT_TESTS_APK_FILE_NAME);
            bytes = zipFile.getInputStream(entry).readAllBytes();
        }
        File tempFile = File.createTempFile("schemaFiles", ".zip");
        tempFile.setWritable(true);
        new FileOutputStream(tempFile).write(bytes);
        return new ZipFile(tempFile);
    }

    private static ZipEntry getZipEntry(ZipFile zipFile, String fileName) {
        Iterator<? extends ZipEntry> iterator = zipFile.entries().asIterator();
        while (iterator.hasNext()) {
            ZipEntry current = iterator.next();
            if (current.getName().contains(fileName)) {
                return current;
            }
        }
        return null;
    }

    private static Map<String, ZipEntry> extractVersionMap(ZipFile files) {

        return files.stream()
                .filter(f -> SCHEMA_FILE_PATTERN.matcher(f.getName()).matches())
                .collect(
                        Collectors.groupingBy(
                                RoomDatabaseVersionBumpGuardrailTest::getDatabaseNameFromFile))
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                e ->
                                        e.getValue().stream()
                                                .max(
                                                        Comparator.comparing(
                                                                RoomDatabaseVersionBumpGuardrailTest
                                                                        ::getVersionFromFile))
                                                .get()));
    }

    private static String getDatabaseNameFromFile(ZipEntry f) {
        return f.getName().split("/")[1];
    }

    private static int getVersionFromFile(ZipEntry fileName) {
        return Integer.parseInt(fileName.getName().split("/")[2].split("\\.")[0]);
    }
}
