/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.passkey;

/** A pending WebAuthn ceremony, consumed once by the callback. */
public final class PasskeyPending {
    private final String challenge;
    private final String nonce;
    private final String redirectUri;
    private final long expiresAt;

    public PasskeyPending(String challenge, String nonce, String redirectUri, long expiresAt) {
        this.challenge = challenge;
        this.nonce = nonce;
        this.redirectUri = redirectUri;
        this.expiresAt = expiresAt;
    }

    public String challenge() {
        return challenge;
    }

    public String nonce() {
        return nonce;
    }

    public String redirectUri() {
        return redirectUri;
    }

    public long expiresAt() {
        return expiresAt;
    }
}
