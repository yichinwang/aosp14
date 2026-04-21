package com.android.server.appsearch;

import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.Executor;

/** This is a factory provider class that holds all factories needed by AppSearch. */
public final class AppSearchEnvironmentFactory {
    private static volatile AppSearchEnvironment mEnvironmentInstance;
    private static volatile FrameworkAppSearchConfig mConfigInstance;

    public static AppSearchEnvironment getEnvironmentInstance() {
        AppSearchEnvironment localRef = mEnvironmentInstance;
        if (localRef == null) {
            synchronized (AppSearchEnvironmentFactory.class) {
                localRef = mEnvironmentInstance;
                if (localRef == null) {
                    mEnvironmentInstance = localRef =
                            new FrameworkAppSearchEnvironment();
                }
            }
        }
        return localRef;
    }

    public static FrameworkAppSearchConfig getConfigInstance(Executor executor) {
        FrameworkAppSearchConfig localRef = mConfigInstance;
        if (localRef == null) {
            synchronized (AppSearchEnvironmentFactory.class) {
                localRef = mConfigInstance;
                if (localRef == null) {
                    mConfigInstance = localRef = FrameworkAppSearchConfigImpl
                            .getInstance(executor);
                }
            }
        }
        return localRef;
    }

    @VisibleForTesting
    static void setEnvironmentInstanceForTest(
            AppSearchEnvironment appSearchEnvironment) {
        synchronized (AppSearchEnvironmentFactory.class) {
            mEnvironmentInstance = appSearchEnvironment;
        }
    }

    @VisibleForTesting
    static void setConfigInstanceForTest(
            FrameworkAppSearchConfig appSearchConfig) {
        synchronized (AppSearchEnvironmentFactory.class) {
            mConfigInstance = appSearchConfig;
        }
    }

    private AppSearchEnvironmentFactory() {
    }
}

