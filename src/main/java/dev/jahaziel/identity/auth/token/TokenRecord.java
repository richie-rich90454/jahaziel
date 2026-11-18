/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A stored token: only the SHA-256 hash is kept, never the plaintext token. */
public final class TokenRecord {
    private final String id;
    private final String hash;
    private final String subject;
    private final String email;
    private final String displayName;
    private final List<String> scopes;
    private final long expiresAt;
    private final boolean active;

    public TokenRecord(
            String id,
            String hash,
            String subject,
            String email,
            String displayName,
            List<String> scopes,
            long expiresAt,
            boolean active) {
        this.id = id;
        this.hash = hash;
        this.subject = subject;
        this.email = email;
        this.displayName = displayName;
        this.scopes = Collections.unmodifiableList(new ArrayList<String>(scopes));
        this.expiresAt = expiresAt;
        this.active = active;
    }

    public String id() {
        return id;
    }

    public String hash() {
        return hash;
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

    public List<String> scopes() {
        return scopes;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public boolean active() {
        return active;
    }

    public boolean hasScope(String scope) {
        for (String candidate : scopes) {
            if (candidate.equals(scope)) {
                return true;
            }
        }
        return false;
    }
}
