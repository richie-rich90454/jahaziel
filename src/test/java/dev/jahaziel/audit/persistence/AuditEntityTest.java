/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Coverage of the audit persistence entities and their composite keys. */
class AuditEntityTest {
  @Test
  void terminalEntityStoresAllFields() {
    AuditTerminalEntity entity = new AuditTerminalEntity();
    AuditTerminalEntity.Id id = new AuditTerminalEntity.Id(UUID.randomUUID(), Instant.now());
    entity.setId(id);
    UUID eventId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    entity.setEventId(eventId);
    entity.setUserId(userId);
    entity.setWorkspaceId(workspaceId);
    entity.setTerminalSessionId(sessionId);
    entity.setCommand("ls -la");
    entity.setExitCode(3);
    entity.setCwd("/home/user");
    entity.setEnvHash("env");
    entity.setDurationMs(12L);
    entity.setStdoutObjectId("out");
    entity.setStderrObjectId("err");
    entity.setStdoutSize(10L);
    entity.setStderrSize(11L);
    assertEquals(id, entity.getId());
    assertEquals(eventId, entity.getEventId());
    assertEquals(userId, entity.getUserId());
    assertEquals(workspaceId, entity.getWorkspaceId());
    assertEquals(sessionId, entity.getTerminalSessionId());
    assertEquals("ls -la", entity.getCommand());
    assertEquals(3, entity.getExitCode());
    assertEquals("/home/user", entity.getCwd());
    assertEquals("env", entity.getEnvHash());
    assertEquals(12L, entity.getDurationMs());
    assertEquals("out", entity.getStdoutObjectId());
    assertEquals("err", entity.getStderrObjectId());
    assertEquals(10L, entity.getStdoutSize());
    assertEquals(11L, entity.getStderrSize());
  }

  @Test
  void terminalEntityEqualsById() {
    AuditTerminalEntity.Id id = new AuditTerminalEntity.Id(UUID.randomUUID(), Instant.now());
    AuditTerminalEntity first = new AuditTerminalEntity();
    first.setId(id);
    first.setCommand("a");
    AuditTerminalEntity second = new AuditTerminalEntity();
    second.setId(id);
    second.setCommand("b");
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setId(new AuditTerminalEntity.Id(UUID.randomUUID(), Instant.now()));
    assertFalse(first.equals(second));
  }

  @Test
  void terminalIdStoresFieldsAndCompares() {
    UUID recordId = UUID.randomUUID();
    Instant timestamp = Instant.now();
    AuditTerminalEntity.Id id = new AuditTerminalEntity.Id();
    id.setRecordId(recordId);
    id.setTimestamp(timestamp);
    assertEquals(recordId, id.getRecordId());
    assertEquals(timestamp, id.getTimestamp());
    AuditTerminalEntity.Id same = new AuditTerminalEntity.Id(recordId, timestamp);
    assertTrue(id.equals(id));
    assertTrue(id.equals(same));
    assertEquals(id.hashCode(), same.hashCode());
    assertFalse(id.equals(null));
    assertFalse(id.equals((Object) "other"));
    assertFalse(id.equals(new AuditTerminalEntity.Id(UUID.randomUUID(), timestamp)));
    assertFalse(id.equals(new AuditTerminalEntity.Id(recordId, Instant.EPOCH)));
  }

  @Test
  void eventEntityStoresAllFields() {
    AuditEventEntity entity = new AuditEventEntity();
    AuditEventEntity.Id id = new AuditEventEntity.Id(UUID.randomUUID(), Instant.now());
    entity.setId(id);
    entity.setActor("user:x");
    entity.setActorType("USER");
    entity.setAction("workspace.start");
    entity.setTarget("ws:x");
    entity.setTargetType("workspace");
    entity.setOutcome("SUCCESS");
    entity.setSourceIp("10.0.0.1");
    entity.setSourceNode("node-a");
    entity.setDetails("{}");
    entity.setPrevHash("prev");
    entity.setHash("hash");
    assertEquals(id, entity.getId());
    assertEquals("user:x", entity.getActor());
    assertEquals("USER", entity.getActorType());
    assertEquals("workspace.start", entity.getAction());
    assertEquals("ws:x", entity.getTarget());
    assertEquals("workspace", entity.getTargetType());
    assertEquals("SUCCESS", entity.getOutcome());
    assertEquals("10.0.0.1", entity.getSourceIp());
    assertEquals("node-a", entity.getSourceNode());
    assertEquals("{}", entity.getDetails());
    assertEquals("prev", entity.getPrevHash());
    assertEquals("hash", entity.getHash());
  }

