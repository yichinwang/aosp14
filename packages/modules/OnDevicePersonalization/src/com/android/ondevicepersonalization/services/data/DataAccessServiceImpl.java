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

package com.android.ondevicepersonalization.services.data;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.EventLogRecord;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.RemoteException;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.internal.util.OdpParceledListSlice;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.events.EventUrlHelper;
import com.android.ondevicepersonalization.services.data.events.EventUrlPayload;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.JoinedEvent;
import com.android.ondevicepersonalization.services.data.events.Query;
import com.android.ondevicepersonalization.services.data.vendor.LocalData;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationLocalDataDao;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.util.OnDevicePersonalizationFlatbufferUtils;
import com.android.ondevicepersonalization.services.util.PackageUtils;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * A class that exports methods that plugin code in the isolated process
 * can use to request data from the managing service.
 */
public class DataAccessServiceImpl extends IDataAccessService.Stub {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "DataAccessServiceImpl";
    @NonNull
    private final Context mApplicationContext;
    @NonNull
    private final String mServicePackageName;
    private final OnDevicePersonalizationVendorDataDao mVendorDataDao;
    @Nullable
    private final OnDevicePersonalizationLocalDataDao mLocalDataDao;
    @Nullable
    private final EventsDao mEventsDao;
    private final boolean mIncludeLocalData;
    private final boolean mIncludeEventData;
    @NonNull
    private final Injector mInjector;

    public DataAccessServiceImpl(
            @NonNull String servicePackageName,
            @NonNull Context applicationContext,
            boolean includeLocalData,
            boolean includeEventData) {
        this(servicePackageName, applicationContext, includeLocalData, includeEventData,
                new Injector());
    }

    @VisibleForTesting
    public DataAccessServiceImpl(
            @NonNull String servicePackageName,
            @NonNull Context applicationContext,
            boolean includeLocalData,
            boolean includeEventData,
            @NonNull Injector injector) {
        mApplicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
        mServicePackageName = Objects.requireNonNull(servicePackageName, "servicePackageName");
        mInjector = Objects.requireNonNull(injector, "injector");
        try {
            mVendorDataDao = mInjector.getVendorDataDao(
                    mApplicationContext, servicePackageName,
                    PackageUtils.getCertDigest(mApplicationContext, servicePackageName));
            mIncludeLocalData = includeLocalData;
            if (includeLocalData) {
                mLocalDataDao = mInjector.getLocalDataDao(
                        mApplicationContext, servicePackageName,
                        PackageUtils.getCertDigest(mApplicationContext, servicePackageName));
            } else {
                mLocalDataDao = null;
            }
        } catch (PackageManager.NameNotFoundException nnfe) {
            throw new IllegalArgumentException("Package: " + servicePackageName
                    + " does not exist.", nnfe);
        }
        mIncludeEventData = includeEventData;
        if (includeEventData) {
            mEventsDao = mInjector.getEventsDao(mApplicationContext);
        } else {
            mEventsDao = null;
        }
    }

