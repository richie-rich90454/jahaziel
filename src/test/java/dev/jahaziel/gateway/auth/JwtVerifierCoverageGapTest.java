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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtVerifierCoverageGapTest {
  @TempDir private Path tmp;

  private KeyPair keys() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    return g.generateKeyPair();
  }

  private JwtVerifier verifier(RSAPublicKey pub) {
    return new JwtVerifier(pub, "https://code.example.com", "aud", Duration.ofMinutes(15));
  }

  @Test
  void ctorRejectsNullIssuerAndAudience() throws Exception {
    KeyPair kp = keys();
    RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
    assertThrows(
        IllegalArgumentException.class,
        () -> new JwtVerifier(pub, null, "aud", Duration.ofMinutes(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new JwtVerifier(pub, "i", null, Duration.ofMinutes(1)));
  }

  @Test
  void malformedClaimsThrow() throws Exception {
    KeyPair kp = keys();
    JwtVerifier v = verifier((RSAPublicKey) kp.getPublic());
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
    assertThrows(JwtRejectedException.class, () -> v.verify(input + "." + signature));
  }

  @Test
  void nonStringSubjectThrows() throws Exception {
    KeyPair kp = keys();
    JwtVerifier v = verifier((RSAPublicKey) kp.getPublic());
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .audience("aud")
            .expirationTime(new Date(System.currentTimeMillis() + 60000))
            .claim("sub", 7)
            .claim("wid", UUID.randomUUID().toString())
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    assertThrows(JwtRejectedException.class, () -> v.verify(jwt.serialize()));
  }

  @Test
  void audienceVariantsThrowAndPass() throws Exception {
    KeyPair kp = keys();
    JwtVerifier v = verifier((RSAPublicKey) kp.getPublic());
    UUID sub = UUID.randomUUID();
    UUID wid = UUID.randomUUID();
    Date exp = new Date(System.currentTimeMillis() + 600000);
    JWTClaimsSet none =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(sub.toString())
            .expirationTime(exp)
            .claim("wid", wid.toString())
            .build();
    SignedJWT j0 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), none);
    j0.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    assertThrows(JwtRejectedException.class, () -> v.verify(j0.serialize()));
    JWTClaimsSet wrong =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(sub.toString())
            .audience("someone-else")
            .expirationTime(exp)
            .claim("wid", wid.toString())
            .build();
    SignedJWT j1 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), wrong);
    j1.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    assertThrows(JwtRejectedException.class, () -> v.verify(j1.serialize()));
  }

  @Test
  void pastNotBeforePasses() throws Exception {
    KeyPair kp = keys();
    JwtVerifier v = verifier((RSAPublicKey) kp.getPublic());
    UUID sub = UUID.randomUUID();
    UUID wid = UUID.randomUUID();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(sub.toString())
            .audience("aud")
            .expirationTime(new Date(System.currentTimeMillis() + 600000))
            .notBeforeTime(new Date(System.currentTimeMillis() - 60000))
            .issueTime(new Date())
            .claim("wid", wid.toString())
            .claim("scope", "a")
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    assertEquals(sub, v.verify(jwt.serialize()).userId());
  }

  @Test
  void keysMalformedPemThrows() throws Exception {
    Path bad = tmp.resolve("bad.pem");
    Files.writeString(bad, "-----BEGIN PUBLIC KEY-----\nA\n-----END PUBLIC KEY-----\n");
    assertThrows(IllegalArgumentException.class, () -> JwtKeys.loadPublicKey(bad));
    assertTrue(Files.exists(bad));
  }
}
