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

package com.android.ondevicepersonalization.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationConfigServiceCallback;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.ServiceTestRule;

import com.android.ondevicepersonalization.services.data.user.RawUserData;
import com.android.ondevicepersonalization.services.data.user.UserDataCollector;
import com.android.ondevicepersonalization.services.data.user.UserDataDao;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationConfigServiceTest {
    @Rule
    public final ServiceTestRule serviceRule = new ServiceTestRule();
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private OnDevicePersonalizationConfigServiceDelegate mBinder;
    private UserPrivacyStatus mUserPrivacyStatus;
    private RawUserData mUserData;
    private UserDataCollector mUserDataCollector;
    private UserDataDao mUserDataDao;

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();
        PhFlagsTestUtil.enableOnDevicePersonalizationApis();
        PhFlagsTestUtil.disablePersonalizationStatusOverride();
        when(mContext.checkCallingOrSelfPermission(anyString()))
                        .thenReturn(PackageManager.PERMISSION_GRANTED);
        mBinder = new OnDevicePersonalizationConfigServiceDelegate(mContext);
        mUserPrivacyStatus = UserPrivacyStatus.getInstance();
        mUserPrivacyStatus.setPersonalizationStatusEnabled(false);
        mUserData = RawUserData.getInstance();
        TimeZone pstTime = TimeZone.getTimeZone("GMT-08:00");
        TimeZone.setDefault(pstTime);
        mUserDataCollector = UserDataCollector.getInstanceForTest(mContext);
        mUserDataDao = UserDataDao.getInstanceForTest(mContext);
    }

    @Test
    public void testDisableOnDevicePersonalizationApis() throws Exception {
        PhFlagsTestUtil.disableOnDevicePersonalizationApis();
        try {
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            mBinder.setPersonalizationStatus(true, null)
            );
        } finally {
            PhFlagsTestUtil.enableOnDevicePersonalizationApis();
        }
    }

    @Test
    public void testSetPersonalizationStatusNoCallingPermission() throws Exception {
        when(mContext.checkCallingOrSelfPermission(anyString()))
                        .thenReturn(PackageManager.PERMISSION_DENIED);
        assertThrows(SecurityException.class, () -> {
            mBinder.setPersonalizationStatus(true, null);
        });
    }

    @Test
    public void testSetPersonalizationStatusChanged() throws Exception {
        assertFalse(mUserPrivacyStatus.isPersonalizationStatusEnabled());
        populateUserData();
        assertNotEquals(0, mUserData.utcOffset);
        assertTrue(mUserDataCollector.isInitialized());

        CountDownLatch latch = new CountDownLatch(1);
        mBinder.setPersonalizationStatus(true,
                new IOnDevicePersonalizationConfigServiceCallback.Stub() {
                    @Override
                    public void onSuccess() {
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        latch.countDown();
                    }
                });

        latch.await();
        assertTrue(mUserPrivacyStatus.isPersonalizationStatusEnabled());

        assertEquals(0, mUserData.utcOffset);
        assertFalse(mUserDataCollector.isInitialized());
        Cursor appUsageCursor = mUserDataDao.readAppUsageInLastXDays(30);
        assertNotNull(appUsageCursor);
        assertEquals(0, appUsageCursor.getCount());
        Cursor locationCursor = mUserDataDao.readLocationInLastXDays(30);
        assertNotNull(locationCursor);
        assertEquals(0, locationCursor.getCount());
    }

    @Test
    public void testSetPersonalizationStatusIfCallbackMissing() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            mBinder.setPersonalizationStatus(true, null);
        });
    }

    @Test
    public void testSetPersonalizationStatusNoOps() throws Exception {
        mUserPrivacyStatus.setPersonalizationStatusEnabled(true);

        populateUserData();
        assertNotEquals(0, mUserData.utcOffset);
        int utcOffset = mUserData.utcOffset;
        assertTrue(mUserDataCollector.isInitialized());
        Cursor appUsageCursor = mUserDataDao.readAppUsageInLastXDays(30);
        Cursor locationCursor = mUserDataDao.readLocationInLastXDays(30);
        assertNotNull(appUsageCursor);
        assertNotNull(locationCursor);
        int appUsageCount = appUsageCursor.getCount();
        int locationCount = locationCursor.getCount();
        assertTrue(appUsageCount > 0);
        assertTrue(locationCount > 0);

        CountDownLatch latch = new CountDownLatch(1);
        mBinder.setPersonalizationStatus(true,
                new IOnDevicePersonalizationConfigServiceCallback.Stub() {
                    @Override
                    public void onSuccess() {
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        latch.countDown();
                    }
                });

        latch.await();

        assertTrue(mUserPrivacyStatus.isPersonalizationStatusEnabled());
        // Adult data should not be roll-back'ed
        assertEquals(utcOffset, mUserData.utcOffset);
        assertTrue(mUserDataCollector.isInitialized());
        Cursor newAppUsageCursor = mUserDataDao.readAppUsageInLastXDays(30);
        Cursor newLocationCursor = mUserDataDao.readLocationInLastXDays(30);
        assertNotNull(newAppUsageCursor);
        assertNotNull(newLocationCursor);
        assertEquals(appUsageCount, newAppUsageCursor.getCount());
        assertEquals(locationCount, newLocationCursor.getCount());
    }

    @Test
    public void testWithBoundService() throws TimeoutException {
        Intent serviceIntent = new Intent(mContext,
                OnDevicePersonalizationConfigServiceImpl.class);
        IBinder binder = serviceRule.bindService(serviceIntent);
        assertTrue(binder instanceof OnDevicePersonalizationConfigServiceDelegate);
    }

    @After
    public void tearDown() throws Exception {
        mUserDataCollector.clearUserData(mUserData);
        mUserDataCollector.clearMetadata();
        mUserDataCollector.clearDatabase();
    }

    private void populateUserData() {
        mUserDataCollector.updateUserData(mUserData);
        // Populate the database in case that no records are collected by UserDataCollector.
        long currentTimeMillis = System.currentTimeMillis();
        mUserDataDao.insertAppUsageStatsData("testApp", 0, currentTimeMillis, 0);
        mUserDataDao.insertLocationHistoryData(currentTimeMillis,
                "111.11111", "-222.22222", 1, true);
    }
}
