/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpiExtraCoverageTest {
  @Test
  void auditQueryGuards() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AuditQuery(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 0));
    assertThrows(
        NullPointerException.class,
        () -> new AuditQuery(null, Optional.empty(), Optional.empty(), Optional.empty(), 1));
    assertThrows(
        NullPointerException.class,
        () -> new AuditQuery(Optional.empty(), null, Optional.empty(), Optional.empty(), 1));
    var q =
        new AuditQuery(Optional.empty(), Optional.empty(), Optional.of("a"), Optional.of("b"), 5);
    assertEquals(5, q.limit());
  }

  @Test
  void metricGuards() {
    assertThrows(IllegalArgumentException.class, () -> new Metric("", 1, Map.of(), Instant.now()));
    assertThrows(
        IllegalArgumentException.class, () -> new Metric(null, 1, Map.of(), Instant.now()));
    assertThrows(NullPointerException.class, () -> new Metric("m", 1, null, Instant.now()));
    assertThrows(NullPointerException.class, () -> new Metric("m", 1, Map.of(), null));
    assertEquals("m", new Metric("m", 1, Map.of(), Instant.now()).name());
  }

  @Test
  void storageUsageGuards() {
    assertThrows(IllegalArgumentException.class, () -> new StorageUsage(-1, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new StorageUsage(0, -1, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new StorageUsage(0, 0, -1, 0));
    assertThrows(IllegalArgumentException.class, () -> new StorageUsage(0, 0, 0, -5));
    assertEquals(0, new StorageUsage(0, 0, 0, 0).deduplicatedBytes());
  }

  @Test
  void imageRefGuards() {
    assertThrows(IllegalArgumentException.class, () -> new ImageRef(null));
    assertThrows(IllegalArgumentException.class, () -> new ImageRef("  "));
    assertEquals("a:b", new ImageRef("a:b").value());
  }

  @Test
  void extensionRequestGuards() {
    UUID u = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> new ExtensionRequest("", "1", u, u));
    assertThrows(IllegalArgumentException.class, () -> new ExtensionRequest(null, "1", u, u));
    assertThrows(IllegalArgumentException.class, () -> new ExtensionRequest("e", "", u, u));
    assertThrows(IllegalArgumentException.class, () -> new ExtensionRequest("e", null, u, u));
    assertThrows(NullPointerException.class, () -> new ExtensionRequest("e", "1", null, u));
    assertThrows(NullPointerException.class, () -> new ExtensionRequest("e", "1", u, null));
  }

  @Test
  void quotaBytesGuards() {
    assertThrows(IllegalArgumentException.class, () -> new QuotaBytes(-1));
    assertEquals(0, new QuotaBytes(0).value());
  }

  @Test
  void snapshotTagGuards() {
    assertThrows(IllegalArgumentException.class, () -> new SnapshotTag(null));
    assertThrows(IllegalArgumentException.class, () -> new SnapshotTag(""));
    assertThrows(IllegalArgumentException.class, () -> new SnapshotTag("  "));
    assertThrows(IllegalArgumentException.class, () -> new SnapshotTag("a..b"));
    assertThrows(IllegalArgumentException.class, () -> new SnapshotTag("a/b"));
    assertThrows(IllegalArgumentException.class, () -> new SnapshotTag("a\\b"));
    assertThrows(IllegalArgumentException.class, () -> new SnapshotTag(".hidden"));
    assertThrows(IllegalArgumentException.class, () -> new SnapshotTag("/abs"));
    assertEquals("ok-1", new SnapshotTag("ok-1").value());
  }

  @Test
  void resourceGuards() {
    assertThrows(IllegalArgumentException.class, () -> new Resource(null, "x"));
    assertThrows(IllegalArgumentException.class, () -> new Resource("", "x"));
    assertThrows(IllegalArgumentException.class, () -> new Resource("t", null));
    assertThrows(IllegalArgumentException.class, () -> new Resource("t", " "));
    assertEquals("t", new Resource("t", "x").type());
  }

  @Test
  void notificationGuards() {
    assertThrows(IllegalArgumentException.class, () -> new Notification("", "s", "b"));
    assertThrows(IllegalArgumentException.class, () -> new Notification(null, "s", "b"));
    assertThrows(IllegalArgumentException.class, () -> new Notification("r", "", "b"));
    assertThrows(IllegalArgumentException.class, () -> new Notification("r", null, "b"));
    assertThrows(IllegalArgumentException.class, () -> new Notification("r", "s", ""));
    assertThrows(IllegalArgumentException.class, () -> new Notification("r", "s", null));
  }

  @Test
  void permissionAndPolicyGuards() {
    assertThrows(IllegalArgumentException.class, () -> new Permission(null));
    assertThrows(IllegalArgumentException.class, () -> new Permission(" "));
    assertThrows(IllegalArgumentException.class, () -> new Policy("", Map.of()));
    assertThrows(IllegalArgumentException.class, () -> new Policy(null, Map.of()));
    assertThrows(NullPointerException.class, () -> new Policy("p", null));
    assertEquals(Map.of("k", "v"), new Policy("p", Map.of("k", "v")).attributes());
  }

  @Test
  void auditEventCopiesDetails() {
    var e =
        new AuditEvent(
            UUID.randomUUID(),
            Instant.now(),
            "a",
            ActorType.USER,
            "act",
            Optional.empty(),
            Optional.empty(),
            Outcome.SUCCESS,
            Optional.empty(),
            Optional.empty(),
            Map.of("k", "v"),
            Optional.empty(),
            "h");
    assertEquals("v", e.details().get("k"));
    assertThrows(
        NullPointerException.class,
        () ->
            new AuditEvent(
                null,
                Instant.now(),
                "a",
                ActorType.USER,
                "act",
                Optional.empty(),
                Optional.empty(),
                Outcome.SUCCESS,
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                Optional.empty(),
                "h"));
  }

  @Test
  void runtimeRequestGuards() {
    UUID w = UUID.randomUUID();
    assertThrows(
        IllegalArgumentException.class,
        () -> new RuntimeRequest(w, 0, 1, 0, 0, 0, 1, 1, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RuntimeRequest(w, 1, 0, 0, 0, 0, 1, 1, List.of()));
    assertThrows(
        NullPointerException.class, () -> new RuntimeRequest(null, 1, 1, 0, 0, 0, 1, 1, List.of()));
    assertThrows(
        NullPointerException.class, () -> new RuntimeRequest(w, 1, 1, 0, 0, 0, 1, 1, null));
    assertTrue(
        new RuntimeRequest(w, 1, 1, 0, 0, 0, 1, 1, List.of()).kernelCmdlineExtra().isEmpty());
  }

  @Test
  void prepareRequestGuards() {
    UUID w = UUID.randomUUID();
    ImageRef img = new ImageRef("base:1");
    QuotaBytes q = new QuotaBytes(1);
    assertThrows(IllegalArgumentException.class, () -> new PrepareRequest(w, w, img, q, 0, 1));
    assertThrows(IllegalArgumentException.class, () -> new PrepareRequest(w, w, img, q, 1, 0));
    assertThrows(NullPointerException.class, () -> new PrepareRequest(null, w, img, q, 1, 1));
    assertThrows(NullPointerException.class, () -> new PrepareRequest(w, null, img, q, 1, 1));
    assertThrows(NullPointerException.class, () -> new PrepareRequest(w, w, null, q, 1, 1));
    assertThrows(NullPointerException.class, () -> new PrepareRequest(w, w, img, null, 1, 1));
  }
}
