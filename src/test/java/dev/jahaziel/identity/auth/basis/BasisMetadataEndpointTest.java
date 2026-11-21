/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Discovery endpoints must stay on the configured issuer host and allowed scheme. */
final class BasisMetadataEndpointTest {
    @Test
    void acceptsHttpsEndpointsOnTheIssuerHostWithPorts() {
        String issuer = "https://idp.example.org";
        assertTrue(BasisMetadata.endpointAllowed(issuer, issuer));
        assertTrue(BasisMetadata.endpointAllowed(issuer, "https://idp.example.org:8443/token"));
        assertTrue(BasisMetadata.endpointAllowed(issuer, "https://IDP.example.org/jwks"));
    }

    @Test
    void rejectsCrossHostAndDowngradedEndpoints() {
        String issuer = "https://idp.example.org";
        assertFalse(BasisMetadata.endpointAllowed(issuer, "https://evil.example/token"));
        assertFalse(BasisMetadata.endpointAllowed(issuer, "http://idp.example.org/token"));
        assertFalse(BasisMetadata.endpointAllowed(issuer, "https://idp.example.org.evil/token"));
        assertFalse(BasisMetadata.endpointAllowed("http://idp.example.org", issuer));
    }

    @Test
    void allowsHttpOnlyForLoopbackDevelopment() {
        assertTrue(
                BasisMetadata.endpointAllowed(
                        "http://127.0.0.1:8080", "http://127.0.0.1:9090/token"));
        assertTrue(BasisMetadata.endpointAllowed("http://localhost:8080", "http://localhost/jwks"));
        assertFalse(
                BasisMetadata.endpointAllowed(
                        "http://127.0.0.1:8080", "http://evil.example/token"));
    }
}
