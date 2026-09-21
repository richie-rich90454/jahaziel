/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal.devconnect;

/**
 * The parts of the IdP discovery document we use.
 *
 * @param issuer the issuer identifier the IdP reports
 * @param authorizationEndpoint where the browser starts login
 * @param tokenEndpoint where the authorization code is exchanged
 * @param userinfoEndpoint where identity is read from, after the ID token proves login
 * @param jwksUri where the ID token signing keys are published
 */
public record DiscoveryDocument(
    String issuer,
    String authorizationEndpoint,
    String tokenEndpoint,
    String userinfoEndpoint,
    String jwksUri) {}