    /** Handle a request from the isolated process. */
    @Override
    public void onRequest(
            int operation,
            @NonNull Bundle params,
            @NonNull IDataAccessServiceCallback callback
    ) {
        sLogger.d(TAG + ": onRequest: op=" + operation + " params: " + params.toString());
        switch (operation) {
            case Constants.DATA_ACCESS_OP_REMOTE_DATA_LOOKUP:
                String[] lookupKeys = params.getStringArray(Constants.EXTRA_LOOKUP_KEYS);
                if (lookupKeys == null) {
                    throw new IllegalArgumentException("Missing lookup keys.");
                }
                mInjector.getExecutor().execute(
                        () -> remoteDataLookup(
                                lookupKeys, callback));
                break;
            case Constants.DATA_ACCESS_OP_REMOTE_DATA_KEYSET:
                mInjector.getExecutor().execute(
                        () -> remoteDataKeyset(callback));
                break;
            case Constants.DATA_ACCESS_OP_LOCAL_DATA_LOOKUP:
                if (!mIncludeLocalData) {
                    throw new IllegalStateException("LocalData is not included for this instance.");
                }
                lookupKeys = params.getStringArray(Constants.EXTRA_LOOKUP_KEYS);
                if (lookupKeys == null) {
                    throw new IllegalArgumentException("Missing lookup keys.");
                }
                mInjector.getExecutor().execute(
                        () -> localDataLookup(
                                lookupKeys, callback));
                break;
            case Constants.DATA_ACCESS_OP_LOCAL_DATA_KEYSET:
                if (!mIncludeLocalData) {
                    throw new IllegalStateException("LocalData is not included for this instance.");
                }
                mInjector.getExecutor().execute(
                        () -> localDataKeyset(callback));
                break;
            case Constants.DATA_ACCESS_OP_LOCAL_DATA_PUT:
                if (!mIncludeLocalData) {
                    throw new IllegalStateException("LocalData is not included for this instance.");
                }
                String[] putKey = params.getStringArray(Constants.EXTRA_LOOKUP_KEYS);
                byte[] value = params.getByteArray(Constants.EXTRA_VALUE);
                if (value == null
                        || putKey == null || putKey.length != 1 || putKey[0] == null) {
                    throw new IllegalArgumentException("Invalid key or value for put.");
                }
                mInjector.getExecutor().execute(
                        () -> localDataPut(putKey[0], value, callback));
                break;
            case Constants.DATA_ACCESS_OP_LOCAL_DATA_REMOVE:
                if (!mIncludeLocalData) {
                    throw new IllegalStateException("LocalData is not included for this instance.");
                }
                String[] deleteKey = params.getStringArray(Constants.EXTRA_LOOKUP_KEYS);
                if (deleteKey == null || deleteKey.length != 1 || deleteKey[0] == null) {
                    throw new IllegalArgumentException("Invalid key provided for delete.");
                }
                mInjector.getExecutor().execute(
                        () -> localDataDelete(deleteKey[0], callback));
                break;
            case Constants.DATA_ACCESS_OP_GET_EVENT_URL:
                PersistableBundle eventParams = Objects.requireNonNull(params.getParcelable(
                        Constants.EXTRA_EVENT_PARAMS, PersistableBundle.class));
                byte[] responseData = params.getByteArray(Constants.EXTRA_RESPONSE_DATA);
                String mimeType = params.getString(Constants.EXTRA_MIME_TYPE);
                String destinationUrl = params.getString(Constants.EXTRA_DESTINATION_URL);
                mInjector.getExecutor().execute(
                        () -> getEventUrl(
                                eventParams, responseData, mimeType, destinationUrl, callback)
                );
                break;
            case Constants.DATA_ACCESS_OP_GET_REQUESTS:
                if (!mIncludeEventData) {
                    throw new IllegalStateException(
                            "request and event data are not included for this instance.");
                }
                long[] requestTimes = Objects.requireNonNull(params.getLongArray(
                        Constants.EXTRA_LOOKUP_KEYS));
                if (requestTimes.length != 2) {
                    throw new IllegalArgumentException("Invalid request timestamps provided.");
                }
                mInjector.getExecutor().execute(
                        () -> getRequests(requestTimes[0], requestTimes[1], callback));
                break;
            case Constants.DATA_ACCESS_OP_GET_JOINED_EVENTS:
                if (!mIncludeEventData) {
                    throw new IllegalStateException(
                            "request and event data are not included for this instance.");
                }
                long[] eventTimes = Objects.requireNonNull(params.getLongArray(
                        Constants.EXTRA_LOOKUP_KEYS));
                if (eventTimes.length != 2) {
                    throw new IllegalArgumentException("Invalid event timestamps provided.");
                }
                mInjector.getExecutor().execute(
                        () -> getJoinedEvents(eventTimes[0], eventTimes[1], callback));
                break;
            default:
                sendError(callback);
        }
    }

