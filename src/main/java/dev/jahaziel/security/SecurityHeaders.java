/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import java.util.HashMap;

/**
 * Security header constants and a builder. The workbench needs no inline scripts and no eval, so
 * neither is permitted by the content security policy.
 */
public final class SecurityHeaders {

    public static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    public static final String CSP =
            "default-src 'self'; script-src 'self'; style-src 'self'; "
                    + "img-src 'self' data:; font-src 'self'; connect-src 'self'; "
                    + "object-src 'none'; base-uri 'none'; frame-ancestors 'none'; "
                    + "form-action 'self'";

    public static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    public static final String NOSNIFF = "nosniff";

    public static final String REFERRER_POLICY = "Referrer-Policy";
    public static final String NO_REFERRER = "no-referrer";

    public static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    public static final String HSTS = "max-age=31536000; includeSubDomains; preload";

    public static final String X_FRAME_OPTIONS = "X-Frame-Options";
    public static final String DENY = "DENY";

    public static final String SET_COOKIE = "Set-Cookie";
    public static final String COOKIE_ATTRIBUTES = "Path=/; HttpOnly; Secure; SameSite=Strict";

    private SecurityHeaders() {}

    /** Returns the default header set. */
    public static HashMap<String, String> defaults() {
        return new Builder().build();
    }

    /** Accumulates headers; callers may add endpoint-specific ones before building. */
    public static final class Builder {
        private final HashMap<String, String> values = new HashMap<String, String>();

        /** Starts from the mandatory workbench defaults. */
        public Builder() {
            values.put(CONTENT_SECURITY_POLICY, CSP);
            values.put(X_CONTENT_TYPE_OPTIONS, NOSNIFF);
            values.put(REFERRER_POLICY, NO_REFERRER);
            values.put(STRICT_TRANSPORT_SECURITY, HSTS);
            values.put(X_FRAME_OPTIONS, DENY);
        }

        /** Adds or overrides a header. */
        public Builder header(String name, String value) {
            if (name == null || name.isEmpty() || value == null) {
                throw new IllegalArgumentException("header name and value are required");
            }
            values.put(name, value);
            return this;
        }

        /** Adds a cookie with all secure attributes set. */
        public Builder cookie(String name, String value) {
            if (name == null || name.isEmpty() || value == null) {
                throw new IllegalArgumentException("cookie name and value are required");
            }
            values.put(SET_COOKIE, name + "=" + value + "; " + COOKIE_ATTRIBUTES);
            return this;
        }

        /** Returns an immutable-size copy of the accumulated headers. */
        public HashMap<String, String> build() {
            return new HashMap<String, String>(values);
        }
    }
}
