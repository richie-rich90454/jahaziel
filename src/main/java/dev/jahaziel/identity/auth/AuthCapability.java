/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth;

/** Capability names as plain constants, not an enum. */
public final class AuthCapability {
    public static final String OIDC = "oidc";
    public static final String OAUTH2 = "oauth2";
    public static final String MFA_ASSURANCE = "mfa-assurance";
    public static final String REFRESH = "refresh";
    public static final String BACKCHANNEL = "backchannel";
    public static final String PASSWORD = "password";
    public static final String TOTP = "totp";
    public static final String PASSKEY = "passkey";
    public static final String API_TOKEN = "api-token";

    private AuthCapability() {
    }
}
