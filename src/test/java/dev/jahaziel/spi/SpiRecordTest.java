/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Validation of the small SPI value types. */
class SpiRecordTest {
  @Test
  void auditEventStoresFields() {
    AuditEvent event =
        new AuditEvent(
            UUID.randomUUID(),
            Instant.now(),
            "user:x",
            ActorType.USER,
            "workspace.start",
            Optional.of("ws:x"),
            Optional.of("workspace"),
            Outcome.SUCCESS,
            Optional.of("10.0.0.1"),
            Optional.of("node-a"),
            Map.of("port", "8080"),
            Optional.of("prev"),
            "hash");
    assertEquals("user:x", event.actor());
    assertEquals("hash", event.hash());
    assertEquals(Map.of("port", "8080"), event.details());
  }

  @Test
  void auditQueryAcceptsValidLimit() {
    AuditQuery query =
        new AuditQuery(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 5);
    assertEquals(5, query.limit());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AuditQuery(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 0));
  }

  @Test
  void extensionRequestGuardsBlanks() {
    UUID user = UUID.randomUUID();
    UUID workspace = UUID.randomUUID();
    ExtensionRequest request = new ExtensionRequest("publisher.ext", "1.0", user, workspace);
    assertEquals("publisher.ext", request.extensionId());
    assertThrows(
        IllegalArgumentException.class, () -> new ExtensionRequest(null, "1.0", user, workspace));
    assertThrows(
        IllegalArgumentException.class, () -> new ExtensionRequest("  ", "1.0", user, workspace));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExtensionRequest("publisher.ext", null, user, workspace));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExtensionRequest("publisher.ext", "  ", user, workspace));
  }

  @Test
  void imageRefGuardsBlanks() {
    assertEquals("base-image:2026.09", new ImageRef("base-image:2026.09").value());
    assertThrows(IllegalArgumentException.class, () -> new ImageRef(null));
    assertThrows(IllegalArgumentException.class, () -> new ImageRef("  "));
  }

  @Test
  void metricStoresFieldsAndCopiesTags() {
    Metric metric = new Metric("cpu", 1.5, Map.of("node", "a"), Instant.now());
    assertEquals("cpu", metric.name());
    assertEquals(Map.of("node", "a"), metric.tags());
    assertThrows(
        IllegalArgumentException.class, () -> new Metric(null, 1.5, Map.of(), Instant.now()));
    assertThrows(
        IllegalArgumentException.class, () -> new Metric("  ", 1.5, Map.of(), Instant.now()));
    assertThrows(NullPointerException.class, () -> new Metric("cpu", 1.5, null, Instant.now()));
    assertThrows(NullPointerException.class, () -> new Metric("cpu", 1.5, Map.of(), null));
  }

  @Test
  void notificationGuardsBlanks() {
    Notification notification = new Notification("user", "subject", "body");
    assertEquals("body", notification.body());
    assertThrows(IllegalArgumentException.class, () -> new Notification(null, "s", "b"));
    assertThrows(IllegalArgumentException.class, () -> new Notification("  ", "s", "b"));
    assertThrows(IllegalArgumentException.class, () -> new Notification("r", null, "b"));
    assertThrows(IllegalArgumentException.class, () -> new Notification("r", "  ", "b"));
    assertThrows(IllegalArgumentException.class, () -> new Notification("r", "s", null));
    assertThrows(IllegalArgumentException.class, () -> new Notification("r", "s", "  "));
  }

  @Test
  void permissionGuardsBlanks() {
    assertEquals("workspace.read", new Permission("workspace.read").name());
    assertThrows(IllegalArgumentException.class, () -> new Permission(null));
    assertThrows(IllegalArgumentException.class, () -> new Permission("  "));
  }

  @Test
  void policyStoresAttributes() {
    Policy policy = new Policy("default", Map.of("k", "v"));
    assertEquals(Map.of("k", "v"), policy.attributes());
    assertThrows(IllegalArgumentException.class, () -> new Policy(null, Map.of()));
    assertThrows(IllegalArgumentException.class, () -> new Policy("  ", Map.of()));
    assertThrows(NullPointerException.class, () -> new Policy("default", null));
  }

  @Test
  void quotaBytesRejectsNegatives() {
    assertEquals(0L, new QuotaBytes(0L).value());
    assertEquals(8L, new QuotaBytes(8L).value());
    assertThrows(IllegalArgumentException.class, () -> new QuotaBytes(-1L));
  }

  @Test
  void resourceGuardsBlanks() {
    Resource resource = new Resource("workspace", "ws-1");
    assertEquals("ws-1", resource.id());
    assertThrows(IllegalArgumentException.class, () -> new Resource(null, "ws-1"));
    assertThrows(IllegalArgumentException.class, () -> new Resource("  ", "ws-1"));
    assertThrows(IllegalArgumentException.class, () -> new Resource("workspace", null));
    assertThrows(IllegalArgumentException.class, () -> new Resource("workspace", "  "));
  }

  @Test
  void runtimeRequestGuardsSizing() {
    RuntimeRequest request =
        new RuntimeRequest(UUID.randomUUID(), 2, 2048, 0, 0, 0, 0, 0, List.of());
    assertEquals(2, request.vcpuCount());
    assertThrows(
        IllegalArgumentException.class,
        () -> new RuntimeRequest(UUID.randomUUID(), 0, 2048, 0, 0, 0, 0, 0, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RuntimeRequest(UUID.randomUUID(), 2, 0, 0, 0, 0, 0, 0, List.of()));
  }

  @Test
  void storageUsageRejectsNegatives() {
    StorageUsage usage = new StorageUsage(1, 1, 1, 0);
    assertEquals(1L, usage.logicalBytes());
    assertThrows(IllegalArgumentException.class, () -> new StorageUsage(-1, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new StorageUsage(0, -1, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new StorageUsage(0, 0, -1, 0));
  }
}
