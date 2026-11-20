/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal.devconnect;

import java.util.Objects;

/**
 * The parts of the IdP discovery document we use.
 *
 * @param issuer the issuer identifier the IdP reports
 * @param authorizationEndpoint where the browser starts login
 * @param tokenEndpoint where the authorization code is exchanged
 * @param userinfoEndpoint where identity is read from, after the ID token proves login
 * @param jwksUri where the ID token signing keys are published
 */
public final class DiscoveryDocument {
    private final String issuer;
    private final String authorizationEndpoint;
    private final String tokenEndpoint;
    private final String userinfoEndpoint;
    private final String jwksUri;

    /** Creates a discovery document. */
    public DiscoveryDocument(
            String issuer,
            String authorizationEndpoint,
            String tokenEndpoint,
            String userinfoEndpoint,
            String jwksUri) {
        this.issuer = issuer;
        this.authorizationEndpoint = authorizationEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.userinfoEndpoint = userinfoEndpoint;
        this.jwksUri = jwksUri;
    }

    /** Returns the issuer identifier. */
    public String issuer() {
        return issuer;
    }

    public String getIssuer() {
        return issuer;
    }

    /** Returns the authorization endpoint. */
    public String authorizationEndpoint() {
        return authorizationEndpoint;
    }

    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    /** Returns the token endpoint. */
    public String tokenEndpoint() {
        return tokenEndpoint;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    /** Returns the userinfo endpoint. */
    public String userinfoEndpoint() {
        return userinfoEndpoint;
    }

    public String getUserinfoEndpoint() {
        return userinfoEndpoint;
    }

    /** Returns the JWKS URI. */
    public String jwksUri() {
        return jwksUri;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    /** Compares by class and all fields. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DiscoveryDocument that = (DiscoveryDocument) o;
        return Objects.equals(issuer, that.issuer)
                && Objects.equals(authorizationEndpoint, that.authorizationEndpoint)
                && Objects.equals(tokenEndpoint, that.tokenEndpoint)
                && Objects.equals(userinfoEndpoint, that.userinfoEndpoint)
                && Objects.equals(jwksUri, that.jwksUri);
    }

    /** Returns the hash code derived from all fields. */
    @Override
    public int hashCode() {
        return Objects.hash(
                issuer, authorizationEndpoint, tokenEndpoint, userinfoEndpoint, jwksUri);
    }

    /** Returns a readable representation. */
    @Override
    public String toString() {
        return "DiscoveryDocument[issuer="
                + issuer
                + ", authorizationEndpoint="
                + authorizationEndpoint
                + ", tokenEndpoint="
                + tokenEndpoint
                + ", userinfoEndpoint="
                + userinfoEndpoint
                + ", jwksUri="
                + jwksUri
                + "]";
    }
}
