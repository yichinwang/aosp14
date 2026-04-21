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

package android.adservices.adselection;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdTechIdentifier;

import org.junit.Test;

public class PersistAdSelectionResultRequestTest {
    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER;
    private static final long AD_SELECTION_ID = 123456789L;
    private static final byte[] AD_SELECTION_RESULT = new byte[10];

    @Test
    public void testPersistAdSelectionResultRequest_validInput_success() {
        PersistAdSelectionResultRequest request =
                new PersistAdSelectionResultRequest.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(AD_SELECTION_RESULT)
                        .build();

        assertThat(request.getSeller()).isEqualTo(SELLER);
        assertThat(request.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        assertThat(request.getAdSelectionResult()).isEqualTo(AD_SELECTION_RESULT);
    }
}
