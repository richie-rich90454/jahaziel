/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.jahaziel.gateway.auth.GatewaySession;
import dev.jahaziel.gateway.auth.JwtKeys;
import dev.jahaziel.gateway.auth.JwtRejectedException;
import dev.jahaziel.gateway.auth.JwtVerifier;
import dev.jahaziel.gateway.proxy.GatewayServer;
import dev.jahaziel.gateway.proxy.LoopbackTargetResolver;
import dev.jahaziel.gateway.routing.HostParser;
import dev.jahaziel.gateway.routing.ParsedHost;
import dev.jahaziel.gateway.routing.WorkspaceDirectory;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GatewayExtraCoverageTest {
  @TempDir private Path tmp;

  private JwtVerifier verifier(RSAPublicKey pub) {
    return new JwtVerifier(pub, "https://code.example.com", "aud", Duration.ofMinutes(15));
  }

  private String sign(
      RSAPrivateKey priv,
      UUID sub,
      UUID wid,
      Date exp,
      Date nbf,
      Date iat,
      String scope,
      String aud,
      String iss)
      throws Exception {
    JWTClaimsSet.Builder b = new JWTClaimsSet.Builder().subject(sub.toString()).expirationTime(exp);
    if (iss != null) b.issuer(iss);
    if (aud != null) b.audience(aud);
    if (nbf != null) b.notBeforeTime(nbf);
    if (iat != null) b.issueTime(iat);
    if (wid != null) b.claim("wid", wid.toString());
    if (scope != null) b.claim("scope", scope);
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), b.build());
    jwt.sign(new RSASSASigner(priv));
    return jwt.serialize();
  }

  @Test
  void verifierBranches() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    KeyPair kp = g.generateKeyPair();
    RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
    RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();
    KeyPair other = g.generateKeyPair();
    UUID sub = UUID.randomUUID();
    UUID wid = UUID.randomUUID();
    Date exp = new Date(System.currentTimeMillis() + 600000);
    JwtVerifier v = verifier(pub);
    GatewaySession s =
        v.verify(
            sign(priv, sub, wid, exp, null, new Date(), "a b", "aud", "https://code.example.com"));
    assertEquals(sub, s.userId());
    assertThrows(JwtRejectedException.class, () -> v.verify("garbage"));
    assertThrows(
        JwtRejectedException.class,
        () -> v.verify(sign(priv, sub, wid, exp, null, new Date(), "a", "aud", "https://evil")));
    assertThrows(
        JwtRejectedException.class,
        () ->
            v.verify(
                sign(
                    priv,
                    sub,
                    wid,
                    exp,
                    null,
                    new Date(),
                    "a",
                    "other",
                    "https://code.example.com")));
    JWTClaimsSet noAud =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(sub.toString())
            .expirationTime(exp)
            .claim("wid", wid.toString())
            .build();
    SignedJWT j0 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), noAud);
    j0.sign(new RSASSASigner(priv));
    assertThrows(JwtRejectedException.class, () -> v.verify(j0.serialize()));
    assertThrows(
        JwtRejectedException.class,
        () ->
            v.verify(
                sign(
                    priv,
                    sub,
                    wid,
                    new Date(0),
                    null,
                    new Date(0),
                    "a",
                    "aud",
                    "https://code.example.com")));
    JWTClaimsSet noExp =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(sub.toString())
            .audience("aud")
            .claim("wid", wid.toString())
            .build();
    SignedJWT j1 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), noExp);
    j1.sign(new RSASSASigner(priv));
    assertThrows(JwtRejectedException.class, () -> v.verify(j1.serialize()));
    assertThrows(
        JwtRejectedException.class,
        () ->
            v.verify(
                sign(
                    priv,
                    sub,
                    wid,
                    exp,
                    new Date(System.currentTimeMillis() + 600000),
                    new Date(),
                    "a",
                    "aud",
                    "https://code.example.com")));
    JWTClaimsSet noIat =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(sub.toString())
            .audience("aud")
            .expirationTime(exp)
            .claim("wid", wid.toString())
            .claim("scope", "a")
            .build();
    SignedJWT j2 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), noIat);
    j2.sign(new RSASSASigner(priv));
    assertTrue(v.verify(j2.serialize()).scopes().contains("a"));
    assertThrows(
        JwtRejectedException.class,
        () ->
            v.verify(
                sign(
                    (RSAPrivateKey) other.getPrivate(),
                    sub,
                    wid,
                    exp,
                    null,
                    new Date(),
                    "a",
                    "aud",
                    "https://code.example.com")));
    assertThrows(
        JwtRejectedException.class,
        () ->
            v.verify(
                sign(
                    priv,
                    sub,
                    null,
                    exp,
                    null,
                    new Date(),
                    "a",
                    "aud",
                    "https://code.example.com")));
    assertThrows(
        JwtRejectedException.class,
        () ->
            v.verify(
                sign(priv, sub, wid, exp, null, new Date(), "a", "aud", "https://code.example.com")
                    .replaceFirst("\\.[^.]+$", ".AAAA")));
    SignedJWT badAlg =
        new SignedJWT(
            new JWSHeader(JWSAlgorithm.HS256),
            new JWTClaimsSet.Builder()
                .issuer("https://code.example.com")
                .subject(sub.toString())
                .audience("aud")
                .expirationTime(exp)
                .claim("wid", wid.toString())
                .build());
    badAlg.sign(new MACSigner(new byte[32]));
    assertThrows(JwtRejectedException.class, () -> v.verify(badAlg.serialize()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new JwtVerifier(pub, "", "aud", Duration.ofMinutes(1)));
    assertThrows(
        IllegalArgumentException.class, () -> new JwtVerifier(pub, "i", "", Duration.ofMinutes(1)));
    assertThrows(
        NullPointerException.class, () -> new JwtVerifier(null, "i", "a", Duration.ofMinutes(1)));
    assertThrows(NullPointerException.class, () -> new JwtVerifier(pub, "i", "a", null));
  }

  @Test
  void verifierScopeAndUuidEdges() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    KeyPair kp = g.generateKeyPair();
    RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
    RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();
    UUID sub = UUID.randomUUID();
    UUID wid = UUID.randomUUID();
    Date exp = new Date(System.currentTimeMillis() + 600000);
    JwtVerifier v = verifier(pub);
    assertTrue(
        v.verify(
                sign(
                    priv, sub, wid, exp, null, new Date(), null, "aud", "https://code.example.com"))
            .scopes()
            .isEmpty());
    assertTrue(
        v.verify(
                sign(
                    priv, sub, wid, exp, null, new Date(), "  ", "aud", "https://code.example.com"))
            .scopes()
            .isEmpty());
    JWTClaimsSet badWid =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(sub.toString())
            .audience("aud")
            .expirationTime(exp)
            .claim("wid", "not-a-uuid")
            .build();
    SignedJWT j = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), badWid);
    j.sign(new RSASSASigner(priv));
    assertThrows(JwtRejectedException.class, () -> v.verify(j.serialize()));
    JWTClaimsSet badSub =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject("nope")
            .audience("aud")
            .expirationTime(exp)
            .claim("wid", wid.toString())
            .build();
    SignedJWT j2 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), badSub);
    j2.sign(new RSASSASigner(priv));
    assertThrows(JwtRejectedException.class, () -> v.verify(j2.serialize()));
    JWTClaimsSet scopeList =
        new JWTClaimsSet.Builder()
            .issuer("https://code.example.com")
            .subject(sub.toString())
            .audience("aud")
            .expirationTime(exp)
            .claim("wid", wid.toString())
            .claim("scope", List.of("a"))
            .build();
    SignedJWT j3 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), scopeList);
    j3.sign(new RSASSASigner(priv));
    assertThrows(JwtRejectedException.class, () -> v.verify(j3.serialize()));
  }

  @Test
  void jwtKeysBranches() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    KeyPair kp = g.generateKeyPair();
    RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
    String pem =
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(pub.getEncoded())
            + "\n-----END PUBLIC KEY-----\n";
    Path f = tmp.resolve("pub.pem");
    Files.writeString(f, pem);
    assertEquals(pub, JwtKeys.loadPublicKey(f));
    Path bad64 = tmp.resolve("bad.pem");
    Files.writeString(bad64, "-----BEGIN PUBLIC KEY-----\n!!!\n-----END PUBLIC KEY-----\n");
    assertThrows(IllegalArgumentException.class, () -> JwtKeys.loadPublicKey(bad64));
    Path notKey = tmp.resolve("notkey.pem");
    Files.writeString(
        notKey,
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(new byte[] {1, 2, 3})
            + "\n-----END PUBLIC KEY-----\n");
    assertThrows(IllegalArgumentException.class, () -> JwtKeys.loadPublicKey(notKey));
    assertThrows(
        IllegalArgumentException.class, () -> JwtKeys.loadPublicKey(tmp.resolve("missing.pem")));
    assertThrows(IllegalArgumentException.class, () -> JwtKeys.loadPublicKey(tmp));
  }

  @Test
  void hostParserBranches() {
    assertThrows(IllegalArgumentException.class, () -> new HostParser(null));
    assertThrows(IllegalArgumentException.class, () -> new HostParser(" "));
    HostParser p = new HostParser("code.example.com");
    assertThrows(NullPointerException.class, () -> p.parse(null));
    assertThrows(IllegalArgumentException.class, () -> p.parse("other.com"));
    assertThrows(IllegalArgumentException.class, () -> p.parse("code.example.com"));
    assertThrows(IllegalArgumentException.class, () -> p.parse("nodash.code.example.com"));
    assertThrows(IllegalArgumentException.class, () -> p.parse("-8080.code.example.com"));
    assertThrows(IllegalArgumentException.class, () -> p.parse("user-.code.example.com"));
    assertThrows(IllegalArgumentException.class, () -> p.parse("user-abc.code.example.com"));
    ParsedHost ok = p.parse("RICHARD-8080.code.example.com.");
    assertEquals("richard", ok.userSlug());
    assertEquals(8080, ok.port());
    assertThrows(IllegalArgumentException.class, () -> p.parse("u-0.code.example.com"));
  }

  @Test
  void parsedHostAndDirectoryBranches() {
    assertThrows(IllegalArgumentException.class, () -> new ParsedHost(null, 1));
    assertThrows(IllegalArgumentException.class, () -> new ParsedHost(" ", 1));
    assertThrows(IllegalArgumentException.class, () -> new ParsedHost("u", 0));
    assertThrows(IllegalArgumentException.class, () -> new ParsedHost("u", 70000));
    WorkspaceDirectory d = new WorkspaceDirectory();
    assertTrue(d.lookup("u", 8080).isEmpty());
    UUID wid = UUID.randomUUID();
    d.register("u", 8080, wid);
    assertEquals(Optional.of(wid), d.lookup("u", 8080));
    d.unregister("u", 8080);
    assertTrue(d.lookup("u", 8080).isEmpty());
    assertThrows(IllegalArgumentException.class, () -> d.register(" ", 1, wid));
    assertThrows(IllegalArgumentException.class, () -> d.register(null, 1, wid));
    assertThrows(NullPointerException.class, () -> d.register("u", 1, null));
    assertThrows(NullPointerException.class, () -> new LoopbackTargetResolver().resolve(null, 1));
    InetSocketAddress addr = new LoopbackTargetResolver().resolve(UUID.randomUUID(), 8080);
    assertEquals(8080, addr.getPort());
  }

  @Test
  void gatewayErrorPaths() throws Exception {
    HostParser hosts = new HostParser("code.test");
    JwtVerifier tokens = mock(JwtVerifier.class);
    WorkspaceDirectory dir = new WorkspaceDirectory();
    GatewayServer server = new GatewayServer(hosts, tokens, dir, new LoopbackTargetResolver());
    int port;
    try (ServerSocket probe = new ServerSocket(0)) {
      port = probe.getLocalPort();
    }
    Thread.ofVirtual().start(() -> server.serve(port));
    Thread.sleep(300);
    UUID wid = UUID.randomUUID();
    UUID sub = UUID.randomUUID();
    when(tokens.verify("good")).thenReturn(new GatewaySession(sub, wid, List.of()));
    when(tokens.verify("bad")).thenThrow(new JwtRejectedException("no"));
    dir.register("richard", 8080, wid);
    assertEquals(
        "missing session token\n",
        send(port, "GET / HTTP/1.1\r\nHost: richard-8080.code.test\r\n\r\n"));
    assertEquals("missing Host header\n", send(port, "GET / HTTP/1.1\r\nX-A: b\r\n\r\n"));
    assertEquals("unroutable host\n", send(port, "GET / HTTP/1.1\r\nHost: nope.code.test\r\n\r\n"));
    assertEquals(
        "no such route\n",
        send(
            port,
            "GET / HTTP/1.1\r\nHost: richard-9090.code.test\r\nAuthorization: Bearer good\r\n\r\n"));
    assertEquals(
        "rejected session token\n",
        send(
            port,
            "GET / HTTP/1.1\r\nHost: richard-8080.code.test\r\nAuthorization: Bearer bad\r\n\r\n"));
    UUID other = UUID.randomUUID();
    when(tokens.verify("other")).thenReturn(new GatewaySession(sub, other, List.of()));
    assertEquals(
        "session workspace mismatch\n",
        send(
            port,
            "GET / HTTP/1.1\r\nHost: richard-8080.code.test\r\nAuthorization: Bearer other\r\n\r\n"));
    assertEquals(
        "chunked transfer-encoding unsupported\n",
        send(
            port,
            "GET / HTTP/1.1\r\nHost: richard-8080.code.test\r\nAuthorization: Bearer good\r\nTransfer-Encoding: chunked\r\n\r\n"));
    int dead;
    try (ServerSocket reserved = new ServerSocket(0)) {
      dead = reserved.getLocalPort();
    }
    dir.register("richard", 8080, wid);
    dir.register("richard", dead, wid);
    assertTrue(
        send(
                port,
                "GET / HTTP/1.1\r\nHost: richard-"
                    + dead
                    + ".code.test\r\nAuthorization: Bearer good\r\n\r\n")
            .contains("backend unreachable"));
    server.stop();
    Object h = readHead("POST /x HTTP/1.1\r\nHost: h\r\nContent-Length: -1\r\n\r\n");
    assertThrows(Exception.class, () -> contentLength(h));
    Object h2 =
        readHead(
            "GET /x?a=1&token=abc&b=2 HTTP/1.1\r\nHost: h\r\nAuthorization: Bearer good\r\n\r\n");
    assertEquals("good", headers(h2).get("authorization").substring(7));
    server.stop();
  }

  @SuppressWarnings("unchecked")
  private static java.util.Map<String, String> headers(Object head) throws Exception {
    var m = head.getClass().getDeclaredMethod("headers");
    m.setAccessible(true);
    return (java.util.Map<String, String>) m.invoke(head);
  }

  private static Object readHead(String raw) throws Exception {
    var m = GatewayServer.class.getDeclaredMethod("readHead", java.io.InputStream.class);
    m.setAccessible(true);
    return m.invoke(
        null, new java.io.ByteArrayInputStream(raw.getBytes(StandardCharsets.US_ASCII)));
  }

  private static long contentLength(Object head) throws Exception {
    try {
      var m = head.getClass().getDeclaredMethod("contentLength");
      m.setAccessible(true);
      return (long) m.invoke(head);
    } catch (java.lang.reflect.InvocationTargetException e) {
      throw (Exception) e.getCause();
    }
  }

  private String send(int port, String raw) throws Exception {
    try (Socket s = new Socket("127.0.0.1", port)) {
      s.setSoTimeout(5000);
      s.getOutputStream().write(raw.getBytes(StandardCharsets.US_ASCII));
      s.getOutputStream().flush();
      s.shutdownOutput();
      byte[] all = s.getInputStream().readAllBytes();
      String text = new String(all, StandardCharsets.US_ASCII);
      int idx = text.indexOf("\r\n\r\n");
      return idx < 0 ? text : text.substring(idx + 4);
    }
  }
}
