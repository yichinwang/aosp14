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

package com.android.ondevicepersonalization.services.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.EventsContract;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.QueriesContract;
import com.android.ondevicepersonalization.services.data.events.Query;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.util.OnDevicePersonalizationFlatbufferUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class AppRequestFlowTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private OnDevicePersonalizationDbHelper mDbHelper;
    private UserPrivacyStatus mUserPrivacyStatus = UserPrivacyStatus.getInstance();

    private String mRenderedContent;
    private boolean mGenerateHtmlCalled;
    private String mGeneratedHtml;
    private boolean mDisplayHtmlCalled;
    private boolean mCallbackSuccess;
    private boolean mCallbackError;
    private int mCallbackErrorCode;

    @Before
    public void setup() {
        mDbHelper = OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        ArrayList<ContentValues> rows = new ArrayList<>();
        ContentValues row1 = new ContentValues();
        row1.put("a", 1);
        rows.add(row1);
        ContentValues row2 = new ContentValues();
        row2.put("b", 2);
        rows.add(row2);
        byte[] queryDataBytes = OnDevicePersonalizationFlatbufferUtils.createQueryData(
                "com.example.test", "AABBCCDD", rows);
        EventsDao.getInstanceForTest(mContext).insertQuery(
                new Query.Builder().setServicePackageName(mContext.getPackageName()).setQueryData(
                        queryDataBytes).build());
        EventsDao.getInstanceForTest(mContext);
        PhFlagsTestUtil.disablePersonalizationStatusOverride();
        mUserPrivacyStatus.setPersonalizationStatusEnabled(true);
    }

    @After
    public void cleanup() {
        mDbHelper.getWritableDatabase().close();
        mDbHelper.getReadableDatabase().close();
        mDbHelper.close();
    }

    @Test
    public void testRunAppRequestFlow() throws Exception {
        AppRequestFlow appRequestFlow = new AppRequestFlow(
                "abc",
                new ComponentName(mContext.getPackageName(), "com.test.TestPersonalizationService"),
                PersistableBundle.EMPTY,
                new TestCallback(), mContext, 100L, new TestInjector());
        appRequestFlow.run();
        mLatch.await();
        assertTrue(mCallbackSuccess);
        assertEquals(2,
                mDbHelper.getReadableDatabase().query(QueriesContract.QueriesEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
        assertEquals(1,
                mDbHelper.getReadableDatabase().query(EventsContract.EventsEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
    }

    @Test
    public void testRunAppRequestFlowPersonalizationDisabled() throws Exception {
        mUserPrivacyStatus.setPersonalizationStatusEnabled(false);
        AppRequestFlow appRequestFlow = new AppRequestFlow(
                "abc",
                new ComponentName(mContext.getPackageName(), "com.test.TestPersonalizationService"),
                PersistableBundle.EMPTY,
                new TestCallback(), mContext, 100L, new TestInjector());
        appRequestFlow.run();
        mLatch.await();
        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_PERSONALIZATION_DISABLED, mCallbackErrorCode);
    }

    class TestCallback extends IExecuteCallback.Stub {
        @Override
        public void onSuccess(List<String> tokens) {
            mCallbackSuccess = true;
            mLatch.countDown();
        }

        @Override
        public void onError(int errorCode) {
            mCallbackError = true;
            mCallbackErrorCode = errorCode;
            mLatch.countDown();
        }
    }

    class TestInjector extends AppRequestFlow.Injector {
        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }
    }
}
