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

package com.android.adservices.service.adselection.encryption;

import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.EncryptionKeyDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.SingletonRunner;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ExecutionSequencer;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Worker instance for fetching encryption keys and persisting to DB. */
public class BackgroundKeyFetchWorker {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final String JOB_DESCRIPTION = "Ad selection data encryption key fetch job";
    private static final Object SINGLETON_LOCK = new Object();
    private static volatile BackgroundKeyFetchWorker sBackgroundKeyFetchWorker;
    private final AdSelectionEncryptionKeyManager mKeyManager;
    private final Flags mFlags;
    private final Clock mClock;
    private final SingletonRunner<Void> mSingletonRunner =
            new SingletonRunner<>(JOB_DESCRIPTION, this::doRun);

    @VisibleForTesting
    protected BackgroundKeyFetchWorker(
            @NonNull AdSelectionEncryptionKeyManager keyManager,
            @NonNull Flags flags,
            @NonNull Clock clock) {
        Objects.requireNonNull(keyManager);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(clock);
        mKeyManager = keyManager;
        mClock = clock;
        mFlags = flags;
    }

    /**
     * Gets an instance of a {@link BackgroundKeyFetchWorker}. If an instance hasn't been
     * initialized, a new singleton will be created and returned.
     */
    @NonNull
    public static BackgroundKeyFetchWorker getInstance(@NonNull Context context) {
        Objects.requireNonNull(context);

        if (sBackgroundKeyFetchWorker == null) {
            synchronized (SINGLETON_LOCK) {
                if (sBackgroundKeyFetchWorker == null) {
                    EncryptionKeyDao encryptionKeyDao =
                            AdSelectionServerDatabase.getInstance(context).encryptionKeyDao();
                    Flags flags = FlagsFactory.getFlags();
                    AdServicesHttpsClient adServicesHttpsClient =
                            new AdServicesHttpsClient(
                                    AdServicesExecutors.getBlockingExecutor(),
                                    flags
                                            .getFledgeAuctionServerBackgroundKeyFetchNetworkConnectTimeoutMs(),
                                    flags
                                            .getFledgeAuctionServerBackgroundKeyFetchNetworkReadTimeoutMs(),
                                    flags.getFledgeAuctionServerBackgroundKeyFetchMaxResponseSizeB());
                    AdSelectionEncryptionKeyManager keyManager =
                            new AdSelectionEncryptionKeyManager(
                                    encryptionKeyDao,
                                    flags,
                                    adServicesHttpsClient,
                                    AdServicesExecutors.getBackgroundExecutor());
                    sBackgroundKeyFetchWorker =
                            new BackgroundKeyFetchWorker(keyManager, flags, Clock.systemUTC());
                }
            }
        }
        return sBackgroundKeyFetchWorker;
    }

    public Flags getFlags() {
        return mFlags;
    }

    private FluentFuture<Void> doRun(@NonNull Supplier<Boolean> shouldStop) {
        if (shouldStop.get()) {
            sLogger.d("Stopping " + JOB_DESCRIPTION);
            return FluentFuture.from(Futures.immediateVoidFuture())
                    .transform(ignored -> null, AdServicesExecutors.getLightWeightExecutor());
        }

        Instant keyExpiryInstant = mClock.instant();
        Set<Integer> expiredKeyTypes =
                mKeyManager.getExpiredAdSelectionEncryptionKeyTypes(keyExpiryInstant);

        if (expiredKeyTypes.isEmpty()) {
            return FluentFuture.from(Futures.immediateVoidFuture())
                    .transform(ignored -> null, AdServicesExecutors.getLightWeightExecutor());
        }

        List<ListenableFuture<List<DBEncryptionKey>>> keyFetchFutures = new ArrayList<>();

        // Keys are fetched and persisted in sequence to prevent making multiple network
        // calls in parallel.
        ExecutionSequencer sequencer = ExecutionSequencer.create();


        if (mFlags.getFledgeAuctionServerBackgroundAuctionKeyFetchEnabled()
                && expiredKeyTypes.contains(
                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION)
                && !shouldStop.get()) {
            keyFetchFutures.add(fetchAndPersistAuctionKey(keyExpiryInstant, sequencer));
        }


        if (mFlags.getFledgeAuctionServerBackgroundJoinKeyFetchEnabled()
                && expiredKeyTypes.contains(
                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN)
                && !shouldStop.get()) {
            keyFetchFutures.add(fetchAndPersistJoinKey(keyExpiryInstant, sequencer));
        }

        return FluentFuture.from(Futures.allAsList(keyFetchFutures))
                .withTimeout(
                        mFlags.getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs(),
                        TimeUnit.MILLISECONDS,
                        AdServicesExecutors.getScheduler())
                .transform(ignored -> null, AdServicesExecutors.getLightWeightExecutor());
    }

    /**
     * Runs the background key fetch job for Ad Selection Data, including persisting fetched key
     * and removing expired keys.
     *
     * @return A future to be used to check when the task has completed.
     */
    public FluentFuture<Void> runBackgroundKeyFetch() {
        sLogger.d("Starting %s", JOB_DESCRIPTION);
        return mSingletonRunner.runSingleInstance();
    }

    /** Requests that any ongoing work be stopped gracefully and waits for work to be stopped. */
    public void stopWork() {
        mSingletonRunner.stopWork();
    }

    private ListenableFuture<List<DBEncryptionKey>> fetchAndPersistAuctionKey(
            Instant keyExpiryInstant,
            ExecutionSequencer sequencer) {
        return sequencer.submitAsync(
                () ->
                        mKeyManager.fetchAndPersistActiveKeysOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType
                                        .AUCTION,
                                keyExpiryInstant,
                                mFlags
                                        .getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs()),
                AdServicesExecutors.getBackgroundExecutor());
    }

    private ListenableFuture<List<DBEncryptionKey>> fetchAndPersistJoinKey(
            Instant keyExpiryInstant,
            ExecutionSequencer sequencer) {
        return sequencer.submitAsync(
            () ->
                    mKeyManager.fetchAndPersistActiveKeysOfType(
                            AdSelectionEncryptionKey.AdSelectionEncryptionKeyType
                                    .JOIN,
                            keyExpiryInstant,
                            mFlags
                                    .getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs()),
            AdServicesExecutors.getBackgroundExecutor());

    }
}
