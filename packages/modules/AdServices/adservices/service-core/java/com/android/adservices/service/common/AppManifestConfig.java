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

import static com.android.adservices.service.common.AppManifestConfigParser.TAG_ADID;
import static com.android.adservices.service.common.AppManifestConfigParser.TAG_APPSETID;
import static com.android.adservices.service.common.AppManifestConfigParser.TAG_ATTRIBUTION;
import static com.android.adservices.service.common.AppManifestConfigParser.TAG_CUSTOM_AUDIENCES;
import static com.android.adservices.service.common.AppManifestConfigParser.TAG_TOPICS;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.adservices.LogUtil;

import java.util.function.Supplier;

/** The object representing the AdServices manifest config. */
public final class AppManifestConfig {
    @NonNull private final AppManifestIncludesSdkLibraryConfig mIncludesSdkLibraryConfig;
    @Nullable private final AppManifestAttributionConfig mAttributionConfig;
    @Nullable private final AppManifestCustomAudiencesConfig mCustomAudiencesConfig;
    @Nullable private final AppManifestTopicsConfig mTopicsConfig;
    @Nullable private final AppManifestAdIdConfig mAdIdConfig;
    @Nullable private final AppManifestAppSetIdConfig mAppSetIdConfig;
    // TODO(b/297585683): remove this attribute (and update test cases) once it's always enabled by
    // default (it will be initially determined by a flag).
    private final boolean mEnabledByDefault;

    /**
     * AdServices manifest config must contain configs for Attribution, Custom Audiences, AdId,
     * AppSetId and Topics.
     *
     * <p>If any tags (except for the {@code <includes-sdk-library>} tag) are not found in the ad
     * services config, these configs will be {@code null}.
     *
     * @param includesSdkLibraryConfig the list of Sdk Libraries included in the app.
     * @param attributionConfig the config for Attribution.
     * @param customAudiencesConfig the config for Custom Audiences.
     * @param topicsConfig the config for Topics.
     * @param adIdConfig the config for adId.
     * @param appSetIdConfig the config for appSetId.
     * @param enabledByDefault whether APIs should be enabled by default when missing from the
     */
    public AppManifestConfig(
            @NonNull AppManifestIncludesSdkLibraryConfig includesSdkLibraryConfig,
            @Nullable AppManifestAttributionConfig attributionConfig,
            @Nullable AppManifestCustomAudiencesConfig customAudiencesConfig,
            @Nullable AppManifestTopicsConfig topicsConfig,
            @Nullable AppManifestAdIdConfig adIdConfig,
            @Nullable AppManifestAppSetIdConfig appSetIdConfig,
            boolean enabledByDefault) {
        mIncludesSdkLibraryConfig = includesSdkLibraryConfig;
        mAttributionConfig = attributionConfig;
        mCustomAudiencesConfig = customAudiencesConfig;
        mTopicsConfig = topicsConfig;
        mAdIdConfig = adIdConfig;
        mAppSetIdConfig = appSetIdConfig;
        mEnabledByDefault = enabledByDefault;
    }

    /** Getter for IncludesSdkLibraryConfig. */
    @NonNull
    public AppManifestIncludesSdkLibraryConfig getIncludesSdkLibraryConfig() {
        return mIncludesSdkLibraryConfig;
    }

    /**
     * Getter for AttributionConfig.
     *
     * <p>If the tag is not found in the app manifest config, this config is {@code null}.
     */
    @Nullable
    public AppManifestAttributionConfig getAttributionConfig() {
        return getConfig(
                TAG_ATTRIBUTION,
                mAttributionConfig,
                AppManifestAttributionConfig::getEnabledByDefaultInstance);
    }

    /**
     * Returns if the ad partner is permitted to access Attribution API for config represented by
     * this object.
     *
     * <p>If the tag is not found in the app manifest config, returns {@code false}.
     */
    public boolean isAllowedAttributionAccess(@NonNull String enrollmentId) {
        return isAllowedAccess(TAG_ATTRIBUTION, mAttributionConfig, enrollmentId);
    }

