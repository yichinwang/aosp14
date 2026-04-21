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

package com.android.ondevicepersonalization.services.download;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.DownloadCompletedOutputParcel;
import android.adservices.ondevicepersonalization.DownloadInputParcel;
import android.adservices.ondevicepersonalization.UserData;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.JsonReader;


import com.android.ondevicepersonalization.internal.util.ByteArrayParceledListSlice;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.internal.util.StringParceledListSlice;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.DataAccessServiceImpl;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.data.vendor.VendorData;
import com.android.ondevicepersonalization.services.download.mdd.MobileDataDownloadFactory;
import com.android.ondevicepersonalization.services.download.mdd.OnDevicePersonalizationFileGroupPopulator;
import com.android.ondevicepersonalization.services.federatedcompute.FederatedComputeServiceImpl;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.policyengine.UserDataAccessor;
import com.android.ondevicepersonalization.services.process.IsolatedServiceInfo;
import com.android.ondevicepersonalization.services.process.ProcessRunner;
import com.android.ondevicepersonalization.services.statsd.ApiCallStats;
import com.android.ondevicepersonalization.services.statsd.OdpStatsdLogger;
import com.android.ondevicepersonalization.services.util.Clock;
import com.android.ondevicepersonalization.services.util.MonotonicClock;
import com.android.ondevicepersonalization.services.util.PackageUtils;
import com.android.ondevicepersonalization.services.util.StatsUtils;

import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.RemoveFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.openers.ReadStreamOpener;
import com.google.common.util.concurrent.AsyncCallable;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * AsyncCallable to handle the processing of the downloaded vendor data
 */
public class OnDevicePersonalizationDataProcessingAsyncCallable implements AsyncCallable {
    public static final String TASK_NAME = "DownloadJob";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationDataProcessingAsyncCallable";
    private final String mPackageName;
    private final Context mContext;
    private OnDevicePersonalizationVendorDataDao mDao;

    static class Injector {
        Clock getClock() {
            return MonotonicClock.getInstance();
        }

        ProcessRunner getProcessRunner() {
            return ProcessRunner.getInstance();
        }
    }

    private final Injector mInjector;

    public OnDevicePersonalizationDataProcessingAsyncCallable(String packageName,
            Context context) {
        this(packageName, context, new Injector());
    }

    public OnDevicePersonalizationDataProcessingAsyncCallable(String packageName,
            Context context, Injector injector) {
        mPackageName = packageName;
        mContext = context;
        mInjector = injector;
    }

    private static boolean validateSyncToken(long syncToken) {
        // TODO(b/249813538) Add any additional requirements
        return syncToken % 3600 == 0;
    }

    /**
     * Processes the downloaded files for the given package and stores the data into sqlite
     * vendor tables
     */
    public ListenableFuture<Boolean> call() {
        sLogger.d(TAG + ": Package Name: " + mPackageName);
        MobileDataDownload mdd = MobileDataDownloadFactory.getMdd(mContext);
        try {
            String fileGroupName =
                    OnDevicePersonalizationFileGroupPopulator.createPackageFileGroupName(
                            mPackageName, mContext);
            ClientFileGroup clientFileGroup = mdd.getFileGroup(
                    GetFileGroupRequest.newBuilder().setGroupName(fileGroupName).build()).get();
            if (clientFileGroup == null) {
                sLogger.d(TAG + mPackageName + " has no completed downloads.");
                return Futures.immediateFuture(null);
            }
            // It is currently expected that we will only download a single file per package.
            if (clientFileGroup.getFileCount() != 1) {
                sLogger.d(TAG + mPackageName + " has " + clientFileGroup.getFileCount()
                        + " files in the fileGroup");
                return Futures.immediateFuture(null);
            }
            ClientFile clientFile = clientFileGroup.getFile(0);
            Uri androidUri = Uri.parse(clientFile.getFileUri());
            // Manually remove fileGroup after processing. Any fileGroups not removed here, will
            // be caught by MDD maintenance based on stale and expiration settings.
            return FluentFuture.from(processDownloadedJsonFile(androidUri))
                    .transformAsync(unused -> mdd.removeFileGroup(
                    RemoveFileGroupRequest.newBuilder().setGroupName(
                            fileGroupName).build()),
                    OnDevicePersonalizationExecutors.getBackgroundExecutor());
        } catch (PackageManager.NameNotFoundException e) {
            sLogger.d(TAG + ": NameNotFoundException for package: " + mPackageName);
        } catch (ExecutionException e) {
            sLogger.e(TAG + ": Exception for package: " + mPackageName, e);
        } catch (InterruptedException e) {
            sLogger.d(TAG + mPackageName + " was interrupted.");
        }
        return Futures.immediateFuture(null);
    }

