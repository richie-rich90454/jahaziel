/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.JahazielApplication;
import dev.jahaziel.nodeagent.service.LocalProcessBackend;
import dev.jahaziel.nodeagent.service.NodeAgentService;
import dev.jahaziel.nodeagent.storage.FolderStorage;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.WorkspaceHandle;
import dev.jahaziel.spi.WorkspaceStorage;
import dev.jahaziel.workspace.persistence.WorkspaceEntity;
import dev.jahaziel.workspace.persistence.WorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Boots the real application exactly as the fat jar does and proves the portable stack works: local
 * backend, folder storage, SQLite persistence, and a data directory that survives.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DistBootTest {
  private Path tmp;
  private ConfigurableApplicationContext context;
  private final List<String> touchedProperties = new ArrayList<>();

  @BeforeAll
  void boot() throws Exception {
    tmp = Files.createTempDirectory("jahaziel-dist-test");
    JarDataDir data = JarDataDir.resolve(tmp.resolve("data").toString());
    DevKeys.Keys keys = DevKeys.ensure(data.keys());
    JahazielMain.Arguments parsed =
        new JahazielMain.Arguments(
            JahazielMain.Mode.ALL, data.root().toString(), 0, 0, "code.example.com", false, false);
    Map<String, String> properties = JahazielMain.portablePropertyMap(data, keys, parsed, false);
    // The fat jar applies these as system properties; do the same so precedence matches prod.
    for (String key : properties.keySet()) {
      if (System.getProperty(key) == null) {
        touchedProperties.add(key);
      }
    }
    JahazielMain.portableProperties(data, keys, parsed, false);
    context = new SpringApplicationBuilder(JahazielApplication.class).run();
  }

  @AfterAll
  void shutdown() throws Exception {
    if (context != null) {
      context.close();
    }
    for (String key : touchedProperties) {
      System.clearProperty(key);
    }
    touchedProperties.clear();
    try (Stream<Path> walk = Files.walk(tmp)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (Exception ignored) {
                  // Best-effort test cleanup.
                }
              });
    }
  }

  @Test
  void portableBeansWin() {
    assertInstanceOf(LocalProcessBackend.class, context.getBean(NodeBackend.class));
    assertInstanceOf(FolderStorage.class, context.getBean(WorkspaceStorage.class));
    context.getBean(NodeAgentService.class);
    context.getBean(dev.jahaziel.nodeagent.sync.SyncServer.class);
  }

  @Test
  void storageCreatesRealFolders() {
    WorkspaceStorage storage = context.getBean(WorkspaceStorage.class);
    UUID workspaceId = UUID.randomUUID();
    WorkspaceHandle handle =
        storage.prepare(
            workspaceId, UUID.randomUUID(), new ImageRef("base-image:2026.09"), new QuotaBytes(1));
    assertTrue(Files.isDirectory(handle.homePath()));
    assertTrue(Files.isDirectory(handle.jailPath()));
    storage.destroy(handle);
  }

  @Test
  void repositoryRoundTripOnSqliteFile() {
    WorkspaceRepository workspaces = context.getBean(WorkspaceRepository.class);
    UUID workspaceId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    WorkspaceEntity row = new WorkspaceEntity();
    row.setWorkspaceId(workspaceId);
    row.setOwnerUserId(ownerId);
    row.setName("boot-test");
    row.setState("requested");
    row.setStateChangedAt(Instant.now());
    row.setLogicalQuotaBytes(1L << 30);
    row.setInodeQuota(1000);
    row.setCreatedAt(Instant.now());
    workspaces.save(row);
    assertEquals("boot-test", workspaces.findById(workspaceId).orElseThrow().getName());
    Path db = tmp.resolve("data").resolve("jahaziel.db");
    assertTrue(Files.isRegularFile(db));
  }

  @Test
  void schemaIsNeverRecreated() {
    assertEquals("update", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
    assertEquals("false", context.getEnvironment().getProperty("spring.flyway.enabled"));
  }
}
