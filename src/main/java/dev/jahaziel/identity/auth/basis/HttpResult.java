/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A small HTTP result for backchannel and resource-token checks. */
public final class HttpResult {
    private static final String INVALID_BODY =
            "{\"error\":\"invalid_token\",\"error_description\":\"Invalid access token\"}";

    private final int status;
    private final Map<String, String> headers;
    private final String body;

    private HttpResult(int status, Map<String, String> headers, String body) {
        this.status = status;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(headers));
        this.body = body;
    }

    /** The single standard rejection for revoked, expired, or malformed access tokens. */
    public static HttpResult invalidToken() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        headers.put("Content-Type", "application/json");
        return new HttpResult(401, headers, INVALID_BODY);
    }

    public static HttpResult accepted() {
        return new HttpResult(202, new LinkedHashMap<String, String>(), "");
    }

    public static HttpResult noContent() {
        return new HttpResult(204, new LinkedHashMap<String, String>(), "");
    }

    public static HttpResult ok() {
        return new HttpResult(200, new LinkedHashMap<String, String>(), "");
    }

    public static HttpResult badRequest() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", "application/json");
        return new HttpResult(400, headers, "{\"error\":\"invalid_request\"}");
    }

    public int status() {
        return status;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String body() {
        return body;
    }
}
