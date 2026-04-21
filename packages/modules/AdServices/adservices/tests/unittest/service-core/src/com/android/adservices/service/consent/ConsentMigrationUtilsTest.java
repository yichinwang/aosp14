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

package com.android.adservices.service.consent;

import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_FALSE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_TRUE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_MANUAL_INTERACTIONS_RECORDED;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_UNKNOWN;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.extdata.AdServicesExtDataParams;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.appsearch.AppSearchConsentManager;
import com.android.adservices.service.extdata.AdServicesExtDataStorageServiceManager;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

public class ConsentMigrationUtilsTest {
    private static final AdServicesExtDataParams TEST_PARAMS_WITH_ALL_DATA =
            new AdServicesExtDataParams.Builder()
                    .setNotificationDisplayed(BOOLEAN_TRUE)
                    .setMsmtConsent(BOOLEAN_FALSE)
                    .setIsU18Account(BOOLEAN_TRUE)
                    .setIsAdultAccount(BOOLEAN_FALSE)
                    .setManualInteractionWithConsentStatus(STATE_MANUAL_INTERACTIONS_RECORDED)
                    .build();

    private static final AdServicesExtDataParams TEST_PARAMS_WITH_PARTIAL_DATA =
            new AdServicesExtDataParams.Builder()
                    .setNotificationDisplayed(BOOLEAN_TRUE)
                    .setMsmtConsent(BOOLEAN_TRUE)
                    .setIsU18Account(BOOLEAN_UNKNOWN)
                    .setIsAdultAccount(BOOLEAN_UNKNOWN)
                    .setManualInteractionWithConsentStatus(STATE_UNKNOWN)
                    .build();

