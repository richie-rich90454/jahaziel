/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.audit.persistence.AuditEventEntity;
import dev.jahaziel.audit.persistence.AuditEventRepository;
import dev.jahaziel.audit.persistence.AuditProcessEntity;
import dev.jahaziel.audit.persistence.AuditProcessRepository;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.policy.persistence.PolicyEntity;
import dev.jahaziel.policy.persistence.PolicyRepository;
import dev.jahaziel.workspace.persistence.WorkspaceEntity;
import dev.jahaziel.workspace.persistence.WorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves the JPA mappings run on the portable SQLite file: UUID keys, instants, JSON text, and
 * string arrays round-trip through real JDBC. SQLite datetimes keep millis, so test instants are
 * truncated likewise.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SqlitePersistenceTest {
  static final Path DB_DIR = tempDir();

  private static Path tempDir() {
    try {
      return Files.createTempDirectory("jahaziel-sqlite-test");
    } catch (Exception failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }

  @DynamicPropertySource
  static void sqlite(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_DIR.resolve("jahaziel.db"));
    registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
    registry.add(
        "spring.jpa.properties.hibernate.dialect",
        () -> "org.hibernate.community.dialect.SQLiteDialect");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add("spring.flyway.enabled", () -> "false");
  }

  @Autowired private AppUserRepository users;
  @Autowired private WorkspaceRepository workspaces;
  @Autowired private PolicyRepository policies;
  @Autowired private AuditEventRepository events;
  @Autowired private AuditProcessRepository processes;

  @Test
  void workspaceRoundTrip() {
    UUID userId = UUID.randomUUID();
    AppUserEntity user = new AppUserEntity();
    user.setUserId(userId);
    user.setExternalId("sub-1");
    user.setEmail("a@example.com");
    user.setDisplayName("A");
    user.setCreatedAt(now());
    user.setStatus("active");
    user.setMfaEnabled(false);
    users.save(user);

    UUID workspaceId = UUID.randomUUID();
    WorkspaceEntity workspace = new WorkspaceEntity();
    workspace.setWorkspaceId(workspaceId);
    workspace.setOwnerUserId(userId);
    workspace.setName("site");
    workspace.setState("requested");
    workspace.setStateChangedAt(now());
    workspace.setLogicalQuotaBytes(1L << 30);
    workspace.setInodeQuota(1000);
    workspace.setCreatedAt(now());
    workspaces.save(workspace);

    WorkspaceEntity found = workspaces.findById(workspaceId).orElseThrow();
    assertEquals("site", found.getName());
    assertEquals(userId, found.getOwnerUserId());
    assertTrue(Files.isRegularFile(DB_DIR.resolve("jahaziel.db")));
  }

  @Test
  void policyJsonRoundTrip() {
    PolicyEntity policy = new PolicyEntity();
    policy.setPolicyId(UUID.randomUUID());
    policy.setScope("global");
    policy.setPolicyJson("{\"allow\":[\"read\"]}");
    policy.setUpdatedAt(now());
    policy.setUpdatedBy("test");
    policies.save(policy);
    assertEquals(
        "{\"allow\":[\"read\"]}",
        policies.findById(policy.getPolicyId()).orElseThrow().getPolicyJson());
  }

  @Test
  void auditEventRoundTrip() {
    AuditEventEntity row = new AuditEventEntity();
    AuditEventEntity.Id id = new AuditEventEntity.Id();
    id.setEventId(UUID.randomUUID());
    id.setTimestamp(now());
    row.setId(id);
    row.setActor("user:x");
    row.setActorType("USER");
    row.setAction("workspace.create");
    row.setOutcome("SUCCESS");
    row.setDetails("{\"tag\":\"daily\"}");
    row.setHash("abc");
    events.save(row);
    assertEquals("{\"tag\":\"daily\"}", events.findById(id).orElseThrow().getDetails());
  }

  @Test
  void auditProcessArraysRoundTrip() {
    AuditProcessEntity row = new AuditProcessEntity();
    AuditProcessEntity.Id id = new AuditProcessEntity.Id();
    id.setRecordId(UUID.randomUUID());
    id.setTimestamp(now());
    row.setId(id);
    row.setWorkspaceId(UUID.randomUUID());
    row.setPid(42);
    row.setExePath("/bin/bash");
    row.setArgv(new String[] {"bash", "-c", "echo \"hi\" \\ bye"});
    row.setCapabilities(new String[] {"CAP_NET_BIND_SERVICE"});
    row.setStartTime(now());
    row.setUid(1000);
    row.setGid(1000);
    processes.save(row);
    AuditProcessEntity found = processes.findById(id).orElseThrow();
    assertArrayEquals(new String[] {"bash", "-c", "echo \"hi\" \\ bye"}, found.getArgv());
    assertArrayEquals(new String[] {"CAP_NET_BIND_SERVICE"}, found.getCapabilities());
  }

  private static Instant now() {
    return Instant.now().truncatedTo(ChronoUnit.MILLIS);
  }
}
