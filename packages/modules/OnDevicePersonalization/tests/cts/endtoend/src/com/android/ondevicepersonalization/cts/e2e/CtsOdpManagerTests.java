/*
 * Copyright 2023 The Android Open Source Project
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
package com.android.ondevicepersonalization.cts.e2e;

import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;
import android.adservices.ondevicepersonalization.SurfacePackageToken;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.display.DisplayManager;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.view.Display;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.SurfaceView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * CTS Test cases for OnDevicePersonalizationManager APIs.
 */
@RunWith(AndroidJUnit4.class)
public class CtsOdpManagerTests {
    private static final String SERVICE_PACKAGE =
            "com.android.ondevicepersonalization.testing.sampleservice";
    private static final String SERVICE_CLASS =
            "com.android.ondevicepersonalization.testing.sampleservice.SampleService";

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Rule
    public final ActivityScenarioRule<TestActivity> mActivityScenarioRule =
            new ActivityScenarioRule<>(TestActivity.class);

    @Test
    public void testExecuteThrowsIfComponentNameMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        assertThrows(
                NullPointerException.class,
                () -> manager.execute(
                        null,
                        PersistableBundle.EMPTY,
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<List<SurfacePackageToken>>()));
    }

    @Test
    public void testExecuteThrowsIfParamsMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        assertThrows(
                NullPointerException.class,
                () -> manager.execute(
                        new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                        null,
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<List<SurfacePackageToken>>()));
    }

    @Test
    public void testExecuteThrowsIfExecutorMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        assertThrows(
                NullPointerException.class,
                () -> manager.execute(
                        new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                        PersistableBundle.EMPTY,
                        null,
                        new ResultReceiver<List<SurfacePackageToken>>()));
    }

    @Test
    public void testExecuteThrowsIfReceiverMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        assertThrows(
                NullPointerException.class,
                () -> manager.execute(
                        new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                        PersistableBundle.EMPTY,
                        Executors.newSingleThreadExecutor(),
                        null));
    }

    @Test
    public void testExecuteThrowsIfPackageNameMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.execute(
                    new ComponentName("", SERVICE_CLASS),
                        PersistableBundle.EMPTY,
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<List<SurfacePackageToken>>()));
    }

    @Test
    public void testExecuteThrowsIfClassNameMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.execute(
                    new ComponentName(SERVICE_PACKAGE, ""),
                        PersistableBundle.EMPTY,
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<List<SurfacePackageToken>>()));
    }

    @Test
    public void testExecuteReturnsNameNotFoundIfServiceNotInstalled() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<List<SurfacePackageToken>>();
        manager.execute(
                new ComponentName("somepackage", "someclass"),
                PersistableBundle.EMPTY,
                Executors.newSingleThreadExecutor(),
                receiver);
        receiver.await();
        assertNull(receiver.getResult());
        assertTrue(receiver.getException() instanceof NameNotFoundException);
    }

    @Test
    public void testExecuteReturnsClassNotFoundIfServiceClassNotFound()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<List<SurfacePackageToken>>();
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, "someclass"),
                PersistableBundle.EMPTY,
                Executors.newSingleThreadExecutor(),
                receiver);
        receiver.await();
        assertNull(receiver.getResult());
        assertTrue(receiver.getException() instanceof ClassNotFoundException);
    }

    @Test
    public void testExecute() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<List<SurfacePackageToken>>();
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                PersistableBundle.EMPTY,
                Executors.newSingleThreadExecutor(),
                receiver);
        receiver.await();
        List<SurfacePackageToken> results = receiver.getResult();
        assertNotNull(results);
        assertEquals(1, results.size());
        SurfacePackageToken token = results.get(0);
        assertNotNull(token);
    }

    @Test
    public void testRequestSurfacePackage() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        List<SurfacePackageToken> tokens =
                runExecute(manager, PersistableBundle.EMPTY);
        var receiver = new ResultReceiver<SurfacePackage>();
        SurfaceView surfaceView = createSurfaceView();
        manager.requestSurfacePackage(
                tokens.get(0),
                surfaceView.getHostToken(),
                getDisplayId(),
                surfaceView.getWidth(),
                surfaceView.getHeight(),
                Executors.newSingleThreadExecutor(),
                receiver);
        receiver.await();
        assertNotNull(receiver.getResult());
    }

    @Test
    public void testRequestSurfacePackageThrowsIfSurfacePackageTokenMissing()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        SurfaceView surfaceView = createSurfaceView();
        assertThrows(
                NullPointerException.class,
                () -> manager.requestSurfacePackage(
                        null,
                        surfaceView.getHostToken(),
                        getDisplayId(),
                        surfaceView.getWidth(),
                        surfaceView.getHeight(),
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<SurfacePackage>()));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfSurfaceViewHostTokenMissing()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        List<SurfacePackageToken> tokens =
                runExecute(manager, PersistableBundle.EMPTY);
        SurfaceView surfaceView = createSurfaceView();
        assertThrows(
                NullPointerException.class,
                () -> manager.requestSurfacePackage(
                        tokens.get(0),
                        null,
                        getDisplayId(),
                        surfaceView.getWidth(),
                        surfaceView.getHeight(),
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<SurfacePackage>()));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfInvalidDisplayId()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        List<SurfacePackageToken> tokens =
                runExecute(manager, PersistableBundle.EMPTY);
        SurfaceView surfaceView = createSurfaceView();
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.requestSurfacePackage(
                        tokens.get(0),
                        surfaceView.getHostToken(),
                        -1,
                        surfaceView.getWidth(),
                        surfaceView.getHeight(),
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<SurfacePackage>()));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfInvalidWidth()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        List<SurfacePackageToken> tokens =
                runExecute(manager, PersistableBundle.EMPTY);
        SurfaceView surfaceView = createSurfaceView();
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.requestSurfacePackage(
                        tokens.get(0),
                        surfaceView.getHostToken(),
                        getDisplayId(),
                        0,
                        surfaceView.getHeight(),
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<SurfacePackage>()));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfInvalidHeight()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        List<SurfacePackageToken> tokens =
                runExecute(manager, PersistableBundle.EMPTY);
        SurfaceView surfaceView = createSurfaceView();
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.requestSurfacePackage(
                        tokens.get(0),
                        surfaceView.getHostToken(),
                        getDisplayId(),
                        surfaceView.getWidth(),
                        0,
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<SurfacePackage>()));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfExecutorMissing()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        List<SurfacePackageToken> tokens =
                runExecute(manager, PersistableBundle.EMPTY);
        SurfaceView surfaceView = createSurfaceView();
        assertThrows(
                NullPointerException.class,
                () -> manager.requestSurfacePackage(
                        tokens.get(0),
                        surfaceView.getHostToken(),
                        getDisplayId(),
                        surfaceView.getWidth(),
                        surfaceView.getHeight(),
                        null,
                        new ResultReceiver<SurfacePackage>()));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfOutcomeReceiverMissing()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        List<SurfacePackageToken> tokens =
                runExecute(manager, PersistableBundle.EMPTY);
        SurfaceView surfaceView = createSurfaceView();
        assertThrows(
                NullPointerException.class,
                () -> manager.requestSurfacePackage(
                        tokens.get(0),
                        surfaceView.getHostToken(),
                        getDisplayId(),
                        surfaceView.getWidth(),
                        surfaceView.getHeight(),
                        Executors.newSingleThreadExecutor(),
                        null));
    }

    int getDisplayId() {
        final DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        final Display primaryDisplay = dm.getDisplay(DEFAULT_DISPLAY);
        final Context windowContext = mContext.createDisplayContext(primaryDisplay);
        return windowContext.getDisplay().getDisplayId();
    }

    SurfaceView createSurfaceView() throws InterruptedException {
        ArrayBlockingQueue<SurfaceView> viewQueue = new ArrayBlockingQueue<>(1);
        mActivityScenarioRule.getScenario().onActivity(
                a -> viewQueue.add(a.findViewById(R.id.test_surface_view)));
        return viewQueue.take();
    }

    private List<SurfacePackageToken> runExecute(
            OnDevicePersonalizationManager manager, PersistableBundle params)
            throws InterruptedException {
        var receiver = new ResultReceiver<List<SurfacePackageToken>>();
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                params,
                Executors.newSingleThreadExecutor(),
                receiver);
        receiver.await();
        List<SurfacePackageToken> results = receiver.getResult();
        return results;
    }

    class ResultReceiver<T> implements OutcomeReceiver<T, Exception> {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private T mResult;
        private Exception mException;
        @Override public void onResult(T result) {
            mResult = result;
            mLatch.countDown();
        }
        @Override public void onError(Exception e) {
            mException = e;
            mLatch.countDown();
        }
        void await() throws InterruptedException {
            mLatch.await();
        }
        T getResult() {
            return mResult;
        }
        Exception getException() {
            return mException;
        }
    }
}
