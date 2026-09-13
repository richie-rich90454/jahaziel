/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.gateway.auth.JwtKeys;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.identity.internal.JwtServiceImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior of {@link DevKeys}: stable reuse plus control-plane/gateway format agreement. */
class DevKeysTest {
  @TempDir private Path tmp;

  @Test
  void generatesOnceAndLoadsEverywhere() {
    DevKeys.Keys first = DevKeys.ensure(tmp);
    assertTrue(Files.isRegularFile(first.privateKey()));
    assertTrue(Files.isRegularFile(first.publicKey()));
    DevKeys.Keys second = DevKeys.ensure(tmp);
    assertEquals(first, second);

    RSAPublicKey gatewayKey = JwtKeys.loadPublicKey(second.publicKey());
    RSAPrivateKey planeKey = JwtServiceImpl.loadPrivateKey(second.privateKey());
    JwtServiceImpl tokens =
        new JwtServiceImpl(
            planeKey, gatewayKey, "https://code.example.com", "jahaziel-code-server");
    UserId user = new UserId(UUID.randomUUID());
    UUID workspace = UUID.randomUUID();
    String token = tokens.issue(user, workspace, List.of("workspace:read"), Duration.ofMinutes(15));
    assertEquals(workspace, tokens.verify(token).workspaceId().orElseThrow());
  }
}