    @Rule
    public final AdServicesExtendedMockitoRule mExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this).spyStatic(SdkLevel.class).build();

    @Spy private final Context mContextSpy = ApplicationProvider.getApplicationContext();
    @Mock private BooleanFileDatastore mDatastoreMock;
    @Mock private AppSearchConsentManager mAppSearchConsentManagerMock;
    @Mock private AdServicesExtDataStorageServiceManager mAdServicesExtDataManagerMock;
    @Mock private SharedPreferences mSharedPreferencesMock;
    @Mock private SharedPreferences.Editor mSharedPreferencesEditorMock;
    @Mock private AdServicesExtDataParams mAdServicesExtDataParamsMock;

    @Test
    public void testHandleConsentMigrationToAppSearchIfNeeded_onR_skipsMigration() {
        doReturn(false).when(SdkLevel::isAtLeastT);
        doReturn(false).when(SdkLevel::isAtLeastS);

        ConsentMigrationUtils.handleConsentMigrationToAppSearchIfNeeded(
                mContextSpy,
                mDatastoreMock,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock);

        verifyZeroInteractions(mAppSearchConsentManagerMock);
        verifyZeroInteractions(mAdServicesExtDataManagerMock);
    }

    @Test
    public void testHandleConsentMigrationToAppSearchIfNeeded_onTPlus_skipsMigration() {
        doReturn(true).when(SdkLevel::isAtLeastT);

        ConsentMigrationUtils.handleConsentMigrationToAppSearchIfNeeded(
                mContextSpy,
                mDatastoreMock,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock);

        verifyZeroInteractions(mAppSearchConsentManagerMock);
        verifyZeroInteractions(mAdServicesExtDataManagerMock);
    }

    @Test
    public void
            testHandleConsentMigrationToAppSearchIfNeeded_onSWithNullAdExtManager_skipsMigration() {
        mockSDevice();

        ConsentMigrationUtils.handleConsentMigrationToAppSearchIfNeeded(
                mContextSpy, mDatastoreMock, mAppSearchConsentManagerMock, null);

        verifyZeroInteractions(mAdServicesExtDataManagerMock);
        verifyZeroInteractions(mAppSearchConsentManagerMock);
    }

    @Test
    public void
            testHandleConsentMigrationToAppSearchIfNeeded_onSWithPastMigrationDone_skipsMigration() {
        mockSDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);
        when(mSharedPreferencesMock.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED_TO_APP_SEARCH, false))
                .thenReturn(true);

        ConsentMigrationUtils.handleConsentMigrationToAppSearchIfNeeded(
                mContextSpy,
                mDatastoreMock,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock);

        verifyZeroInteractions(mAppSearchConsentManagerMock);
        verifyZeroInteractions(mAdServicesExtDataManagerMock);
    }

    @Test
    public void testHandleConsentMigrationToAppSearchIfNeeded_onSWithNotifOnS_skipsMigration() {
        mockSDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);
        when(mSharedPreferencesMock.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED_TO_APP_SEARCH, false))
                .thenReturn(false);
        when(mAppSearchConsentManagerMock.wasU18NotificationDisplayed()).thenReturn(true);

        ConsentMigrationUtils.handleConsentMigrationToAppSearchIfNeeded(
                mContextSpy,
                mDatastoreMock,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock);

        verify(mAppSearchConsentManagerMock).wasU18NotificationDisplayed();
        verifyNoMoreInteractions(mAppSearchConsentManagerMock);

        verifyZeroInteractions(mAdServicesExtDataManagerMock);
    }

    @Test
    public void testHandleConsentMigrationToAppSearchIfNeeded_onSWithNoNotifOnR_skipsMigration() {
        mockSDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);
        when(mSharedPreferencesMock.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED_TO_APP_SEARCH, false))
                .thenReturn(false);
        mockNoNotifOnS();
        when(mAdServicesExtDataManagerMock.getAdServicesExtData())
                .thenReturn(mAdServicesExtDataParamsMock);
        when(mAdServicesExtDataParamsMock.getIsNotificationDisplayed()).thenReturn(BOOLEAN_FALSE);

        ConsentMigrationUtils.handleConsentMigrationToAppSearchIfNeeded(
                mContextSpy,
                mDatastoreMock,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock);

        verify(mAppSearchConsentManagerMock).wasU18NotificationDisplayed();
        verify(mAppSearchConsentManagerMock).wasGaUxNotificationDisplayed();
        verify(mAppSearchConsentManagerMock).wasNotificationDisplayed();
        verifyNoMoreInteractions(mAppSearchConsentManagerMock);

        verify(mAdServicesExtDataManagerMock).getAdServicesExtData();
        verifyNoMoreInteractions(mAdServicesExtDataManagerMock);
    }

    @Test
    public void
            testHandleConsentMigrationToAppSearchIfNeeded_onSWithMigrationEligibleWithFullData_migrationSuccessWithAdExtDataCleared() {
        mockSDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);
        when(mSharedPreferencesMock.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED_TO_APP_SEARCH, false))
                .thenReturn(false);
        mockNoNotifOnS();
        when(mAdServicesExtDataManagerMock.getAdServicesExtData())
                .thenReturn(TEST_PARAMS_WITH_ALL_DATA);
        when(mDatastoreMock.get(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT)).thenReturn(true);
        when(mSharedPreferencesMock.edit()).thenReturn(mSharedPreferencesEditorMock);
        when(mSharedPreferencesEditorMock.commit()).thenReturn(true);

        ConsentMigrationUtils.handleConsentMigrationToAppSearchIfNeeded(
                mContextSpy,
                mDatastoreMock,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock);

        verify(mAppSearchConsentManagerMock).wasU18NotificationDisplayed();
        verify(mAppSearchConsentManagerMock).wasGaUxNotificationDisplayed();
        verify(mAppSearchConsentManagerMock).wasNotificationDisplayed();
        verify(mAppSearchConsentManagerMock)
                .setConsent(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT, true);
        verify(mAppSearchConsentManagerMock)
                .setConsent(
                        AdServicesApiType.MEASUREMENTS.toPpApiDatastoreKey(),
                        TEST_PARAMS_WITH_ALL_DATA.getIsMeasurementConsented() == BOOLEAN_TRUE);
        verify(mAppSearchConsentManagerMock)
                .setU18NotificationDisplayed(
                        TEST_PARAMS_WITH_ALL_DATA.getIsNotificationDisplayed() == BOOLEAN_TRUE);
        verify(mAppSearchConsentManagerMock)
                .recordUserManualInteractionWithConsent(
                        TEST_PARAMS_WITH_ALL_DATA.getManualInteractionWithConsentStatus());
        verify(mAppSearchConsentManagerMock)
                .setU18Account(TEST_PARAMS_WITH_ALL_DATA.getIsU18Account() == BOOLEAN_TRUE);
        verify(mAppSearchConsentManagerMock)
                .setAdultAccount(TEST_PARAMS_WITH_ALL_DATA.getIsAdultAccount() == BOOLEAN_TRUE);
        verifyNoMoreInteractions(mAppSearchConsentManagerMock);

        verify(mAdServicesExtDataManagerMock).getAdServicesExtData();
        verify(mAdServicesExtDataManagerMock).clearDataOnOtaAsync();
        verifyNoMoreInteractions(mAdServicesExtDataManagerMock);
    }

    @Test
    public void
            testHandleConsentMigrationToAppSearchIfNeeded_onSWithMigrationEligibleWithPartialDataWithFailedSharedPrefUpdate_migrationSuccessWithAdExtDataCleared() {
        mockSDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);
        when(mSharedPreferencesMock.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED_TO_APP_SEARCH, false))
                .thenReturn(false);
        mockNoNotifOnS();
        when(mAdServicesExtDataManagerMock.getAdServicesExtData())
                .thenReturn(TEST_PARAMS_WITH_PARTIAL_DATA);
        when(mDatastoreMock.get(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT)).thenReturn(null);
        when(mSharedPreferencesMock.edit()).thenReturn(mSharedPreferencesEditorMock);
        when(mSharedPreferencesEditorMock.commit()).thenReturn(false);

        ConsentMigrationUtils.handleConsentMigrationToAppSearchIfNeeded(
                mContextSpy,
                mDatastoreMock,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock);

        verify(mAppSearchConsentManagerMock).wasU18NotificationDisplayed();
        verify(mAppSearchConsentManagerMock).wasGaUxNotificationDisplayed();
        verify(mAppSearchConsentManagerMock).wasNotificationDisplayed();
        verify(mAppSearchConsentManagerMock, never())
                .setConsent(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT, false);
        verify(mAppSearchConsentManagerMock)
                .setConsent(
                        AdServicesApiType.MEASUREMENTS.toPpApiDatastoreKey(),
                        TEST_PARAMS_WITH_PARTIAL_DATA.getIsMeasurementConsented() == BOOLEAN_TRUE);
        verify(mAppSearchConsentManagerMock)
                .setU18NotificationDisplayed(
                        TEST_PARAMS_WITH_PARTIAL_DATA.getIsNotificationDisplayed() == BOOLEAN_TRUE);
        verify(mAppSearchConsentManagerMock, never())
                .recordUserManualInteractionWithConsent(
                        TEST_PARAMS_WITH_PARTIAL_DATA.getManualInteractionWithConsentStatus());
        verify(mAppSearchConsentManagerMock, never())
                .setU18Account(TEST_PARAMS_WITH_PARTIAL_DATA.getIsU18Account() == BOOLEAN_TRUE);
        verify(mAppSearchConsentManagerMock, never())
                .setAdultAccount(TEST_PARAMS_WITH_PARTIAL_DATA.getIsAdultAccount() == BOOLEAN_TRUE);

        verifyNoMoreInteractions(mAppSearchConsentManagerMock);

        verify(mAdServicesExtDataManagerMock).getAdServicesExtData();
        verify(mAdServicesExtDataManagerMock).clearDataOnOtaAsync();
        verifyNoMoreInteractions(mAdServicesExtDataManagerMock);
    }

    private void mockNoNotifOnS() {
        when(mAppSearchConsentManagerMock.wasU18NotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentManagerMock.wasGaUxNotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentManagerMock.wasNotificationDisplayed()).thenReturn(false);
    }

    private void mockSDevice() {
        doReturn(false).when(SdkLevel::isAtLeastT);
        doReturn(true).when(SdkLevel::isAtLeastS);
    }
}