    private void remoteDataKeyset(@NonNull IDataAccessServiceCallback callback) {
        Bundle result = new Bundle();
        result.putSerializable(Constants.EXTRA_RESULT,
                new HashSet<>(mVendorDataDao.readAllVendorDataKeys()));
        sendResult(result, callback);
    }

    private void localDataKeyset(@NonNull IDataAccessServiceCallback callback) {
        Bundle result = new Bundle();
        result.putSerializable(Constants.EXTRA_RESULT,
                new HashSet<>(mLocalDataDao.readAllLocalDataKeys()));
        sendResult(result, callback);
    }

    private void remoteDataLookup(String[] keys, @NonNull IDataAccessServiceCallback callback) {
        HashMap<String, byte[]> vendorData = new HashMap<>();
        try {
            for (String key : keys) {
                vendorData.put(key, mVendorDataDao.readSingleVendorDataRow(key));
            }
            Bundle result = new Bundle();
            result.putSerializable(Constants.EXTRA_RESULT, vendorData);
            sendResult(result, callback);
        } catch (Exception e) {
            sendError(callback);
        }
    }

    private void localDataLookup(String[] keys, @NonNull IDataAccessServiceCallback callback) {
        HashMap<String, byte[]> localData = new HashMap<>();
        try {
            for (String key : keys) {
                localData.put(key, mLocalDataDao.readSingleLocalDataRow(key));
            }
            Bundle result = new Bundle();
            result.putSerializable(Constants.EXTRA_RESULT, localData);
            sendResult(result, callback);
        } catch (Exception e) {
            sendError(callback);
        }
    }

    private void localDataPut(String key, byte[] data,
            @NonNull IDataAccessServiceCallback callback) {
        HashMap<String, byte[]> localData = new HashMap<>();
        try {
            localData.put(key, mLocalDataDao.readSingleLocalDataRow(key));
            if (!mLocalDataDao.updateOrInsertLocalData(
                    new LocalData.Builder().setKey(key).setData(data).build())) {
                sendError(callback);
            }
            Bundle result = new Bundle();
            result.putSerializable(Constants.EXTRA_RESULT, localData);
            sendResult(result, callback);
        } catch (Exception e) {
            sendError(callback);
        }
    }

    private void localDataDelete(String key, @NonNull IDataAccessServiceCallback callback) {
        HashMap<String, byte[]> localData = new HashMap<>();
        try {
            localData.put(key, mLocalDataDao.readSingleLocalDataRow(key));
            mLocalDataDao.deleteLocalDataRow(key);
            Bundle result = new Bundle();
            result.putSerializable(Constants.EXTRA_RESULT, localData);
            sendResult(result, callback);
        } catch (Exception e) {
            sendError(callback);
        }
    }

    private void getEventUrl(
            @NonNull PersistableBundle eventParams,
            @Nullable byte[] responseData,
            @Nullable String mimeType,
            @Nullable String destinationUrl,
            @NonNull IDataAccessServiceCallback callback) {
        try {
            sLogger.d(TAG, ": getEventUrl() started.");
            EventUrlPayload payload = new EventUrlPayload(eventParams, responseData, mimeType);
            Uri eventUrl;
            if (destinationUrl == null || destinationUrl.isEmpty()) {
                eventUrl = EventUrlHelper.getEncryptedOdpEventUrl(payload);
            } else {
                eventUrl = EventUrlHelper.getEncryptedClickTrackingUrl(
                        payload, destinationUrl);
            }
            Bundle result = new Bundle();
            result.putParcelable(Constants.EXTRA_RESULT, eventUrl);
            sLogger.d(TAG + ": getEventUrl() success. Url: " + eventUrl.toString());
            sendResult(result, callback);
        } catch (Exception e) {
            sLogger.d(TAG + ": getEventUrl() failed.", e);
            sendError(callback);
        }
    }