  @Test
  void eventEntityEqualsById() {
    AuditEventEntity.Id id = new AuditEventEntity.Id(UUID.randomUUID(), Instant.now());
    AuditEventEntity first = new AuditEventEntity();
    first.setId(id);
    AuditEventEntity second = new AuditEventEntity();
    second.setId(id);
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setId(new AuditEventEntity.Id(UUID.randomUUID(), Instant.now()));
    assertFalse(first.equals(second));
  }

  @Test
  void eventIdStoresFieldsAndCompares() {
    UUID eventId = UUID.randomUUID();
    Instant timestamp = Instant.now();
    AuditEventEntity.Id id = new AuditEventEntity.Id();
    id.setEventId(eventId);
    id.setTimestamp(timestamp);
    assertEquals(eventId, id.getEventId());
    assertEquals(timestamp, id.getTimestamp());
    AuditEventEntity.Id same = new AuditEventEntity.Id(eventId, timestamp);
    assertTrue(id.equals(id));
    assertTrue(id.equals(same));
    assertEquals(id.hashCode(), same.hashCode());
    assertFalse(id.equals(null));
    assertFalse(id.equals((Object) "other"));
    assertFalse(id.equals(new AuditEventEntity.Id(UUID.randomUUID(), timestamp)));
    assertFalse(id.equals(new AuditEventEntity.Id(eventId, Instant.EPOCH)));
  }

  @Test
  void processEntityStoresAllFields() {
    AuditProcessEntity entity = new AuditProcessEntity();
    AuditProcessEntity.Id id = new AuditProcessEntity.Id(UUID.randomUUID(), Instant.now());
    entity.setId(id);
    entity.setWorkspaceId(UUID.randomUUID());
    entity.setPid(42);
    entity.setPpid(1);
    entity.setExePath("/bin/bash");
    entity.setArgv(new String[] {"bash", "-c"});
    entity.setExitCode(0);
    entity.setStartTime(Instant.EPOCH);
    entity.setEndTime(Instant.now());
    entity.setUid(1000);
    entity.setGid(1000);
    entity.setCapabilities(new String[] {"net_bind"});
    assertEquals(id, entity.getId());
    assertEquals(42, entity.getPid());
    assertEquals(1, entity.getPpid());
    assertEquals("/bin/bash", entity.getExePath());
    assertArrayEquals(new String[] {"bash", "-c"}, entity.getArgv());
    assertEquals(0, entity.getExitCode());
    assertEquals(Instant.EPOCH, entity.getStartTime());
    assertEquals(1000, entity.getUid());
    assertEquals(1000, entity.getGid());
    assertArrayEquals(new String[] {"net_bind"}, entity.getCapabilities());
  }

  @Test
  void processEntityEqualsById() {
    AuditProcessEntity.Id id = new AuditProcessEntity.Id(UUID.randomUUID(), Instant.now());
    AuditProcessEntity first = new AuditProcessEntity();
    first.setId(id);
    AuditProcessEntity second = new AuditProcessEntity();
    second.setId(id);
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setId(new AuditProcessEntity.Id(UUID.randomUUID(), Instant.now()));
    assertFalse(first.equals(second));
  }

  @Test
  void processIdStoresFieldsAndCompares() {
    UUID recordId = UUID.randomUUID();
    Instant timestamp = Instant.now();
    AuditProcessEntity.Id id = new AuditProcessEntity.Id();
    id.setRecordId(recordId);
    id.setTimestamp(timestamp);
    assertEquals(recordId, id.getRecordId());
    assertEquals(timestamp, id.getTimestamp());
    AuditProcessEntity.Id same = new AuditProcessEntity.Id(recordId, timestamp);
    assertTrue(id.equals(id));
    assertTrue(id.equals(same));
    assertEquals(id.hashCode(), same.hashCode());
    assertFalse(id.equals(null));
    assertFalse(id.equals((Object) "other"));
    assertFalse(id.equals(new AuditProcessEntity.Id(UUID.randomUUID(), timestamp)));
    assertFalse(id.equals(new AuditProcessEntity.Id(recordId, Instant.EPOCH)));
  }
}
