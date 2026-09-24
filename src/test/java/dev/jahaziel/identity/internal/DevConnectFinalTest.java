/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import dev.jahaziel.identity.api.DevConnectException;
import dev.jahaziel.identity.internal.devconnect.DevConnectClient;
import dev.jahaziel.identity.internal.devconnect.DevConnectProperties;
import dev.jahaziel.identity.internal.devconnect.DiscoveryDocument;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/** Final gap tests: empty bodies, the decoder race, and the audience validator. */
class DevConnectFinalTest {
  private static DevConnectClient client() {
    DevConnectProperties props = new DevConnectProperties();
    props.setAuthIssuer("https://idp.test");
    props.setClientId("jahaziel");
    return new DevConnectClient(
        mock(RestClient.class), mock(RestTemplate.class), props, Clock.systemUTC());
  }

  private static Object read(DevConnectClient client, String body) throws Exception {
    Method read = DevConnectClient.class.getDeclaredMethod("read", String.class, String.class);
    read.setAccessible(true);
    try {
      return read.invoke(client, body, "http://x/token");
    } catch (InvocationTargetException failure) {
      throw (Exception) failure.getCause();
    }
  }

  @Test
  void readRejectsNullAndBlankBodies() {
    DevConnectClient client = client();
    assertThrows(DevConnectException.class, () -> read(client, null));
    assertThrows(DevConnectException.class, () -> read(client, "  "));
  }

  @Test
  void readParsesValidJson() throws Exception {
    assertNotNull(read(client(), "{\"issuer\":\"x\"}"));
  }

  private static DiscoveryDocument document(String jwksUri) {
    return new DiscoveryDocument(
        "https://idp.test",
        "https://idp.test/authorize",
        "https://idp.test/token",
        "https://idp.test/userinfo",
        jwksUri);
  }

  private static void setDecoder(DevConnectClient client, JwtDecoder decoder, String uri)
      throws Exception {
    Field decoderField = DevConnectClient.class.getDeclaredField("decoder");
    decoderField.setAccessible(true);
    decoderField.set(client, decoder);
    Field uriField = DevConnectClient.class.getDeclaredField("decoderJwksUri");
    uriField.setAccessible(true);
    uriField.set(client, uri);
  }

  private static Object decoderFor(DevConnectClient client, DiscoveryDocument document)
      throws Exception {
    Method method = DevConnectClient.class.getDeclaredMethod("decoderFor", DiscoveryDocument.class);
    method.setAccessible(true);
    return method.invoke(client, document);
  }

  @Test
  void decoderSkipsRebuildInsideLock() throws Exception {
    DevConnectClient client = client();
    setDecoder(client, mock(JwtDecoder.class), null);
    int threads = 4;
    int rounds = 25;
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);
    List<Object> results = java.util.Collections.synchronizedList(new ArrayList<>());
    List<Thread> workers = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      Thread worker =
          Thread.ofVirtual()
              .start(
                  () -> {
                    ready.countDown();
                    try {
                      go.await(15, java.util.concurrent.TimeUnit.SECONDS);
                      for (int i = 0; i < rounds; i++) {
                        results.add(decoderFor(client, document("https://idp.test/jwks")));
                      }
                    } catch (Exception failure) {
                      throw new AssertionError(failure);
                    }
                  });
      workers.add(worker);
    }
    ready.await(15, java.util.concurrent.TimeUnit.SECONDS);
    go.countDown();
    for (Thread worker : workers) {
      worker.join(30_000);
    }
    assertEquals(threads * rounds, results.size());
    assertTrue(results.stream().allMatch(java.util.Objects::nonNull));
  }

  @Test
  @SuppressWarnings("unchecked")
  void audienceValidatorDecides() throws Exception {
    DevConnectClient client = client();
    JwtDecoder built = (JwtDecoder) decoderFor(client, document("https://idp.test/jwks"));
    Field validatorField = built.getClass().getDeclaredField("jwtValidator");
    validatorField.setAccessible(true);
    var validator =
        (org.springframework.security.oauth2.core.OAuth2TokenValidator<
                org.springframework.security.oauth2.jwt.Jwt>)
            validatorField.get(built);
    org.springframework.security.oauth2.jwt.Jwt noAud =
        new org.springframework.security.oauth2.jwt.Jwt(
            "t",
            java.time.Instant.now(),
            java.time.Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", "x", "iss", "https://idp.test"));
    assertTrue(validator.validate(noAud).hasErrors());
    org.springframework.security.oauth2.jwt.Jwt wrongAud =
        new org.springframework.security.oauth2.jwt.Jwt(
            "t",
            java.time.Instant.now(),
            java.time.Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", "x", "iss", "https://idp.test", "aud", List.of("other")));
    assertTrue(validator.validate(wrongAud).hasErrors());
    org.springframework.security.oauth2.jwt.Jwt rightAud =
        new org.springframework.security.oauth2.jwt.Jwt(
            "t",
            java.time.Instant.now(),
            java.time.Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", "x", "iss", "https://idp.test", "aud", List.of("jahaziel")));
    assertTrue(!validator.validate(rightAud).hasErrors());
  }
}
