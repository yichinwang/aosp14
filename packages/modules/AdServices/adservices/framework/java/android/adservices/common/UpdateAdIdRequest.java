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

package android.adservices.common;

import android.adservices.FlagsConstants;
import android.adservices.adid.AdId;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * The request sent from the AdIdProvider to update the AdId in Adservices, when the device updates
 * the AdId.
 *
 * @hide
 */
// TODO(b/300445889): Consider using codegen for Parcelable.
@SystemApi
@FlaggedApi("ad_id_cache_enabled")
public final class UpdateAdIdRequest implements Parcelable {
    private static final String KEY_AD_ID_CACHE_ENABLED = FlagsConstants.KEY_AD_ID_CACHE_ENABLED;
    private final String mAdId;
    private final boolean mLimitAdTrackingEnabled;

    private UpdateAdIdRequest(String adId, boolean isLimitAdTrackingEnabled) {
        mAdId = Objects.requireNonNull(adId);
        mLimitAdTrackingEnabled = isLimitAdTrackingEnabled;
    }

    private UpdateAdIdRequest(Parcel in) {
        this(in.readString(), in.readBoolean());
    }

    @FlaggedApi(KEY_AD_ID_CACHE_ENABLED)
    @NonNull
    public static final Creator<UpdateAdIdRequest> CREATOR =
            new Parcelable.Creator<>() {
                @Override
                public UpdateAdIdRequest createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);
                    return new UpdateAdIdRequest(in);
                }

                @Override
                public UpdateAdIdRequest[] newArray(int size) {
                    return new UpdateAdIdRequest[size];
                }
            };

    @FlaggedApi(KEY_AD_ID_CACHE_ENABLED)
    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @FlaggedApi(KEY_AD_ID_CACHE_ENABLED)
    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        Objects.requireNonNull(out);

        out.writeString(mAdId);
        out.writeBoolean(mLimitAdTrackingEnabled);
    }

    /** Returns the advertising ID associated with this result. */
    @FlaggedApi(KEY_AD_ID_CACHE_ENABLED)
    @NonNull
    public String getAdId() {
        return mAdId;
    }

    /**
     * Returns the Limited Ad Tracking field associated with this result.
     *
     * <p>When Limited Ad Tracking is enabled, it implies the user opts out the usage of {@link
     * AdId}. {@link AdId#ZERO_OUT} will be assigned to the device.
     */
    @FlaggedApi(KEY_AD_ID_CACHE_ENABLED)
    public boolean isLimitAdTrackingEnabled() {
        return mLimitAdTrackingEnabled;
    }

    // TODO(b/302682607): Investigate encoding AdId in logcat for related AdId classes.
    @Override
    public String toString() {
        return "UpdateAdIdRequest{"
                + "mAdId="
                + mAdId
                + ", mLimitAdTrackingEnabled="
                + mLimitAdTrackingEnabled
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof UpdateAdIdRequest)) {
            return false;
        }

        UpdateAdIdRequest that = (UpdateAdIdRequest) o;

        return Objects.equals(mAdId, that.mAdId)
                && (mLimitAdTrackingEnabled == that.mLimitAdTrackingEnabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAdId, mLimitAdTrackingEnabled);
    }

    /** Builder for {@link UpdateAdIdRequest} objects. */
    @FlaggedApi(KEY_AD_ID_CACHE_ENABLED)
    public static final class Builder {
        private final String mAdId;
        private boolean mLimitAdTrackingEnabled;

        @FlaggedApi(KEY_AD_ID_CACHE_ENABLED)
        public Builder(@NonNull String adId) {
            mAdId = Objects.requireNonNull(adId);
        }

        /** Sets the Limited AdTracking enabled field. */
        @FlaggedApi(KEY_AD_ID_CACHE_ENABLED)
        @NonNull
        public UpdateAdIdRequest.Builder setLimitAdTrackingEnabled(
                boolean isLimitAdTrackingEnabled) {
            mLimitAdTrackingEnabled = isLimitAdTrackingEnabled;
            return this;
        }

        /** Builds a {@link UpdateAdIdRequest} instance. */
        @FlaggedApi(KEY_AD_ID_CACHE_ENABLED)
        @NonNull
        public UpdateAdIdRequest build() {
            return new UpdateAdIdRequest(mAdId, mLimitAdTrackingEnabled);
        }
    }
}