    private ListenableFuture<Void> processDownloadedJsonFile(Uri uri) throws
            PackageManager.NameNotFoundException, InterruptedException, ExecutionException {
        sLogger.d(TAG + ": begin processDownloadJsonFile");
        long syncToken = -1;
        Map<String, VendorData> vendorDataMap = null;

        SynchronousFileStorage fileStorage = MobileDataDownloadFactory.getFileStorage(mContext);
        try (InputStream in = fileStorage.open(uri, ReadStreamOpener.create())) {
            try (JsonReader reader = new JsonReader(new InputStreamReader(in))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("syncToken")) {
                        syncToken = reader.nextLong();
                    } else if (name.equals("contents")) {
                        vendorDataMap = readContentsArray(reader);
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            }
        } catch (IOException e) {
            sLogger.d(TAG + mPackageName + " Failed to process downloaded JSON file");
            return Futures.immediateFuture(null);
        }

        if (syncToken == -1 || !validateSyncToken(syncToken)) {
            sLogger.d(TAG + mPackageName + " downloaded JSON file has invalid syncToken provided");
            return Futures.immediateFuture(null);
        }
        if (vendorDataMap == null || vendorDataMap.size() == 0) {
            sLogger.d(TAG + mPackageName + " downloaded JSON file has no content provided");
            return Futures.immediateFuture(null);
        }

        mDao = OnDevicePersonalizationVendorDataDao.getInstance(
                mContext, mPackageName,
                PackageUtils.getCertDigest(mContext, mPackageName));
        long existingSyncToken = mDao.getSyncToken();

        // If existingToken is greaterThan or equal to the new token, skip as there is no new data.
        if (existingSyncToken >= syncToken) {
            sLogger.d(TAG + ": syncToken is not newer than existing token.");
            return Futures.immediateFuture(null);
        }

        Map<String, VendorData> finalVendorDataMap = vendorDataMap;
        long finalSyncToken = syncToken;
        try {
            ListenableFuture<IsolatedServiceInfo> loadFuture =
                    mInjector.getProcessRunner().loadIsolatedService(
                        TASK_NAME, mPackageName);
            var resultFuture = FluentFuture.from(loadFuture)
                    .transformAsync(
                            result -> executeDownloadHandler(result, finalVendorDataMap),
                            OnDevicePersonalizationExecutors.getBackgroundExecutor())
                    .transform(pluginResult -> filterAndStoreData(pluginResult, finalSyncToken,
                                    finalVendorDataMap),
                            OnDevicePersonalizationExecutors.getBackgroundExecutor())
                    .catching(
                            Exception.class,
                            e -> {
                                sLogger.e(TAG + ": Processing failed.", e);
                                return null;
                            },
                            OnDevicePersonalizationExecutors.getBackgroundExecutor());

            var unused = Futures.whenAllComplete(loadFuture, resultFuture)
                    .callAsync(() -> mInjector.getProcessRunner().unloadIsolatedService(
                            loadFuture.get()),
                        OnDevicePersonalizationExecutors.getBackgroundExecutor());

            return resultFuture;
        } catch (Exception e) {
            sLogger.e(TAG + ": Could not run isolated service.", e);
            return Futures.immediateFuture(null);
        }
    }

    private Void filterAndStoreData(Bundle pluginResult, long syncToken,
            Map<String, VendorData> vendorDataMap) {
        sLogger.d(TAG + ": Plugin filter code completed successfully");
        List<VendorData> filteredList = new ArrayList<>();
        DownloadCompletedOutputParcel downloadResult = pluginResult.getParcelable(
                Constants.EXTRA_RESULT, DownloadCompletedOutputParcel.class);
        List<String> retainedKeys = downloadResult.getRetainedKeys();
        if (retainedKeys == null) {
            // TODO(b/270710021): Determine how to correctly handle null retainedKeys.
            return null;
        }
        for (String key : retainedKeys) {
            if (vendorDataMap.containsKey(key)) {
                filteredList.add(vendorDataMap.get(key));
            }
        }
        mDao.batchUpdateOrInsertVendorDataTransaction(filteredList, retainedKeys,
                syncToken);
        return null;
    }

