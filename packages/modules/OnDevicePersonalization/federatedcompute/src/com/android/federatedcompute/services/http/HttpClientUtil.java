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

package com.android.federatedcompute.services.http;

import com.android.federatedcompute.internal.util.LogUtil;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Utility class containing http related variable e.g. headers, method. */
public final class HttpClientUtil {
    private static final String TAG = HttpClientUtil.class.getSimpleName();
    public static final String CONTENT_ENCODING_HDR = "Content-Encoding";

    public static final String ACCEPT_ENCODING_HDR = "Accept-Encoding";
    public static final String CONTENT_LENGTH_HDR = "Content-Length";
    public static final String GZIP_ENCODING_HDR = "gzip";
    public static final String CONTENT_TYPE_HDR = "Content-Type";
    public static final String PROTOBUF_CONTENT_TYPE = "application/x-protobuf";
    public static final String OCTET_STREAM = "application/octet-stream";
    public static final ImmutableSet<Integer> HTTP_OK_STATUS = ImmutableSet.of(200, 201);
    public static final String ODP_IDEMPOTENCY_KEY = "odp-idempotency-key";
    public static final int DEFAULT_BUFFER_SIZE = 1024;
    public static final byte[] EMPTY_BODY = new byte[0];

    /** The supported http methods. */
    public enum HttpMethod {
        GET,
        POST,
        PUT,
    }

    /** Compresses the input data using Gzip. */
    public static byte[] compressWithGzip(byte[] uncompressedData) {
        try (ByteString.Output outputStream = ByteString.newOutput(uncompressedData.length);
                GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(uncompressedData);
            gzipOutputStream.finish();
            return outputStream.toByteString().toByteArray();
        } catch (IOException e) {
            LogUtil.e(TAG, "Failed to compress using Gzip");
            throw new IllegalStateException("Failed to compress using Gzip", e);
        }
    }

    /** Uncompresses the input data using Gzip. */
    public static byte[] uncompressWithGzip(byte[] data) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                GZIPInputStream gzip = new GZIPInputStream(inputStream);
                ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            int length;
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            while ((length = gzip.read(buffer, 0, DEFAULT_BUFFER_SIZE)) > 0) {
                result.write(buffer, 0, length);
            }
            return result.toByteArray();
        } catch (Exception e) {
            LogUtil.e(TAG, "Failed to decompress the data.", e);
            throw new IllegalStateException("Failed to unscompress using Gzip", e);
        }
    }

    private HttpClientUtil() {}
}
