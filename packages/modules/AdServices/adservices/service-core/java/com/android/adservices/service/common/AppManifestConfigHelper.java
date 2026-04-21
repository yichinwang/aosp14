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

package com.android.adservices.service.common;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_MANIFEST_CONFIG_PARSING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import com.android.adservices.LogUtil;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.exception.XmlParseException;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.modules.utils.build.SdkLevel;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Objects;

/** Helper class for parsing and checking the app manifest config (<ad-services-config>). */
// TODO(b/213488783): Add persistence, so that lookup/parse is not on every request.
// Also consider if this should execute in the background.
public class AppManifestConfigHelper {
    public static final String AD_SERVICES_CONFIG_PROPERTY =
            "android.adservices.AD_SERVICES_CONFIG";
    private static final String ANDROID_MANIFEST_FILE = "AndroidManifest.xml";

    /**
     * Parses the app's manifest config to determine whether this sdk is permitted to use the
     * Attribution API.
     *
     * <p>If there is a parse error, it returns false.
     * @param appPackageName the package name of the app whose manifest config will be read.
     * @param enrollmentId the enrollment ID of the sdk that will be checked against the app's
     *     manifest config.
     */
    public static boolean isAllowedAttributionAccess(
            @NonNull String appPackageName,
            @NonNull String enrollmentId) {
        return isAllowedApiAccess(
                "isAllowedAttributionAccess()",
                appPackageName,
                enrollmentId,
                config -> config.isAllowedAttributionAccess(enrollmentId));
    }

    /**
     * Parses the app's manifest config to determine whether the given {@code enrollmentId}
     * associated with an ad tech is permitted to use the Custom Audience API.
     *
     * <p>If there is a parse error, it returns {@code false}.
     * @param appPackageName the package name of the app whose manifest config will be read
     * @param enrollmentId the enrollment ID associate with the ad tech
     */
    public static boolean isAllowedCustomAudiencesAccess(
            @NonNull String appPackageName,
            @NonNull String enrollmentId) {
        return isAllowedApiAccess(
                "isAllowedCustomAudiencesAccess()",
                appPackageName,
                enrollmentId,
                config -> config.isAllowedCustomAudiencesAccess(enrollmentId));
    }

    /**
     * Parses the app's manifest config to determine whether this sdk is permitted to use the Topics
     * API.
     *
     * <p>If there is a parse error, it returns false.
     * @param useSandboxCheck whether to use the sandbox check.
     * @param appPackageName the package name of the app whose manifest config will be read.
     * @param enrollmentId the enrollment ID of the sdk that will be checked against the app's
     *     manifest config.
     */
    public static boolean isAllowedTopicsAccess(
            @NonNull boolean useSandboxCheck,
            @NonNull String appPackageName,
            @NonNull String enrollmentId) {
        return isAllowedApiAccess(
                "isAllowedTopicsAccess()",
                appPackageName,
                enrollmentId,
                config -> {
                    // If the request comes directly from the app, check that the app has declared
                    // that it includes this Sdk library.
                    if (!useSandboxCheck) {
                        return config.getIncludesSdkLibraryConfig().contains(enrollmentId)
                                && config.isAllowedTopicsAccess(enrollmentId);
                    }

                    // If the request comes from the SdkRuntime, then the app had to have declared
                    // the Sdk using <uses-sdk-library>, so no need to check.
                    return config.isAllowedTopicsAccess(enrollmentId);
                });
    }

    @Nullable
    private static XmlResourceParser getXmlParser(
            @NonNull String appPackageName, boolean enabledByDefault)
            throws NameNotFoundException, XmlParseException, XmlPullParserException, IOException {
        Context context = ApplicationContextSingleton.get();
        LogUtil.v("getXmlParser(%s): context=%s", appPackageName, context);
        AppManifestConfigCall call = new AppManifestConfigCall(appPackageName);
        call.enabledByDefault = enabledByDefault;
        // NOTE: resources is only used pre-S, but it must be called regardless to make sure the
        // app exists
        Resources resources = null;
        try {
            resources = context.getPackageManager().getResourcesForApplication(appPackageName);
            call.appExists = true;
        } catch (NameNotFoundException e) {
            AppManifestConfigMetricsLogger.logUsage(call);
            throw e;
        }

        Integer resId =
                SdkLevel.isAtLeastS()
                        ? getAdServicesConfigResourceIdOnExistingPackageOnSPlus(
                                context, appPackageName)
                        : getAdServicesConfigResourceIdOnRMinus(context, resources, appPackageName);

        XmlResourceParser xmlResourceParser = null;
        if (resId != null) {
            xmlResourceParser = resources.getXml(resId);
            call.appHasConfig = true;
        }
        AppManifestConfigMetricsLogger.logUsage(call);
        return xmlResourceParser;
    }

    @Nullable
    private static Integer getAdServicesConfigResourceIdOnExistingPackageOnSPlus(
            @NonNull Context context, @NonNull String appPackageName) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageManager.Property property =
                    pm.getProperty(AD_SERVICES_CONFIG_PROPERTY, appPackageName);
            return property.getResourceId();
        } catch (NameNotFoundException e) {
            LogUtil.v("getAdServicesConfigResourceIdOnSPlus(%s) failed: %s", appPackageName, e);
            return null;
        }
    }

    @Nullable
    private static Integer getAdServicesConfigResourceIdOnRMinus(
            @NonNull Context context, @NonNull Resources resources, @NonNull String appPackageName)
            throws NameNotFoundException, XmlPullParserException, IOException {
        // PackageManager::getProperty(..) API is only available on S+. For R-, we will need to load
        // app's manifest and parse. See go/rbp-manifest.
        AssetManager assetManager =
                context.createPackageContext(appPackageName, /* flags= */ 0).getAssets();
        XmlResourceParser parser = assetManager.openXmlResourceParser(ANDROID_MANIFEST_FILE);
        return AndroidManifestConfigParser.getAdServicesConfigResourceId(parser, resources);
    }

    private static boolean isAllowedApiAccess(
            String method,
            String appPackageName,
            String enrollmentId,
            ApiAccessChecker checker) {
        Objects.requireNonNull(appPackageName);
        Objects.requireNonNull(enrollmentId);
        boolean enabledByDefault = FlagsFactory.getFlags().getAppConfigReturnsEnabledByDefault();
        try {
            XmlResourceParser in = getXmlParser(appPackageName, enabledByDefault);
            if (in == null) {
                LogUtil.v(
                        "%s: returning %b for app (%s) that doesn't have the AdServices XML config",
                        method, enabledByDefault, appPackageName);
                return enabledByDefault;
            }
            AppManifestConfig appManifestConfig =
                    AppManifestConfigParser.getConfig(in, enabledByDefault);
            return checker.isAllowedAccess(appManifestConfig);
        } catch (NameNotFoundException e) {
            LogUtil.v(
                    "Name not found while looking for manifest for app %s: %s", appPackageName, e);
        } catch (Exception e) {
            LogUtil.e(e, "App manifest parse failed.");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_MANIFEST_CONFIG_PARSING_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        }
        return false;
    }

    private interface ApiAccessChecker {
        boolean isAllowedAccess(AppManifestConfig config);
    }
}
