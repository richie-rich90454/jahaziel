/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.jahaziel.identity.api.JwtRejectedException;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.JwtSession;
import dev.jahaziel.identity.domain.UserId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** RS256 session tokens. Keys load from PKCS8 and X.509 PEM files. */
public final class JwtServiceImpl implements JwtService {
  private final RSAPrivateKey privateKey;
  private final RSAPublicKey publicKey;
  private final String issuer;
  private final String audience;

  /**
   * Creates the service.
   *
   * @param privateKey the signing key
   * @param publicKey the verification key
   * @param issuer the token issuer
   * @param audience the token audience
   */
  public JwtServiceImpl(
      RSAPrivateKey privateKey, RSAPublicKey publicKey, String issuer, String audience) {
    this.privateKey = Objects.requireNonNull(privateKey, "privateKey");
    this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException("issuer must not be blank");
    }
    if (audience == null || audience.isBlank()) {
      throw new IllegalArgumentException("audience must not be blank");
    }
    this.issuer = issuer;
    this.audience = audience;
  }

  @Override
  public String issue(UserId user, UUID workspaceId, List<String> scopes, Duration ttl) {
    Objects.requireNonNull(user, "user");
    Objects.requireNonNull(scopes, "scopes");
    Objects.requireNonNull(ttl, "ttl");
    Instant now = Instant.now();
    JWTClaimsSet.Builder claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(user.value().toString())
            .audience(audience)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(ttl)))
            .jwtID(UUID.randomUUID().toString())
            .claim("scope", String.join(" ", scopes));
    if (workspaceId != null) {
      claims.claim("wid", workspaceId.toString());
    }
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
    try {
      jwt.sign(new RSASSASigner(privateKey));
    } catch (JOSEException failure) {
      throw new IllegalStateException("signing failed", failure);
    }
    return jwt.serialize();
  }

  @Override
  public JwtSession verify(String token) {
    SignedJWT jwt;
    try {
      jwt = SignedJWT.parse(token);
    } catch (ParseException failure) {
      throw new JwtRejectedException("malformed token", failure);
    }
    try {
      if (!jwt.verify(new RSASSAVerifier(publicKey))) {
        throw new JwtRejectedException("bad signature");
      }
    } catch (JOSEException failure) {
      throw new JwtRejectedException("signature check failed", failure);
    }
    JWTClaimsSet claims = claimsOf(jwt);
    if (!issuer.equals(claims.getIssuer())) {
      throw new JwtRejectedException("wrong issuer");
    }
    List<String> audiences = claims.getAudience();
    // getAudience never returns null (absent audiences decode to an empty list).
    if (!audiences.contains(audience)) {
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
    UserId user = new UserId(uuid(claims, "sub"));
    Optional<UUID> workspace = Optional.empty();
    try {
      String wid = claims.getStringClaim("wid");
      if (wid != null) {
        workspace = Optional.of(UUID.fromString(wid));
      }
    } catch (ParseException | IllegalArgumentException failure) {
      throw new JwtRejectedException("malformed wid claim");
    }
    return new JwtSession(user, workspace, scopes(claims), expiry.toInstant());
  }

  /**
   * Loads a PKCS8 private key from a PEM file.
   *
   * @param path the key path
   * @return the private key
   */
  public static RSAPrivateKey loadPrivateKey(Path path) {
    byte[] encoded = decodePem(read(path), "PRIVATE KEY");
    try {
      return (RSAPrivateKey)
          KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    } catch (Exception failure) {
      throw new IllegalArgumentException("invalid private key: " + path, failure);
    }
  }

  /**
   * Loads an X.509 public key from a PEM file.
   *
   * @param path the key path
   * @return the public key
   */
  public static RSAPublicKey loadPublicKey(Path path) {
    byte[] encoded = decodePem(read(path), "PUBLIC KEY");
    try {
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
    } catch (Exception failure) {
      throw new IllegalArgumentException("invalid public key: " + path, failure);
    }
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (Exception failure) {
      throw new IllegalArgumentException("cannot read key: " + path, failure);
    }
  }

  private static byte[] decodePem(String pem, String kind) {
    String stripped =
        pem.replace("-----BEGIN " + kind + "-----", "").replace("-----END " + kind + "-----", "");
    try {
      return Base64.getMimeDecoder().decode(stripped);
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("malformed PEM block: " + kind, failure);
    }
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
