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

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.annotation.Nullable;

/**
 * Represents a request containing the information to get ad selection data.
 *
 * <p>Instances of this class are created by SDKs to be provided as arguments to the {@link
 * AdSelectionManager#getAdSelectionData} methods in {@link AdSelectionManager}.
 */
public final class GetAdSelectionDataRequest {
    @Nullable private final AdTechIdentifier mSeller;

    private GetAdSelectionDataRequest(@Nullable AdTechIdentifier seller) {
        this.mSeller = seller;
    }

    /**
     * @return a AdTechIdentifier of the seller, for example "www.example-ssp.com"
     */
    @Nullable
    public AdTechIdentifier getSeller() {
        return mSeller;
    }

    /**
     * Builder for {@link GetAdSelectionDataRequest} objects.
     */
    public static final class Builder {
        @Nullable private AdTechIdentifier mSeller;

        public Builder() {}

        /** Sets the seller {@link AdTechIdentifier}. */
        @NonNull
        public GetAdSelectionDataRequest.Builder setSeller(@Nullable AdTechIdentifier seller) {
            this.mSeller = seller;
            return this;
        }

        /**
         * Builds a {@link GetAdSelectionDataRequest} instance.
         */
        @NonNull
        public GetAdSelectionDataRequest build() {
            return new GetAdSelectionDataRequest(mSeller);
        }
    }
}
