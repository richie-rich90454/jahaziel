/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.jahaziel.identity.api.JwtRejectedException;
import dev.jahaziel.identity.api.JwtSession;
import dev.jahaziel.identity.domain.UserId;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtServiceCoverageGapTest {
  @TempDir private Path tmp;

  private KeyPair keys() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    return g.generateKeyPair();
  }

  private JwtServiceImpl svc(KeyPair kp) {
    return new JwtServiceImpl(
        (RSAPrivateKey) kp.getPrivate(),
        (RSAPublicKey) kp.getPublic(),
        "https://code.example.com",
        "jahaziel-code-server");
  }

  @Test
  void issueSigningFailureThrowsState() throws Exception {
    KeyPair kp = keys();
    JwtServiceImpl s =
        new JwtServiceImpl(
            mock(RSAPrivateKey.class),
            (RSAPublicKey) kp.getPublic(),
            "https://code.example.com",
            "jahaziel-code-server");
    assertThrows(
        IllegalStateException.class,
        () -> s.issue(new UserId(UUID.randomUUID()), null, List.of(), Duration.ofMinutes(1)));
  }

  @Test
  void verifyRsaVerifierRejectsHmacToken() throws Exception {
    KeyPair kp = keys();
    JwtServiceImpl s = svc(kp);
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(UUID.randomUUID().toString())
            .audience("jahaziel-code-server")
            .expirationTime(new Date(System.currentTimeMillis() + 60000))
            .build();
    SignedJWT foreign = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    foreign.sign(new MACSigner(new byte[32]));
    assertThrows(JwtRejectedException.class, () -> s.verify(foreign.serialize()));
  }

  @Test
  void verifyMalformedClaimsThrows() throws Exception {
    KeyPair kp = keys();
    JwtServiceImpl s = svc(kp);
    String header =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.US_ASCII));
    String payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("not-json".getBytes(StandardCharsets.US_ASCII));
    String input = header + "." + payload;
    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(kp.getPrivate());
    signer.update(input.getBytes(StandardCharsets.US_ASCII));
    String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    assertThrows(JwtRejectedException.class, () -> s.verify(input + "." + signature));
  }

  @Test
  void verifyNonStringSubjectThrows() throws Exception {
    KeyPair kp = keys();
    JwtServiceImpl s = svc(kp);
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .audience("jahaziel-code-server")
            .expirationTime(new Date(System.currentTimeMillis() + 60000))
            .claim("sub", 12345)
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    assertThrows(JwtRejectedException.class, () -> s.verify(jwt.serialize()));
  }

  @Test
  void pemDecodeFailureThrowsMalformed() throws Exception {
    Path priv = tmp.resolve("priv.pem");
    Files.writeString(priv, "-----BEGIN PRIVATE KEY-----\nA\n-----END PRIVATE KEY-----\n");
    assertThrows(IllegalArgumentException.class, () -> JwtServiceImpl.loadPrivateKey(priv));
    Path pub = tmp.resolve("pub.pem");
    Files.writeString(pub, "-----BEGIN PUBLIC KEY-----\nA\n-----END PUBLIC KEY-----\n");
    assertThrows(IllegalArgumentException.class, () -> JwtServiceImpl.loadPublicKey(pub));
  }

  @Test
  void ctorRejectsNullAudience() throws Exception {
    KeyPair kp = keys();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JwtServiceImpl(
                (RSAPrivateKey) kp.getPrivate(), (RSAPublicKey) kp.getPublic(), "i", null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JwtServiceImpl(
                (RSAPrivateKey) kp.getPrivate(), (RSAPublicKey) kp.getPublic(), "i", ""));
  }

  @Test
  void verifyPastNotBeforePasses() throws Exception {
    KeyPair kp = keys();
    JwtServiceImpl s = svc(kp);
    UserId user = new UserId(UUID.randomUUID());
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(user.value().toString())
            .audience("jahaziel-code-server")
            .expirationTime(new Date(System.currentTimeMillis() + 600000))
            .notBeforeTime(new Date(System.currentTimeMillis() - 60000))
            .claim("scope", "a")
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    assertEquals(user, s.verify(jwt.serialize()).user());
  }

  @Test
  void verifyMissingScopeClaimYieldsEmpty() throws Exception {
    KeyPair kp = keys();
    JwtServiceImpl s = svc(kp);
    UserId user = new UserId(UUID.randomUUID());
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(user.value().toString())
            .audience("jahaziel-code-server")
            .expirationTime(new Date(System.currentTimeMillis() + 60000))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    JwtSession session = s.verify(jwt.serialize());
    assertTrue(session.scopes().isEmpty());
    assertTrue(session.workspaceId().isEmpty());
  }

  @Test
  void rejectedExceptionUserCtor() {
    UserId user = new UserId(UUID.randomUUID());
    JwtRejectedException failure = new JwtRejectedException(user, "stale");
    assertEquals(user, failure.userId());
    assertTrue(failure.getMessage().contains("stale"));
    assertEquals(null, new JwtRejectedException("bad").userId());
    assertEquals(null, new JwtRejectedException("bad", new RuntimeException()).userId());
  }
}
