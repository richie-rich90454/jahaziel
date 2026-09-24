/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.admin.internal.PluginLoader;
import dev.jahaziel.nodeagent.service.LocalProcessBackend;
import dev.jahaziel.nodeagent.storage.FolderStorage;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.WorkspaceStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/** Platform surface: actuator, unknown routes, plugin visibility, and the portable wiring. */
class PlatformE2eTest extends E2eBase {
  @Autowired ApplicationContext context;

  @Autowired Environment environment;

  @Test
  void actuatorHealthIsPublic() throws Exception {
    E2eResponse health = getAnonymous("/actuator/health");
    assertEquals(200, health.status(), health.body());
    assertTrue(health.body().contains("\"status\":\"UP\""), health.body());
  }

  @Test
  void actuatorInfoIsAbsent() throws Exception {
    // Only /actuator/health is public; info needs auth and is not exposed by default.
    assertEquals(401, getAnonymous("/actuator/info").status());
    assertEquals(404, get("/actuator/info", userToken()).status());
  }

  @Test
  void unknownRouteIsNotFoundJson() throws Exception {
    E2eResponse missing = get("/api/v1/nope", userToken());
    assertEquals(404, missing.status(), missing.body());
    assertTrue(missing.body().contains("\"code\":\"NOT_FOUND\""), missing.body());
    assertTrue(missing.body().contains("correlationId"), missing.body());
  }

  @Test
  void pluginsHaveNoHttpSurface() {
    // PluginLoader has no controller; with an empty plugins dir nothing loads either.
    assertTrue(context.getBean(PluginLoader.class).plugins().isEmpty());
  }

  @Test
  void portableStackBooted() {
    assertTrue(context.getBean(NodeBackend.class) instanceof LocalProcessBackend);
    assertTrue(context.getBean(WorkspaceStorage.class) instanceof FolderStorage);
    assertEquals("update", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    assertEquals("false", environment.getProperty("spring.flyway.enabled"));
  }
}