    /**
     * Getter for CustomAudiencesConfig.
     *
     * <p>If the tag is not found in the app manifest config, this config is {@code null}.
     */
    @Nullable
    public AppManifestCustomAudiencesConfig getCustomAudiencesConfig() {
        return getConfig(
                TAG_CUSTOM_AUDIENCES,
                mCustomAudiencesConfig,
                AppManifestCustomAudiencesConfig::getEnabledByDefaultInstance);
    }

    /**
     * Returns {@code true} if an ad tech with the given enrollment ID is permitted to access Custom
     * Audience API for config represented by this object.
     *
     * <p>If the tag is not found in the app manifest config, returns {@code false}.
     */
    public boolean isAllowedCustomAudiencesAccess(@NonNull String enrollmentId) {
        return isAllowedAccess(TAG_CUSTOM_AUDIENCES, mCustomAudiencesConfig, enrollmentId);
    }

    /**
     * Getter for TopicsConfig.
     *
     * <p>If the tag is not found in the app manifest config, this config is {@code null}.
     */
    @Nullable
    public AppManifestTopicsConfig getTopicsConfig() {
        return getConfig(
                TAG_TOPICS, mTopicsConfig, AppManifestTopicsConfig::getEnabledByDefaultInstance);
    }

    /**
     * Returns if the ad partner is permitted to access Topics API for config represented by this
     * object.
     *
     * <p>If the tag is not found in the app manifest config, returns {@code false}.
     */
    public boolean isAllowedTopicsAccess(@NonNull String enrollmentId) {
        return isAllowedAccess(TAG_TOPICS, mTopicsConfig, enrollmentId);
    }

    /**
     * Getter for AdIdConfig.
     *
     * <p>If the tag is not found in the app manifest config, this config is {@code null}.
     */
    @Nullable
    public AppManifestAdIdConfig getAdIdConfig() {
        return getConfig(TAG_ADID, mAdIdConfig, AppManifestAdIdConfig::getEnabledByDefaultInstance);
    }

    /**
     * Returns if sdk is permitted to access AdId API for config represented by this object.
     *
     * <p>If the tag is not found in the app manifest config, returns {@code false}.
     */
    public boolean isAllowedAdIdAccess(@NonNull String sdk) {
        return isAllowedAccess(TAG_ADID, mAdIdConfig, sdk);
    }

    /**
     * Getter for AppSetIdConfig.
     *
     * <p>If the tag is not found in the app manifest config, this config is {@code null}.
     */
    @Nullable
    public AppManifestAppSetIdConfig getAppSetIdConfig() {
        return getConfig(
                TAG_APPSETID,
                mAppSetIdConfig,
                AppManifestAppSetIdConfig::getEnabledByDefaultInstance);
    }

    /**
     * Returns if sdk is permitted to access AppSetId API for config represented by this object.
     *
     * <p>If the tag is not found in the app manifest config, returns {@code false}.
     */
    public boolean isAllowedAppSetIdAccess(@NonNull String sdk) {
        return isAllowedAccess(TAG_APPSETID, mAppSetIdConfig, sdk);
    }

    private <T extends AppManifestApiConfig> T getConfig(
            String tag, @Nullable T config, Supplier<T> supplier) {
        if (config != null) {
            return config;
        }
        if (mEnabledByDefault) {
            LogUtil.v("app manifest config tag '%s' not found, returning default", tag);
            return supplier.get();
        }
        LogUtil.v("app manifest config tag '%s' not found, returning null", tag);
        return null;
    }

    private boolean isAllowedAccess(
            String tag, @Nullable AppManifestApiConfig config, String partnerId) {
        if (config == null) {
            LogUtil.v(
                    "app manifest config tag '%s' not found, returning %b", tag, mEnabledByDefault);
            return mEnabledByDefault;
        }

        return config.getAllowAllToAccess()
                || config.getAllowAdPartnersToAccess().contains(partnerId);
    }
}
