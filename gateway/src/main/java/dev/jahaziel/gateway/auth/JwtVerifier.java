/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Verifies control-plane session tokens against the public key. Fails closed on any doubt. */
public final class JwtVerifier {
  private final JWSVerifier verifier;
  private final String issuer;
  private final String audience;
  private final Duration maxTtl;

  /**
   * Creates the verifier.
   *
   * @param publicKey the control plane JWT public key
   * @param issuer the expected issuer
   * @param audience the expected audience
   * @param maxTtl the longest accepted token lifetime
   */
  public JwtVerifier(RSAPublicKey publicKey, String issuer, String audience, Duration maxTtl) {
    Objects.requireNonNull(publicKey, "publicKey");
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException("issuer must not be blank");
    }
    if (audience == null || audience.isBlank()) {
      throw new IllegalArgumentException("audience must not be blank");
    }
    this.verifier = new RSASSAVerifier(publicKey);
    this.issuer = issuer;
    this.audience = audience;
    this.maxTtl = Objects.requireNonNull(maxTtl, "maxTtl");
  }

  /**
   * Verifies one token.
   *
   * @param token the compact token
   * @return the verified session
   * @throws JwtRejectedException when the token is invalid, expired, or misissued
   */
  public GatewaySession verify(String token) {
    SignedJWT jwt;
    try {
      jwt = SignedJWT.parse(token);
    } catch (ParseException failure) {
      throw new JwtRejectedException("malformed token", failure);
    }
    boolean signature;
    try {
      signature = jwt.verify(verifier);
    } catch (JOSEException failure) {
      throw new JwtRejectedException("signature check failed", failure);
    }
    if (!signature) {
      throw new JwtRejectedException("bad signature");
    }
    JWTClaimsSet claims = claimsOf(jwt);
    if (!issuer.equals(claims.getIssuer())) {
      throw new JwtRejectedException("wrong issuer");
    }
    List<String> audiences = claims.getAudience();
    if (audiences == null || !audiences.contains(audience)) {
      throw new JwtRejectedException("wrong audience");
    }
    Date expiry = claims.getExpirationTime();
    if (expiry == null || !expiry.after(new Date())) {
      throw new JwtRejectedException("expired or missing expiry");
    }
    Date notBefore = claims.getNotBeforeTime();
    if (notBefore != null && notBefore.after(new Date())) {
      throw new JwtRejectedException("not yet valid");
    }
    Date issuedAt = claims.getIssueTime();
    if (issuedAt != null && issuedAt.toInstant().plus(maxTtl).isBefore(new Date().toInstant())) {
      throw new JwtRejectedException("exceeds maximum lifetime");
    }
    return new GatewaySession(uuid(claims, "sub"), uuid(claims, "wid"), scopes(claims));
  }

  private static JWTClaimsSet claimsOf(SignedJWT jwt) {
    try {
      return jwt.getJWTClaimsSet();
    } catch (ParseException failure) {
      throw new JwtRejectedException("malformed claims", failure);
    }
  }

  private static List<String> scopes(JWTClaimsSet claims) {
    String scope;
    try {
      scope = claims.getStringClaim("scope");
    } catch (ParseException failure) {
      throw new JwtRejectedException("malformed scope", failure);
    }
    if (scope == null || scope.isBlank()) {
      return List.of();
    }
    return List.of(scope.split(" "));
  }

  private static UUID uuid(JWTClaimsSet claims, String name) {
    String value;
    try {
      value = claims.getStringClaim(name);
    } catch (ParseException failure) {
      throw new JwtRejectedException("malformed claim " + name, failure);
    }
    if (value == null) {
      throw new JwtRejectedException("missing claim " + name);
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException failure) {
      throw new JwtRejectedException("claim " + name + " is not a UUID");
    }
  }
}
