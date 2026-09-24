/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.jahaziel.identity.api.JwtRejectedException;
import dev.jahaziel.identity.api.JwtSession;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.identity.internal.JwtServiceImpl;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IdentityJwtExtraCoverageTest {
  @TempDir private Path tmp;

  private JwtServiceImpl svc(KeyPair kp) {
    return new JwtServiceImpl(
        (RSAPrivateKey) kp.getPrivate(),
        (RSAPublicKey) kp.getPublic(),
        "https://code.example.com",
        "jahaziel-code-server");
  }

  private KeyPair keys() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    return g.generateKeyPair();
  }

  @Test
  void ctorAndIssueGuards() throws Exception {
    KeyPair kp = keys();
    assertThrows(
        NullPointerException.class,
        () -> new JwtServiceImpl(null, (RSAPublicKey) kp.getPublic(), "i", "a"));
    assertThrows(
        NullPointerException.class,
        () -> new JwtServiceImpl((RSAPrivateKey) kp.getPrivate(), null, "i", "a"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JwtServiceImpl(
                (RSAPrivateKey) kp.getPrivate(), (RSAPublicKey) kp.getPublic(), "", "a"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JwtServiceImpl(
                (RSAPrivateKey) kp.getPrivate(), (RSAPublicKey) kp.getPublic(), null, "a"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JwtServiceImpl(
                (RSAPrivateKey) kp.getPrivate(), (RSAPublicKey) kp.getPublic(), "i", " "));
    JwtServiceImpl s = svc(kp);
    assertThrows(
        NullPointerException.class, () -> s.issue(null, null, List.of(), Duration.ofMinutes(1)));
    assertThrows(
        NullPointerException.class,
        () -> s.issue(new UserId(UUID.randomUUID()), null, null, Duration.ofMinutes(1)));
    assertThrows(
        NullPointerException.class,
        () -> s.issue(new UserId(UUID.randomUUID()), null, List.of(), null));
  }

  @Test
  void verifyBranches() throws Exception {
    KeyPair kp = keys();
    KeyPair other = keys();
    JwtServiceImpl s = svc(kp);
    UserId u = new UserId(UUID.randomUUID());
    String good = s.issue(u, UUID.randomUUID(), List.of("a b"), Duration.ofMinutes(5));
    JwtSession sess = s.verify(good);
    assertEquals(u, sess.user());
    assertThrows(JwtRejectedException.class, () -> s.verify("not-a-token"));
    String tampered = good + "x";
    assertThrows(JwtRejectedException.class, () -> s.verify(tampered));
    JwtServiceImpl wrongKey =
        new JwtServiceImpl(
            (RSAPrivateKey) other.getPrivate(),
            (RSAPublicKey) other.getPublic(),
            "https://code.example.com",
            "jahaziel-code-server");
    String foreign = wrongKey.issue(u, null, List.of(), Duration.ofMinutes(5));
    assertThrows(JwtRejectedException.class, () -> s.verify(foreign));
    JWTClaimsSet wrongIss =
        new JWTClaimsSet.Builder()
            .issuer("https://evil")
            .subject(u.value().toString())
            .audience("jahaziel-code-server")
            .expirationTime(new Date(System.currentTimeMillis() + 60000))
            .build();
    SignedJWT j = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), wrongIss);
    j.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    assertThrows(JwtRejectedException.class, () -> s.verify(j.serialize()));
    JWTClaimsSet wrongAud =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(u.value().toString())
            .audience("other")
            .expirationTime(new Date(System.currentTimeMillis() + 60000))
            .build();
    SignedJWT j2 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), wrongAud);
    j2.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    assertThrows(JwtRejectedException.class, () -> s.verify(j2.serialize()));
    JWTClaimsSet noAud =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(u.value().toString())
            .expirationTime(new Date(System.currentTimeMillis() + 60000))
            .build();
    SignedJWT j3 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), noAud);
    j3.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    assertThrows(JwtRejectedException.class, () -> s.verify(j3.serialize()));
    JWTClaimsSet noExp =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(u.value().toString())
            .audience("jahaziel-code-server")
            .build();
    SignedJWT j4 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), noExp);
    j4.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    assertThrows(JwtRejectedException.class, () -> s.verify(j4.serialize()));
    JWTClaimsSet nbf =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(u.value().toString())
            .audience("jahaziel-code-server")
            .expirationTime(new Date(System.currentTimeMillis() + 600000))
            .notBeforeTime(new Date(System.currentTimeMillis() + 600000))
            .build();
    SignedJWT j5 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), nbf);
    j5.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    assertThrows(JwtRejectedException.class, () -> s.verify(j5.serialize()));
    assertTrue(s.verify(s.issue(u, null, List.of(), Duration.ofMinutes(5))).scopes().isEmpty());
    JWTClaimsSet badWid =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(u.value().toString())
            .audience("jahaziel-code-server")
            .expirationTime(new Date(System.currentTimeMillis() + 60000))
            .claim("wid", "nope")
            .build();
    SignedJWT j6 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), badWid);
    j6.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    assertThrows(JwtRejectedException.class, () -> s.verify(j6.serialize()));
    JWTClaimsSet badSub =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject("nope")
            .audience("jahaziel-code-server")
            .expirationTime(new Date(System.currentTimeMillis() + 60000))
            .build();
    SignedJWT j7 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), badSub);
    j7.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    assertThrows(JwtRejectedException.class, () -> s.verify(j7.serialize()));
    JWTClaimsSet noSub =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .audience("jahaziel-code-server")
            .expirationTime(new Date(System.currentTimeMillis() + 60000))
            .build();
    SignedJWT j8 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), noSub);
    j8.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    assertThrows(JwtRejectedException.class, () -> s.verify(j8.serialize()));
    JWTClaimsSet scopeList =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(u.value().toString())
            .audience("jahaziel-code-server")
            .expirationTime(new Date(System.currentTimeMillis() + 60000))
            .claim("scope", List.of("a"))
            .build();
    SignedJWT j9 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), scopeList);
    j9.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
    assertThrows(JwtRejectedException.class, () -> s.verify(j9.serialize()));
    String withWid = s.issue(u, UUID.randomUUID(), List.of(), Duration.ofMinutes(5));
    assertTrue(s.verify(withWid).workspaceId().isPresent());
  }

  @Test
  void keyLoadingBranches() throws Exception {
    KeyPair kp = keys();
    RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();
    RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
    String privPem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(priv.getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
    Path pf = tmp.resolve("priv.pem");
    Files.writeString(pf, privPem, StandardCharsets.UTF_8);
    assertEquals(priv, JwtServiceImpl.loadPrivateKey(pf));
    String pubPem =
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(pub.getEncoded())
            + "\n-----END PUBLIC KEY-----\n";
    Path qf = tmp.resolve("pub.pem");
    Files.writeString(qf, pubPem, StandardCharsets.UTF_8);
    assertEquals(pub, JwtServiceImpl.loadPublicKey(qf));
    assertThrows(
        IllegalArgumentException.class, () -> JwtServiceImpl.loadPrivateKey(tmp.resolve("no.pem")));
    assertThrows(
        IllegalArgumentException.class, () -> JwtServiceImpl.loadPublicKey(tmp.resolve("no.pem")));
    assertThrows(IllegalArgumentException.class, () -> JwtServiceImpl.loadPrivateKey(tmp));
    Path bad64 = tmp.resolve("bad.pem");
    Files.writeString(bad64, "-----BEGIN PRIVATE KEY-----\n!!!\n-----END PRIVATE KEY-----\n");
    assertThrows(IllegalArgumentException.class, () -> JwtServiceImpl.loadPrivateKey(bad64));
    Path badPub64 = tmp.resolve("badpub.pem");
    Files.writeString(badPub64, "-----BEGIN PUBLIC KEY-----\n!!!\n-----END PUBLIC KEY-----\n");
    assertThrows(IllegalArgumentException.class, () -> JwtServiceImpl.loadPublicKey(badPub64));
    Path notKey = tmp.resolve("notkey.pem");
    Files.writeString(
        notKey,
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(new byte[] {1, 2, 3})
            + "\n-----END PRIVATE KEY-----\n");
    assertThrows(IllegalArgumentException.class, () -> JwtServiceImpl.loadPrivateKey(notKey));
    Path notPub = tmp.resolve("notpub.pem");
    Files.writeString(
        notPub,
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(new byte[] {1, 2, 3})
            + "\n-----END PUBLIC KEY-----\n");
    assertThrows(IllegalArgumentException.class, () -> JwtServiceImpl.loadPublicKey(notPub));
  }
}
