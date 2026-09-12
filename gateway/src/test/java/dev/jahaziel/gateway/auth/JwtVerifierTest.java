/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior of {@link JwtVerifier} with generated keys. */
class JwtVerifierTest {
  private RSAPublicKey publicKey;
  private RSAPrivateKey privateKey;
  private JwtVerifier verifier;
  private UUID userId;
  private UUID workspaceId;

  @BeforeEach
  void setUp() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    publicKey = (RSAPublicKey) pair.getPublic();
    privateKey = (RSAPrivateKey) pair.getPrivate();
    verifier = new JwtVerifier(publicKey, "https://code.jahaziel.dev", "jahaziel-code-server");
    userId = UUID.randomUUID();
    workspaceId = UUID.randomUUID();
  }

  @Test
  void acceptsWellFormedToken() throws Exception {
    GatewaySession session = verifier.verify(token(minutes(10), "https://code.jahaziel.dev"));
    assertEquals(userId, session.userId());
    assertEquals(workspaceId, session.workspaceId());
    assertTrue(session.scopes().contains("workspace:read"));
  }

  @Test
  void rejectsExpiredWrongIssuerAndGarbage() throws Exception {
    assertThrows(
        JwtRejectedException.class,
        () -> verifier.verify(token(minutes(-10), "https://code.jahaziel.dev")));
    assertThrows(
        JwtRejectedException.class, () -> verifier.verify(token(minutes(10), "https://evil.dev")));
    assertThrows(JwtRejectedException.class, () -> verifier.verify("not-a-token"));
    assertThrows(
        JwtRejectedException.class,
        () -> verifier.verify(token(minutes(10), "https://code.jahaziel.dev") + "x"));
  }

  private String token(Date expiry, String issuer) throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(userId.toString())
            .audience("jahaziel-code-server")
            .expirationTime(expiry)
            .issueTime(new Date())
            .claim("wid", workspaceId.toString())
            .claim("scope", "workspace:read workspace:write")
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(privateKey));
    return jwt.serialize();
  }

  private static Date minutes(int offset) {
    return new Date(System.currentTimeMillis() + offset * 60L * 1000L);
  }
}
