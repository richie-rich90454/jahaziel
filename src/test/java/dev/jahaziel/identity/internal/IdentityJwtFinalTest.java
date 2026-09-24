/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.jahaziel.identity.api.JwtRejectedException;
import dev.jahaziel.identity.domain.UserId;
import java.lang.reflect.Method;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Final gap tests for session tokens: null audience and malformed subject claim. */
class IdentityJwtFinalTest {
  private static final String ISSUER = "https://id.test";
  private static final String AUDIENCE = "jahaziel-test";

  private record Keys(RSAPrivateKey priv, RSAPublicKey pub) {}

  private static Keys keys() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    return new Keys((RSAPrivateKey) pair.getPrivate(), (RSAPublicKey) pair.getPublic());
  }

  private static String sign(Keys keys, JWTClaimsSet claims) throws Exception {
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(keys.priv()));
    return jwt.serialize();
  }

  @Test
  void rejectsMissingAudience() throws Exception {
    Keys keys = keys();
    Instant now = Instant.now();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject(UUID.randomUUID().toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))))
            .build();
    JwtServiceImpl svc = new JwtServiceImpl(keys.priv(), keys.pub(), ISSUER, AUDIENCE);
    JwtRejectedException rejected =
        assertThrows(JwtRejectedException.class, () -> svc.verify(sign(keys, claims)));
    assertEquals("wrong audience", rejected.getMessage());
  }

  @Test
  void rejectsWrongAudience() throws Exception {
    Keys keys = keys();
    Instant now = Instant.now();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .audience("someone-else")
            .subject(UUID.randomUUID().toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))))
            .build();
    JwtServiceImpl svc = new JwtServiceImpl(keys.priv(), keys.pub(), ISSUER, AUDIENCE);
    JwtRejectedException rejected =
        assertThrows(JwtRejectedException.class, () -> svc.verify(sign(keys, claims)));
    assertEquals("wrong audience", rejected.getMessage());
  }

  @Test
  void rejectsMalformedSubjectClaim() throws Exception {
    JWTClaimsSet claims = mock(JWTClaimsSet.class);
    when(claims.getStringClaim("sub")).thenThrow(new java.text.ParseException("bad", 0));
    Method uuid = JwtServiceImpl.class.getDeclaredMethod("uuid", JWTClaimsSet.class, String.class);
    uuid.setAccessible(true);
    try {
      uuid.invoke(null, claims, "sub");
      throw new AssertionError("expected JwtRejectedException");
    } catch (java.lang.reflect.InvocationTargetException failure) {
      assertEquals("malformed claim sub", ((JwtRejectedException) failure.getCause()).getMessage());
    }
  }

  @Test
  void roundTripThroughIssue() throws Exception {
    Keys keys = keys();
    JwtServiceImpl svc = new JwtServiceImpl(keys.priv(), keys.pub(), ISSUER, AUDIENCE);
    UserId user = new UserId(UUID.randomUUID());
    String token = svc.issue(user, null, List.of("e2e"), Duration.ofMinutes(5));
    assertEquals(user, svc.verify(token).user());
  }
}
