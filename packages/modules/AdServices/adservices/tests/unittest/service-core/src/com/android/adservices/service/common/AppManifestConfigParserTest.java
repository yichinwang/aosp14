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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.res.XmlResourceParser;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.exception.XmlParseException;
import com.android.adservices.servicecoretest.R;

import org.junit.Before;
import org.junit.Test;
import org.xmlpull.v1.XmlPullParserException;

@SmallTest
public final class AppManifestConfigParserTest extends AdServicesUnitTestCase {

    private Context mContext;
    private String mPackageName;

    @Before
    public void setContext() {
        mContext = appContext.get();
        mPackageName = mContext.getPackageName();
    }

    @Test
    public void testValidXml() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config);

        AppManifestConfig appManifestConfig = AppManifestConfigParser.getConfig(parser);
        assertWithMessage("manifest for ad_services_config").that(appManifestConfig).isNotNull();

        // Verify IncludesSdkLibrary tags.
        AppManifestIncludesSdkLibraryConfig includesSdkLibraryConfig =
                appManifestConfig.getIncludesSdkLibraryConfig();
        expect.withMessage("getIncludesSdkLibraryConfig()")
                .that(includesSdkLibraryConfig)
                .isNotNull();
        if (includesSdkLibraryConfig != null) {
            expect.withMessage("getIncludesSdkLibraryConfig().isEmpty()")
                    .that(appManifestConfig.getIncludesSdkLibraryConfig().isEmpty())
                    .isFalse();
            expect.withMessage("getIncludesSdkLibraryConfig().contains(1234)")
                    .that(appManifestConfig.getIncludesSdkLibraryConfig().contains("1234"))
                    .isTrue();
            expect.withMessage("getIncludesSdkLibraryConfig().contains(1234)")
                    .that(appManifestConfig.getIncludesSdkLibraryConfig().contains("4567"))
                    .isTrue();
            expect.withMessage("getIncludesSdkLibraryConfig().contains(1234)")
                    .that(appManifestConfig.getIncludesSdkLibraryConfig().contains("89"))
                    .isTrue();
            expect.withMessage("getIncludesSdkLibraryConfig().contains(1234)")
                    .that(appManifestConfig.getIncludesSdkLibraryConfig().contains("1234567"))
                    .isTrue();
        }

        // Verify Attribution tags.
        expect.withMessage("getAttributionConfig().getAllowAdPartnersToAccess()")
                .that(appManifestConfig.isAllowedAttributionAccess("1234"))
                .isTrue();
        expect.withMessage("isAllowedAttributionAccess()")
                .that(appManifestConfig.isAllowedAttributionAccess("108"))
                .isFalse();
        AppManifestAttributionConfig attributionConfig = appManifestConfig.getAttributionConfig();
        expect.withMessage("getAttributionConfig()").that(attributionConfig).isNotNull();
        if (attributionConfig != null) {
            expect.withMessage("getAttributionConfig().getAllowAllToAccess()")
                    .that(attributionConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getAttributionConfig().getAllowAdPartnersToAccess()")
                    .that(appManifestConfig.getAttributionConfig().getAllowAdPartnersToAccess())
                    .containsExactly("1234");
        }

        // Verify Custom Audience tags.
        expect.withMessage("isAllowedCustomAudiencesAccess()")
                .that(appManifestConfig.isAllowedCustomAudiencesAccess("108"))
                .isFalse();
        AppManifestCustomAudiencesConfig customAudiencesConfig =
                appManifestConfig.getCustomAudiencesConfig();
        expect.withMessage("getCustomAudiencesConfig()").that(customAudiencesConfig).isNotNull();
        if (customAudiencesConfig != null) {
            expect.withMessage("getCustomAudiencesConfig().getAllowAllToAccess()")
                    .that(customAudiencesConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getCustomAudiencesConfig().getAllowAdPartnersToAccess()")
                    .that(customAudiencesConfig.getAllowAdPartnersToAccess())
                    .hasSize(2);
            expect.withMessage("getCustomAudiencesConfig().getAllowAdPartnersToAccess()")
                    .that(customAudiencesConfig.getAllowAdPartnersToAccess())
                    .containsExactly("1234", "4567");
        }

        // Verify Topics tags.
        expect.withMessage("1234567()")
                .that(appManifestConfig.isAllowedTopicsAccess("1234567"))
                .isTrue();
        expect.withMessage("isAllowedTopicsAccess()")
                .that(appManifestConfig.isAllowedTopicsAccess("108"))
                .isFalse();
        AppManifestTopicsConfig topicsConfig = appManifestConfig.getTopicsConfig();
        expect.withMessage("getTopicsConfig()").that(topicsConfig).isNotNull();
        if (topicsConfig != null) {
            expect.withMessage("getTopicsConfig().getAllowAllToAccess()")
                    .that(topicsConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getTopicsConfig().getAllowAdPartnersToAccess()")
                    .that(topicsConfig.getAllowAdPartnersToAccess())
                    .contains("1234567");
        }

        // Verify AppId tags.
        expect.withMessage("isAllowedAdIdAccess()")
                .that(appManifestConfig.isAllowedAdIdAccess("42"))
                .isTrue();
        expect.withMessage("isAllowedAdIdAccess()")
                .that(appManifestConfig.isAllowedAdIdAccess("108"))
                .isFalse();
        AppManifestAdIdConfig adIdConfig = appManifestConfig.getAdIdConfig();
        expect.withMessage("getAdIdConfig()").that(adIdConfig).isNotNull();
        if (adIdConfig != null) {
            expect.withMessage("getAdIdConfig().getAllowAllToAccess()")
                    .that(adIdConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getAdIdConfig().getAllowAdPartnersToAccess()")
                    .that(adIdConfig.getAllowAdPartnersToAccess())
                    .containsExactly("4", "8", "15", "16", "23", "42");
        }

        // Verify AppSetId tags.
        expect.withMessage("isAllowedAppSetIdAccess()")
                .that(appManifestConfig.isAllowedAppSetIdAccess("42"))
                .isTrue();
        expect.withMessage("isAllowedAppSetIdAccess()")
                .that(appManifestConfig.isAllowedAppSetIdAccess("108"))
                .isFalse();
        AppManifestAppSetIdConfig appSetIdConfig = appManifestConfig.getAppSetIdConfig();
        expect.withMessage("getAppSetIdConfig()").that(appSetIdConfig).isNotNull();
        if (appSetIdConfig != null) {
            expect.withMessage("getAppSetIdConfig().getAllowAllToAccess()")
                    .that(appSetIdConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getAppSetIdConfig().getAllowAdPartnersToAccess()")
                    .that(appSetIdConfig.getAllowAdPartnersToAccess())
                    .containsExactly("4", "8", "15", "16", "23", "42");
        }
    }

    @Test
    public void testInvalidXml_missingSdkLibrary() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_missing_sdk_name);

        Exception e =
                assertThrows(
                        XmlParseException.class, () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e)
                .hasMessageThat()
                .isEqualTo("Sdk name not mentioned in <includes-sdk-library>");
    }

    @Test
    public void testInvalidXml_incorrectValues() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_incorrect_values);

        Exception e =
                assertThrows(
                        XmlParseException.class, () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e)
                .hasMessageThat()
                .isEqualTo("allowAll cannot be set to true when allowAdPartners is also set");
    }

    @Test
    public void testValidXml_disabledByDefault_missingAllTags() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_missing_tags);
        AppManifestConfig appManifestConfig = AppManifestConfigParser.getConfig(parser);
        assertWithMessage("manifest for ad_services_config_missing_tags")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ false);

        AppManifestAttributionConfig attributionConfig = appManifestConfig.getAttributionConfig();
        expect.withMessage("getAttributionConfig()").that(attributionConfig).isNull();
        expect.withMessage("isAllowedAttributionAccess()")
                .that(appManifestConfig.isAllowedAttributionAccess("not actually there"))
                .isFalse();

        AppManifestCustomAudiencesConfig customAudiencesConfig =
                appManifestConfig.getCustomAudiencesConfig();
        expect.withMessage("getCustomAudiencesConfig()").that(attributionConfig).isNull();
        expect.withMessage("isAllowedCustomAudiencesAccess()")
                .that(appManifestConfig.isAllowedCustomAudiencesAccess("not actually there"))
                .isFalse();

        AppManifestTopicsConfig topicsConfig = appManifestConfig.getTopicsConfig();
        expect.withMessage("getTopicsConfig()").that(topicsConfig).isNull();
        expect.withMessage("isAllowedTopicsAccess()")
                .that(appManifestConfig.isAllowedTopicsAccess("not actually there"))
                .isFalse();

        AppManifestAdIdConfig adIdConfig = appManifestConfig.getAdIdConfig();
        expect.withMessage("getAdIdConfig()").that(adIdConfig).isNull();
        expect.withMessage("isAllowedAdIdAccess()")
                .that(appManifestConfig.isAllowedAdIdAccess("not actually there"))
                .isFalse();

        AppManifestAppSetIdConfig appSetIdConfig = appManifestConfig.getAppSetIdConfig();
        expect.withMessage("getAppSetIdConfig()").that(appSetIdConfig).isNull();
        expect.withMessage("isAllowedAppSetIdAccess()")
                .that(appManifestConfig.isAllowedAppSetIdAccess("not actually there"))
                .isFalse();
    }

    @Test
    public void testValidXml_enabledByDefault_missingAllTags() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_missing_tags);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_missing_attribution")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsDefault(appManifestConfig);
        assertCustomAudiencesConfigIsDefault(appManifestConfig);
        assertTopicsConfigIsDefault(appManifestConfig);
        assertAdIdConfigIsDefault(appManifestConfig);
        assertAppSetIdConfigIsDefault(appManifestConfig);
    }

    @Test
    public void testValidXml_enabledByDefault_missingAttribution() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_all_false_missing_attribution);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_missing_attribution")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsDefault(appManifestConfig);
        assertCustomAudiencesConfigIsFalse(appManifestConfig);
        assertTopicsConfigIsFalse(appManifestConfig);
        assertAdIdConfigIsFalse(appManifestConfig);
        assertAppSetIdConfigIsFalse(appManifestConfig);
    }

    @Test
    public void testValidXml_enabledByDefault_missingCustomAudiences() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_all_false_missing_custom_audiences);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_missing_custom_audiences")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsFalse(appManifestConfig);
        assertCustomAudiencesConfigIsDefault(appManifestConfig);
        assertTopicsConfigIsFalse(appManifestConfig);
        assertAdIdConfigIsFalse(appManifestConfig);
        assertAppSetIdConfigIsFalse(appManifestConfig);
    }

    @Test
    public void testValidXml_enabledByDefault_missingTopics() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_all_false_missing_topics);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_missing_topics")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsFalse(appManifestConfig);
        assertCustomAudiencesConfigIsFalse(appManifestConfig);
        assertTopicsConfigIsDefault(appManifestConfig);
        assertAdIdConfigIsFalse(appManifestConfig);
        assertAppSetIdConfigIsFalse(appManifestConfig);
    }

    @Test
    public void testValidXml_enabledByDefault_missingAdId() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_all_false_missing_adid);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_missing_adid")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsFalse(appManifestConfig);
        assertCustomAudiencesConfigIsFalse(appManifestConfig);
        assertTopicsConfigIsFalse(appManifestConfig);
        assertAdIdConfigIsDefault(appManifestConfig);
        assertAppSetIdConfigIsFalse(appManifestConfig);
    }

    @Test
    public void testValidXml_enabledByDefault_missingAppsetId() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_all_false_missing_appsetid);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_missing_appsetid")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsFalse(appManifestConfig);
        assertCustomAudiencesConfigIsFalse(appManifestConfig);
        assertTopicsConfigIsFalse(appManifestConfig);
        assertAdIdConfigIsFalse(appManifestConfig);
        assertAppSetIdConfigIsDefault(appManifestConfig);
    }

    @Test
    public void testValidXml_enabledByDefault_missingSdkLibraries() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_all_false_missing_sdk_libraries);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_missing_sdk_libraries")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsFalse(appManifestConfig);
        assertCustomAudiencesConfigIsFalse(appManifestConfig);
        assertTopicsConfigIsFalse(appManifestConfig);
        assertAdIdConfigIsFalse(appManifestConfig);
        assertAppSetIdConfigIsFalse(appManifestConfig);
    }

    @Test
    public void testValidXml_enabledByDefault_withSdkLibraries() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        // This XML contains only 42 and 108
                        .getXml(R.xml.ad_services_config_all_false_with_sdk_libraries);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_with_sdk_libraries")
                .that(appManifestConfig)
                .isNotNull();

        AppManifestIncludesSdkLibraryConfig sdkLibrary =
                appManifestConfig.getIncludesSdkLibraryConfig();
        expect.withMessage("getIncludesSdkLibraryConfig()").that(sdkLibrary).isNotNull();
        expect.withMessage("getIncludesSdkLibraryConfig().isEmpty()")
                .that(sdkLibrary.isEmpty())
                .isFalse();
        expect.withMessage("getIncludesSdkLibraryConfig().contains(42)")
                .that(sdkLibrary.contains("42"))
                .isTrue();
        expect.withMessage("getIncludesSdkLibraryConfig().contains(108)")
                .that(sdkLibrary.contains("108"))
                .isTrue();
        expect.withMessage("getIncludesSdkLibraryConfig().contains(4815162342)")
                .that(sdkLibrary.contains("4815162342"))
                .isFalse();

        assertAttributionConfigIsFalse(appManifestConfig);
        assertCustomAudiencesConfigIsFalse(appManifestConfig);
        assertTopicsConfigIsFalse(appManifestConfig);
        assertAdIdConfigIsFalse(appManifestConfig);
        assertAppSetIdConfigIsFalse(appManifestConfig);
    }

    @Test
    public void testValidXml_missingValues() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_missing_values);
        AppManifestConfig appManifestConfig = AppManifestConfigParser.getConfig(parser);
        assertWithMessage("manifest for ad_services_config_missing_values")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ false);
        assertAttributionConfigIsFalse(appManifestConfig);
        assertCustomAudiencesConfigIsFalse(appManifestConfig);
        assertTopicsConfigIsFalse(appManifestConfig);
        assertAdIdConfigIsFalse(appManifestConfig);
        assertAppSetIdConfigIsFalse(appManifestConfig);
    }

    @Test
    public void testInvalidXml_repeatTags() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_repeat_tags);

        Exception e =
                assertThrows(
                        XmlParseException.class, () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e).hasMessageThat().isEqualTo("Tag custom-audiences appears more than once");
    }

    @Test
    public void testInvalidXml_incorrectStartTag() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_incorrect_start_tag);

        Exception e =
                assertThrows(
                        XmlPullParserException.class,
                        () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e).hasMessageThat().isEqualTo("expected START_TAGBinary XML file line #17");
    }

    @Test
    public void testInvalidXml_incorrectTag() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_incorrect_tag);

        Exception e =
                assertThrows(
                        XmlParseException.class, () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e)
                .hasMessageThat()
                .isEqualTo("Unknown tag: foobar [Tags and attributes are case sensitive]");
    }

    @Test
    public void testInvalidXml_incorrectAttr() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_incorrect_attr);

        Exception e =
                assertThrows(
                        XmlParseException.class, () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e)
                .hasMessageThat()
                .isEqualTo("Unknown attribute: foobar [Tags and attributes are case sensitive]");
    }

    private void assertSdkLibraryConfigIsEmpty(
            AppManifestConfig appManifestConfig, boolean containsByDefault) {
        AppManifestIncludesSdkLibraryConfig sdkLibrary =
                appManifestConfig.getIncludesSdkLibraryConfig();
        expect.withMessage("getIncludesSdkLibraryConfig()").that(sdkLibrary).isNotNull();
        expect.withMessage("getIncludesSdkLibraryConfig().isEmpty()")
                .that(sdkLibrary.isEmpty())
                .isTrue();
        if (containsByDefault) {
            expect.withMessage("getIncludesSdkLibraryConfig().contains(42)")
                    .that(sdkLibrary.contains("42"))
                    .isTrue();
        } else {
            expect.withMessage("getIncludesSdkLibraryConfig().contains(42)")
                    .that(sdkLibrary.contains("42"))
                    .isFalse();
        }
    }

    private void assertAttributionConfigIsDefault(AppManifestConfig appManifestConfig) {
        AppManifestAttributionConfig attributionConfig = appManifestConfig.getAttributionConfig();
        assertApiConfigIsDefault("getAttributionConfig()", attributionConfig);
        expect.withMessage("isAllowedAttributionAccess()")
                .that(appManifestConfig.isAllowedAttributionAccess("not actually there"))
                .isTrue();
    }

    private void assertAttributionConfigIsFalse(AppManifestConfig appManifestConfig) {
        AppManifestAttributionConfig attributionConfig = appManifestConfig.getAttributionConfig();
        assertApiConfigIsFalse("getAttributionConfig()", attributionConfig);
        expect.withMessage("isAllowedAttributionAccess()")
                .that(appManifestConfig.isAllowedAttributionAccess("not actually there"))
                .isFalse();
    }

    private void assertCustomAudiencesConfigIsDefault(AppManifestConfig appManifestConfig) {
        AppManifestCustomAudiencesConfig customAudiencesConfig =
                appManifestConfig.getCustomAudiencesConfig();
        assertApiConfigIsDefault("getCustomAudiencesConfig()", customAudiencesConfig);
        expect.withMessage("isAllowedCustomAudiencesAccess()")
                .that(appManifestConfig.isAllowedCustomAudiencesAccess("not actually there"))
                .isTrue();
    }

    private void assertCustomAudiencesConfigIsFalse(AppManifestConfig appManifestConfig) {
        AppManifestCustomAudiencesConfig customAudiencesConfig =
                appManifestConfig.getCustomAudiencesConfig();
        assertApiConfigIsFalse("getCustomAudiencesConfig()", customAudiencesConfig);
        expect.withMessage("isAllowedCustomAudiencesAccess()")
                .that(appManifestConfig.isAllowedCustomAudiencesAccess("not actually there"))
                .isFalse();
    }

    private void assertTopicsConfigIsDefault(AppManifestConfig appManifestConfig) {
        AppManifestTopicsConfig topicsConfig = appManifestConfig.getTopicsConfig();
        assertApiConfigIsDefault("getTopicsConfig()", topicsConfig);
        expect.withMessage("isAllowedTopicsAccess()")
                .that(appManifestConfig.isAllowedTopicsAccess("not actually there"))
                .isTrue();
    }

    private void assertTopicsConfigIsFalse(AppManifestConfig appManifestConfig) {
        AppManifestTopicsConfig topicsConfig = appManifestConfig.getTopicsConfig();
        assertApiConfigIsFalse("getTopicsConfig()", topicsConfig);
        expect.withMessage("isAllowedTopicsAccess()")
                .that(appManifestConfig.isAllowedTopicsAccess("not actually there"))
                .isFalse();
    }

    private void assertAdIdConfigIsDefault(AppManifestConfig appManifestConfig) {
        AppManifestAdIdConfig adIdConfig = appManifestConfig.getAdIdConfig();
        assertApiConfigIsDefault("getAdIdConfig()", adIdConfig);
        expect.withMessage("isAllowedAdIdAccess()")
                .that(appManifestConfig.isAllowedAdIdAccess("not actually there"))
                .isTrue();
    }

    private void assertAdIdConfigIsFalse(AppManifestConfig appManifestConfig) {
        AppManifestAdIdConfig adIdConfig = appManifestConfig.getAdIdConfig();
        assertApiConfigIsFalse("getAdIdConfig()", adIdConfig);
        expect.withMessage("isAllowedAdIdAccess()")
                .that(appManifestConfig.isAllowedAdIdAccess("not actually there"))
                .isFalse();
    }

    private void assertAppSetIdConfigIsDefault(AppManifestConfig appManifestConfig) {
        AppManifestAppSetIdConfig appSetIdConfig = appManifestConfig.getAppSetIdConfig();
        assertApiConfigIsDefault("getAppSetIdConfig()", appSetIdConfig);
        expect.withMessage("isAllowedAppSetIdAccess()")
                .that(appManifestConfig.isAllowedAppSetIdAccess("not actually there"))
                .isTrue();
    }

    private void assertAppSetIdConfigIsFalse(AppManifestConfig appManifestConfig) {
        AppManifestAppSetIdConfig appSetIdConfig = appManifestConfig.getAppSetIdConfig();
        assertApiConfigIsFalse("getAppSetIdConfig()", appSetIdConfig);
        expect.withMessage("isAllowedAppSetIdAccess()")
                .that(appManifestConfig.isAllowedAppSetIdAccess("not actually there"))
                .isFalse();
    }

    private void assertApiConfigIsDefault(String name, AppManifestApiConfig config) {
        expect.withMessage(name).that(config).isNotNull();
        if (config != null) {
            expect.withMessage("%s.getAllowAllToAccess()", name)
                    .that(config.getAllowAllToAccess())
                    .isTrue();
            expect.withMessage("%s.getAllowAdPartnersToAccess()", name)
                    .that(config.getAllowAdPartnersToAccess())
                    .isEmpty();
        }
    }

    private void assertApiConfigIsFalse(String name, AppManifestApiConfig config) {
        expect.withMessage(name).that(config).isNotNull();
        if (config != null) {
            expect.withMessage("%s.getAllowAllToAccess()", name)
                    .that(config.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("%s.getAllowAdPartnersToAccess()", name)
                    .that(config.getAllowAdPartnersToAccess())
                    .isEmpty();
        }
    }
}