    private void getRequests(long startTimeMillis, long endTimeMillis,
            @NonNull IDataAccessServiceCallback callback) {
        try {
            List<Query> queries = mEventsDao.readAllQueries(startTimeMillis, endTimeMillis,
                    mServicePackageName);
            List<RequestLogRecord> requestLogRecords = new ArrayList<>();
            for (Query query : queries) {
                RequestLogRecord record = new RequestLogRecord.Builder()
                        .setRows(OnDevicePersonalizationFlatbufferUtils
                                .getContentValuesFromQueryData(query.getQueryData()))
                        .setRequestId(query.getQueryId())
                        .setTimeMillis(query.getTimeMillis())
                        .build();
                requestLogRecords.add(record);
            }
            Bundle result = new Bundle();
            result.putParcelable(Constants.EXTRA_RESULT,
                    new OdpParceledListSlice<>(requestLogRecords));
            sendResult(result, callback);
        } catch (Exception e) {
            sendError(callback);
        }
    }

    private void getJoinedEvents(long startTimeMillis, long endTimeMillis,
            @NonNull IDataAccessServiceCallback callback) {
        try {
            List<JoinedEvent> joinedEvents = mEventsDao.readJoinedTableRows(startTimeMillis,
                    endTimeMillis,
                    mServicePackageName);
            List<EventLogRecord> joinedLogRecords = new ArrayList<>();
            for (JoinedEvent joinedEvent : joinedEvents) {
                RequestLogRecord requestLogRecord = new RequestLogRecord.Builder()
                        .setRequestId(joinedEvent.getQueryId())
                        .setRows(OnDevicePersonalizationFlatbufferUtils
                                .getContentValuesFromQueryData(joinedEvent.getQueryData()))
                        .setTimeMillis(joinedEvent.getQueryTimeMillis())
                        .build();
                EventLogRecord record = new EventLogRecord.Builder()
                        .setTimeMillis(joinedEvent.getEventTimeMillis())
                        .setType(joinedEvent.getType())
                        .setData(
                                OnDevicePersonalizationFlatbufferUtils
                                        .getContentValuesFromEventData(joinedEvent.getEventData()))
                        .setRequestLogRecord(requestLogRecord)
                        .build();
                joinedLogRecords.add(record);
            }
            Bundle result = new Bundle();
            result.putParcelable(Constants.EXTRA_RESULT,
                    new OdpParceledListSlice<>(joinedLogRecords));
            sendResult(result, callback);
        } catch (Exception e) {
            sendError(callback);
        }
    }

    private void sendResult(
            @NonNull Bundle result,
            @NonNull IDataAccessServiceCallback callback) {
        try {
            callback.onSuccess(result);
        } catch (RemoteException e) {
            sLogger.e(TAG + ": Callback error", e);
        }
    }

    private void sendError(@NonNull IDataAccessServiceCallback callback) {
        try {
            callback.onError(Constants.STATUS_INTERNAL_ERROR);
        } catch (RemoteException e) {
            sLogger.e(TAG + ": Callback error", e);
        }
    }

    @VisibleForTesting
    static class Injector {
        long getTimeMillis() {
            return System.currentTimeMillis();
        }

        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        OnDevicePersonalizationVendorDataDao getVendorDataDao(
                Context context, String packageName, String certDigest
        ) {
            return OnDevicePersonalizationVendorDataDao.getInstance(context,
                    packageName, certDigest);
        }

        OnDevicePersonalizationLocalDataDao getLocalDataDao(
                Context context, String packageName, String certDigest
        ) {
            return OnDevicePersonalizationLocalDataDao.getInstance(context,
                    packageName, certDigest);
        }

        EventsDao getEventsDao(
                Context context
        ) {
            return EventsDao.getInstance(context);
        }
    }
}
