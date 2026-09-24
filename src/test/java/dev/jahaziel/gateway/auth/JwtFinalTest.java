/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.lang.reflect.Method;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Final gap tests for gateway token verification: null audience and malformed claims. */
class JwtFinalTest {
  private static final String ISSUER = "https://code.test";
  private static final String AUDIENCE = "jahaziel-code-server";

  private record Keys(KeyPair pair, RSAPublicKey pub) {}

  private static Keys keys() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    return new Keys(pair, (RSAPublicKey) pair.getPublic());
  }

  private static String sign(KeyPair pair, JWTClaimsSet claims) throws Exception {
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(pair.getPrivate()));
    return jwt.serialize();
  }

  private static JWTClaimsSet.Builder base(String sub, String wid) {
    Instant now = Instant.now();
    return new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .subject(sub)
        .claim("wid", wid)
        .expirationTime(Date.from(now.plus(Duration.ofHours(1))))
        .issueTime(Date.from(now));
  }

  @Test
  void rejectsMissingAudience() throws Exception {
    Keys keys = keys();
    String sub = UUID.randomUUID().toString();
    String wid = UUID.randomUUID().toString();
    String token = sign(keys.pair(), base(sub, wid).build());
    JwtVerifier verifier = new JwtVerifier(keys.pub(), ISSUER, AUDIENCE, Duration.ofHours(2));
    JwtRejectedException rejected =
        assertThrows(JwtRejectedException.class, () -> verifier.verify(token));
    assertEquals("wrong audience", rejected.getMessage());
  }

  @Test
  void rejectsWrongAudience() throws Exception {
    Keys keys = keys();
    String sub = UUID.randomUUID().toString();
    String wid = UUID.randomUUID().toString();
    String token = sign(keys.pair(), base(sub, wid).audience("someone-else").build());
    JwtVerifier verifier = new JwtVerifier(keys.pub(), ISSUER, AUDIENCE, Duration.ofHours(2));
    JwtRejectedException rejected =
        assertThrows(JwtRejectedException.class, () -> verifier.verify(token));
    assertEquals("wrong audience", rejected.getMessage());
  }

  @Test
  void rejectsMalformedSubjectClaim() throws Exception {
    JWTClaimsSet claims = mock(JWTClaimsSet.class);
    when(claims.getStringClaim("sub")).thenThrow(new java.text.ParseException("bad", 0));
    Method uuid = JwtVerifier.class.getDeclaredMethod("uuid", JWTClaimsSet.class, String.class);
    uuid.setAccessible(true);
    try {
      uuid.invoke(null, claims, "sub");
      throw new AssertionError("expected JwtRejectedException");
    } catch (java.lang.reflect.InvocationTargetException failure) {
      assertEquals("malformed claim sub", ((JwtRejectedException) failure.getCause()).getMessage());
    }
  }

  @Test
  void rejectsMissingSubjectClaim() throws Exception {
    JWTClaimsSet claims = mock(JWTClaimsSet.class);
    when(claims.getStringClaim("sub")).thenReturn(null);
    Method uuid = JwtVerifier.class.getDeclaredMethod("uuid", JWTClaimsSet.class, String.class);
    uuid.setAccessible(true);
    try {
      uuid.invoke(null, claims, "sub");
      throw new AssertionError("expected JwtRejectedException");
    } catch (java.lang.reflect.InvocationTargetException failure) {
      assertEquals("missing claim sub", ((JwtRejectedException) failure.getCause()).getMessage());
    }
  }

  @Test
  void acceptsWellFormedToken() throws Exception {
    Keys keys = keys();
    String sub = UUID.randomUUID().toString();
    String wid = UUID.randomUUID().toString();
    String token = sign(keys.pair(), base(sub, wid).audience(AUDIENCE).build());
    JwtVerifier verifier = new JwtVerifier(keys.pub(), ISSUER, AUDIENCE, Duration.ofHours(2));
    GatewaySession session = verifier.verify(token);
    assertEquals(UUID.fromString(sub), session.userId());
    assertEquals(UUID.fromString(wid), session.workspaceId());
    assertEquals(List.of(), session.scopes());
  }
}
