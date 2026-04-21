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

package com.android.adservices.service.adselection.signature;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;

import android.adservices.common.AdTechIdentifier;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.adservices.service.enrollment.EnrollmentData;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

public class ProtectedAudienceSignatureManagerTest {
    private Context mContext = ApplicationProvider.getApplicationContext();
    @Mock private EnrollmentDao mEnrollmentDao;
    @Mock private EncryptionKeyDao mEncryptionKeyDao;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testFetchKeys_validAdTech_success() {
        AdTechIdentifier adTech = AdTechIdentifier.fromString("example.com");
        String publicKey = "test-key";
        String enrollmentId = "enrollment1";

        doReturn(new EnrollmentData.Builder().setEnrollmentId(enrollmentId).build())
                .when(mEnrollmentDao)
                .getEnrollmentDataForFledgeByAdTechIdentifier(adTech);
        doReturn(Collections.singletonList(new EncryptionKey.Builder().setBody(publicKey).build()))
                .when(mEncryptionKeyDao)
                .getEncryptionKeyFromEnrollmentIdAndKeyType(
                        enrollmentId, EncryptionKey.KeyType.SIGNING);

        ProtectedAudienceSignatureManager signatureManager =
                new ProtectedAudienceSignatureManager(mContext, mEnrollmentDao, mEncryptionKeyDao);

        List<String> signingKeys = signatureManager.fetchPublicKeyForAdTech(adTech);

        assertThat(signingKeys).isEqualTo(Collections.singletonList(publicKey));
    }

    @Test
    public void testFetchKeys_validAdTechMultipleKeysSortedProperly_success() {
        AdTechIdentifier adTech = AdTechIdentifier.fromString("example.com");
        String enrollmentId = "enrollment1";
        EnrollmentData enrollment =
                new EnrollmentData.Builder().setEnrollmentId(enrollmentId).build();

        String publicKey1 = "test-key1";
        String publicKey2 = "test-key2";
        long expiration1 = 0L;
        long expiration2 = 1L;
        EncryptionKey encKey1 =
                new EncryptionKey.Builder().setBody(publicKey1).setExpiration(expiration1).build();
        EncryptionKey encKey2 =
                new EncryptionKey.Builder().setBody(publicKey2).setExpiration(expiration2).build();
        List<EncryptionKey> encKeysToPersistInReverseOrder = List.of(encKey2, encKey1);

        doReturn(enrollment)
                .when(mEnrollmentDao)
                .getEnrollmentDataForFledgeByAdTechIdentifier(adTech);
        doReturn(encKeysToPersistInReverseOrder)
                .when(mEncryptionKeyDao)
                .getEncryptionKeyFromEnrollmentIdAndKeyType(
                        enrollmentId, EncryptionKey.KeyType.SIGNING);

        ProtectedAudienceSignatureManager signatureManager =
                new ProtectedAudienceSignatureManager(mContext, mEnrollmentDao, mEncryptionKeyDao);

        List<String> signingKeys = signatureManager.fetchPublicKeyForAdTech(adTech);

        assertThat(signingKeys.size()).isEqualTo(2);
        assertThat(signingKeys.get(0)).isEqualTo(publicKey1);
        assertThat(signingKeys.get(1)).isEqualTo(publicKey2);
    }

    @Test
    public void testFetchKeys_notEnrolledAdTech_returnsEmptyList() {
        AdTechIdentifier adTech = AdTechIdentifier.fromString("example.com");
        doReturn(null).when(mEnrollmentDao).getEnrollmentDataForFledgeByAdTechIdentifier(adTech);

        ProtectedAudienceSignatureManager signatureManager =
                new ProtectedAudienceSignatureManager(mContext, mEnrollmentDao, mEncryptionKeyDao);

        List<String> signingKeys = signatureManager.fetchPublicKeyForAdTech(adTech);

        assertThat(signingKeys).isEqualTo(Collections.emptyList());
    }

    @Test
    public void testFetchKeys_enrolledAdTechWithNullId_returnsEmptyList() {
        AdTechIdentifier adTech = AdTechIdentifier.fromString("example.com");
        doReturn(new EnrollmentData.Builder().build())
                .when(mEnrollmentDao)
                .getEnrollmentDataForFledgeByAdTechIdentifier(adTech);

        ProtectedAudienceSignatureManager signatureManager =
                new ProtectedAudienceSignatureManager(mContext, mEnrollmentDao, mEncryptionKeyDao);

        List<String> signingKeys = signatureManager.fetchPublicKeyForAdTech(adTech);

        assertThat(signingKeys).isEqualTo(Collections.emptyList());
    }
}
