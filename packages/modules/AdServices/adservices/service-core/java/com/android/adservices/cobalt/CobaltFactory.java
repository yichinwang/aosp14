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

package com.android.adservices.cobalt;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.cobalt.CobaltLogger;
import com.android.cobalt.CobaltPeriodicJob;
import com.android.cobalt.CobaltPipelineType;
import com.android.cobalt.crypto.HpkeEncrypter;
import com.android.cobalt.data.DataService;
import com.android.cobalt.domain.Project;
import com.android.cobalt.impl.CobaltLoggerImpl;
import com.android.cobalt.impl.CobaltPeriodicJobImpl;
import com.android.cobalt.observations.PrivacyGenerator;
import com.android.cobalt.system.SystemClockImpl;
import com.android.cobalt.system.SystemData;
import com.android.internal.annotations.GuardedBy;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/** Factory for Cobalt's logger and periodic job implementations. */
public final class CobaltFactory {
    private static final Object SINGLETON_LOCK = new Object();

    /*
     * Uses the prod pipeline because AdServices' reports are for either the DEBUG or GA release
     * stage and DEBUG is sufficient for local testing.
     */
    private static final CobaltPipelineType PIPELINE_TYPE = CobaltPipelineType.PROD;

    // Objects which are non-trivial to construct or need to be shared between the logger and
    // periodic job are static.
    private static Project sSingletonCobaltRegistryProject;
    private static DataService sSingletonDataService;
    private static SecureRandom sSingletonSecureRandom;

    @GuardedBy("SINGLETON_LOCK")
    private static CobaltLogger sSingletonCobaltLogger;

    @GuardedBy("SINGLETON_LOCK")
    private static CobaltPeriodicJob sSingletonCobaltPeriodicJob;

    /**
     * Returns the static singleton CobaltLogger.
     *
     * @throws CobaltInitializationException if an unrecoverable errors occurs during initialization
     */
    @NonNull
    public static CobaltLogger getCobaltLogger(@NonNull Context context, @NonNull Flags flags)
            throws CobaltInitializationException {
        Objects.requireNonNull(context);
        Objects.requireNonNull(flags);
        synchronized (SINGLETON_LOCK) {
            if (sSingletonCobaltLogger == null) {
                sSingletonCobaltLogger =
                        sSingletonCobaltLogger =
                                new CobaltLoggerImpl(
                                        getRegistry(context),
                                        CobaltReleaseStages.getReleaseStage(
                                                flags.getAdservicesReleaseStageForCobalt()),
                                        getDataService(context),
                                        new SystemData(),
                                        getExecutor(),
                                        new SystemClockImpl(),
                                        flags.getTopicsCobaltLoggingEnabled());
            }
            return sSingletonCobaltLogger;
        }
    }

    /**
     * Returns the static singleton CobaltPeriodicJob.
     *
     * <p>Note, this implementation does not result in any data being uploaded because the upload
     * API does not exist yet and the actual uploader is blocked on it landing.
     *
     * @throws CobaltInitializationException if an unrecoverable errors occurs during initialization
     */
    @NonNull
    public static CobaltPeriodicJob getCobaltPeriodicJob(
            @NonNull Context context, @NonNull Flags flags) throws CobaltInitializationException {
        Objects.requireNonNull(context);
        Objects.requireNonNull(flags);
        synchronized (SINGLETON_LOCK) {
            if (sSingletonCobaltPeriodicJob == null) {
                sSingletonCobaltPeriodicJob =
                        new CobaltPeriodicJobImpl(
                                getRegistry(context),
                                CobaltReleaseStages.getReleaseStage(
                                        flags.getAdservicesReleaseStageForCobalt()),
                                getDataService(context),
                                getExecutor(),
                                getScheduledExecutor(),
                                new SystemClockImpl(),
                                new SystemData(),
                                new PrivacyGenerator(getSecureRandom()),
                                getSecureRandom(),
                                new CobaltUploader(context, PIPELINE_TYPE),
                                HpkeEncrypter.createForEnvironment(
                                        new HpkeEncryptImpl(), PIPELINE_TYPE),
                                CobaltApiKeys.copyFromHexApiKey(
                                        flags.getCobaltAdservicesApiKeyHex()),
                                Duration.ofMillis(flags.getCobaltUploadServiceUnbindDelayMs()),
                                flags.getTopicsCobaltLoggingEnabled());
            }
            return sSingletonCobaltPeriodicJob;
        }
    }

    @NonNull
    private static ExecutorService getExecutor() {
        // Cobalt requires disk I/O and must run on the background executor.
        return AdServicesExecutors.getBackgroundExecutor();
    }

    @NonNull
    private static ScheduledExecutorService getScheduledExecutor() {
        // Cobalt requires a timeout to disconnect from the system server.
        return AdServicesExecutors.getScheduler();
    }

    @NonNull
    private static Project getRegistry(Context context) throws CobaltInitializationException {
        if (sSingletonCobaltRegistryProject == null) {
            sSingletonCobaltRegistryProject = CobaltRegistryLoader.getRegistry(context);
        }
        return sSingletonCobaltRegistryProject;
    }

    @NonNull
    private static DataService getDataService(@NonNull Context context) {
        Objects.requireNonNull(context);
        if (sSingletonDataService == null) {
            sSingletonDataService =
                    CobaltDataServiceFactory.createDataService(context, getExecutor());
        }

        return sSingletonDataService;
    }

    @NonNull
    private static SecureRandom getSecureRandom() {
        if (sSingletonSecureRandom == null) {
            sSingletonSecureRandom = new SecureRandom();
        }

        return sSingletonSecureRandom;
    }
}