    private ListenableFuture<Bundle> executeDownloadHandler(
            IsolatedServiceInfo isolatedServiceInfo,
            Map<String, VendorData> vendorDataMap) {
        Bundle pluginParams = new Bundle();
        DataAccessServiceImpl binder = new DataAccessServiceImpl(
                mPackageName, mContext, /* includeLocalData */ true,
                /* includeEventData */ true);
        pluginParams.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, binder);
        FederatedComputeServiceImpl fcpBinder = new FederatedComputeServiceImpl(
                mPackageName, mContext);
        pluginParams.putBinder(Constants.EXTRA_FEDERATED_COMPUTE_SERVICE_BINDER, fcpBinder);

        List<String> keys = new ArrayList<>();
        List<byte[]> values = new ArrayList<>();
        for (String key : vendorDataMap.keySet()) {
            keys.add(key);
            values.add(vendorDataMap.get(key).getData());
        }
        StringParceledListSlice keysListSlice = new StringParceledListSlice(keys);
        // This needs to be set to a small number >0 for the parcel.
        keysListSlice.setInlineCountLimit(1);
        ByteArrayParceledListSlice valuesListSlice = new ByteArrayParceledListSlice(values);
        valuesListSlice.setInlineCountLimit(1);

        DownloadInputParcel downloadInputParcel = new DownloadInputParcel.Builder()
                .setDownloadedKeys(keysListSlice)
                .setDownloadedValues(valuesListSlice)
                .build();

        pluginParams.putParcelable(Constants.EXTRA_INPUT, downloadInputParcel);

        UserDataAccessor userDataAccessor = new UserDataAccessor();
        UserData userData = userDataAccessor.getUserData();
        pluginParams.putParcelable(Constants.EXTRA_USER_DATA, userData);
        ListenableFuture<Bundle> result = mInjector.getProcessRunner().runIsolatedService(
                isolatedServiceInfo,
                AppManifestConfigHelper.getServiceNameFromOdpSettings(mContext, mPackageName),
                Constants.OP_DOWNLOAD,
                pluginParams);
        return FluentFuture.from(result)
                .transform(
                    val -> {
                        writeServiceRequestMetrics(
                                val, isolatedServiceInfo.getStartTimeMillis(),
                                Constants.STATUS_SUCCESS);
                        return val;
                    },
                    OnDevicePersonalizationExecutors.getBackgroundExecutor()
                )
                .catchingAsync(
                    Exception.class,
                    e -> {
                        writeServiceRequestMetrics(
                                null, isolatedServiceInfo.getStartTimeMillis(),
                                Constants.STATUS_INTERNAL_ERROR);
                        return Futures.immediateFailedFuture(e);
                    },
                    OnDevicePersonalizationExecutors.getBackgroundExecutor()
                );
    }

    private Map<String, VendorData> readContentsArray(JsonReader reader) throws IOException {
        Map<String, VendorData> vendorDataMap = new HashMap<>();
        reader.beginArray();
        while (reader.hasNext()) {
            VendorData data = readContent(reader);
            if (data != null) {
                vendorDataMap.put(data.getKey(), data);
            }
        }
        reader.endArray();

        return vendorDataMap;
    }

    private VendorData readContent(JsonReader reader) throws IOException {
        String key = null;
        byte[] data = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("key")) {
                key = reader.nextString();
            } else if (name.equals("data")) {
                data = reader.nextString().getBytes(StandardCharsets.UTF_8);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        if (key == null || data == null) {
            return null;
        }
        return new VendorData.Builder().setKey(key).setData(data).build();
    }

    private void writeServiceRequestMetrics(Bundle result, long startTimeMillis, int responseCode) {
        int latencyMillis = (int) (mInjector.getClock().elapsedRealtime() - startTimeMillis);
        int overheadLatencyMillis =
                (int) StatsUtils.getOverheadLatencyMillis(latencyMillis, result);
        ApiCallStats callStats =
                new ApiCallStats.Builder(ApiCallStats.API_SERVICE_ON_DOWNLOAD_COMPLETED)
                .setLatencyMillis(latencyMillis)
                .setOverheadLatencyMillis(overheadLatencyMillis)
                .setResponseCode(responseCode)
                .build();
        OdpStatsdLogger.getInstance().logApiCallStats(callStats);
    }
}
