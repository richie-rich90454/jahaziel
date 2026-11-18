/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Provider-neutral identity. The durable key is provider plus subject, never email. */
public final class AuthIdentity {
    private final String provider;
    private final String subject;
    private final String email;
    private final String displayName;
    private final boolean emailVerified;
    private final boolean mfaAssured;
    private final Map<String, String> claims;

    public AuthIdentity(
            String provider,
            String subject,
            String email,
            String displayName,
            boolean emailVerified,
            boolean mfaAssured,
            Map<String, String> claims) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.email = email == null ? "" : email;
        this.displayName = displayName == null ? "" : displayName;
        this.emailVerified = emailVerified;
        this.mfaAssured = mfaAssured;
        Objects.requireNonNull(claims, "claims");
        this.claims = Collections.unmodifiableMap(new HashMap<String, String>(claims));
    }

    public String provider() {
        return provider;
    }

    public String subject() {
        return subject;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public boolean emailVerified() {
        return emailVerified;
    }

    public boolean mfaAssured() {
        return mfaAssured;
    }

    public Map<String, String> claims() {
        return claims;
    }

    public String linkKey() {
        return provider + ":" + subject;
    }
}
