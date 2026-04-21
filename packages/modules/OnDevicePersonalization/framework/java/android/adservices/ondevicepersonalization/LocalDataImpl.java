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

package android.adservices.ondevicepersonalization;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Bundle;
import android.os.RemoteException;


import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** @hide */
public class LocalDataImpl implements MutableKeyValueStore {
    private static final String TAG = "LocalDataImpl";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    @NonNull
    IDataAccessService mDataAccessService;

    /** @hide */
    public LocalDataImpl(@NonNull IDataAccessService binder) {
        mDataAccessService = Objects.requireNonNull(binder);
    }

    @Override @Nullable
    public byte[] get(@NonNull String key) {
        Objects.requireNonNull(key);
        Bundle params = new Bundle();
        params.putStringArray(Constants.EXTRA_LOOKUP_KEYS, new String[]{key});
        return handleLookupRequest(Constants.DATA_ACCESS_OP_LOCAL_DATA_LOOKUP, key, params);
    }

    @Override @Nullable
    public byte[] put(@NonNull String key, byte[] value) {
        Objects.requireNonNull(key);
        Bundle params = new Bundle();
        params.putStringArray(Constants.EXTRA_LOOKUP_KEYS, new String[]{key});
        params.putByteArray(Constants.EXTRA_VALUE, value);
        return handleLookupRequest(Constants.DATA_ACCESS_OP_LOCAL_DATA_PUT, key, params);
    }

    @Override @Nullable
    public byte[] remove(@NonNull String key) {
        Objects.requireNonNull(key);
        Bundle params = new Bundle();
        params.putStringArray(Constants.EXTRA_LOOKUP_KEYS, new String[]{key});
        return handleLookupRequest(Constants.DATA_ACCESS_OP_LOCAL_DATA_REMOVE, key, params);
    }

    private byte[] handleLookupRequest(int op, String key, Bundle params) {
        Bundle result = handleAsyncRequest(op, params);
        HashMap<String, byte[]> data = result.getSerializable(
                Constants.EXTRA_RESULT, HashMap.class);
        if (null == data) {
            sLogger.e(TAG + ": No EXTRA_RESULT was present in bundle");
            throw new IllegalStateException("Bundle missing EXTRA_RESULT.");
        }
        return data.get(key);
    }

    @Override @NonNull
    public Set<String> keySet() {
        Bundle result = handleAsyncRequest(Constants.DATA_ACCESS_OP_LOCAL_DATA_KEYSET,
                Bundle.EMPTY);
        HashSet<String> resultSet =
                result.getSerializable(Constants.EXTRA_RESULT, HashSet.class);
        if (null == resultSet) {
            sLogger.e(TAG + ": No EXTRA_RESULT was present in bundle");
            throw new IllegalStateException("Bundle missing EXTRA_RESULT.");
        }
        return resultSet;
    }

    private Bundle handleAsyncRequest(int op, Bundle params) {
        try {
            BlockingQueue<Bundle> asyncResult = new ArrayBlockingQueue<>(1);
            mDataAccessService.onRequest(
                    op,
                    params,
                    new IDataAccessServiceCallback.Stub() {
                        @Override
                        public void onSuccess(@NonNull Bundle result) {
                            if (result != null) {
                                asyncResult.add(result);
                            } else {
                                asyncResult.add(Bundle.EMPTY);
                            }
                        }

                        @Override
                        public void onError(int errorCode) {
                            asyncResult.add(Bundle.EMPTY);
                        }
                    });
            return asyncResult.take();
        } catch (InterruptedException | RemoteException e) {
            sLogger.e(TAG + ": Failed to retrieve result from localData", e);
            throw new IllegalStateException(e);
        }
    }
}
