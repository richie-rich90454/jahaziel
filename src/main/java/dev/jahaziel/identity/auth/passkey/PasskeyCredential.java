/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.passkey;

/** A registered credential. The public key bytes are a COSE_Key, never a private key. */
public final class PasskeyCredential {
    private final String credentialId;
    private final byte[] cosePublicKey;
    private final long signCount;
    private final String rpId;

    public PasskeyCredential(
            String credentialId, byte[] cosePublicKey, long signCount, String rpId) {
        this.credentialId = credentialId;
        this.cosePublicKey = cosePublicKey;
        this.signCount = signCount;
        this.rpId = rpId;
    }

    public String credentialId() {
        return credentialId;
    }

    public byte[] cosePublicKey() {
        return cosePublicKey.clone();
    }

    public long signCount() {
        return signCount;
    }

    public String rpId() {
        return rpId;
    }
}
