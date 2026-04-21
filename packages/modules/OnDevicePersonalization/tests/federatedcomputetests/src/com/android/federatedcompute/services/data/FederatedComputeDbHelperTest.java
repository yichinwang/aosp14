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

package com.android.federatedcompute.services.data;

import static com.android.federatedcompute.services.data.FederatedTraningTaskContract.FEDERATED_TRAINING_TASKS_TABLE;
import static com.android.federatedcompute.services.data.FederatedComputeEncryptionKeyContract.ENCRYPTION_KEY_TABLE;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public final class FederatedComputeDbHelperTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    @After
    public void cleanUp() throws Exception {
        FederatedComputeDbHelper.resetInstance();
    }

    @Test
    public void onCreate() {
        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(mContext);

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        assertThat(db).isNotNull();
        assertThat(DatabaseUtils.queryNumEntries(db, FEDERATED_TRAINING_TASKS_TABLE)).isEqualTo(0);
        assertThat(DatabaseUtils.queryNumEntries(db, ENCRYPTION_KEY_TABLE)).isEqualTo(0);

        // query number of tables
        ArrayList<String> tableNames = new ArrayList<String>();
        Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                tableNames.add(cursor.getString(cursor.getColumnIndex("name")));
                cursor.moveToNext();
            }
        }
        String[] expectedTables = {FEDERATED_TRAINING_TASKS_TABLE, ENCRYPTION_KEY_TABLE};
        // android metadata table also exists in the database
        assertThat(tableNames).containsAtLeastElementsIn(expectedTables);
    }
}
