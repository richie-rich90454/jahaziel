/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.monitoring.api.HourlyRollup;
import dev.jahaziel.networking.api.GatewayEndpoint;
import dev.jahaziel.networking.api.PortBinding;
import dev.jahaziel.node.domain.Node;
import dev.jahaziel.node.domain.NodeCapacity;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.spi.ActorType;
import dev.jahaziel.spi.Outcome;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Mapping of the read-model DTO records. */
class ApiDtoTest {
  @Test
  void auditRecordMapsAllFields() {
    UUID eventId = UUID.randomUUID();
    Instant timestamp = Instant.now();
    AuditRecord record =
        new AuditRecord(
            eventId,
            timestamp,
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
    AuditRecordDto dto = AuditRecordDto.from(record);
    assertEquals(eventId, dto.eventId());
    assertEquals(timestamp, dto.timestamp());
    assertEquals("user:x", dto.actor());
    assertEquals("USER", dto.actorType());
    assertEquals("workspace.start", dto.action());
    assertEquals(Optional.of("ws:x"), dto.target());
    assertEquals("SUCCESS", dto.outcome());
    assertEquals("hash", dto.hash());
  }

  @Test
  void metricsMapFromRollupAndZero() {
    HourlyRollup rollup =
        new HourlyRollup(
            UUID.randomUUID(), Instant.now(), 10.0f, 20.0f, 100L, 200L, 1L, 2L, 3L, 4L, 5);
    MetricsDto dto = MetricsDto.from(rollup);
    assertEquals(10.0f, dto.cpuUsagePercent(), 0.0001);
    assertEquals(100L * 1024 * 1024, dto.memoryUsedBytes());
    assertEquals(200L * 1024 * 1024, dto.memoryPeakBytes());
    assertEquals(1L, dto.diskReadBytesPerSec());
    assertEquals(3L, dto.networkRxBytesPerSec());
    assertEquals(4L, dto.networkTxBytesPerSec());
    assertEquals(5, dto.processCount());
    MetricsDto zero = MetricsDto.zero();
    assertEquals(0, zero.cpuUsagePercent(), 0.0001);
    assertEquals(0, zero.processCount());
  }

  @Test
  void nodeMapsWithLowercaseState() {
    Node node =
        new Node(
            new NodeId("node-a"),
            "Primary",
            "default",
            new NodeCapacity(14, 49152, 500),
            "sha256:abc");
    NodeDto dto = NodeDto.from(node);
    assertEquals("node-a", dto.nodeId());
    assertEquals("Primary", dto.name());
    assertEquals("default", dto.region());
    assertEquals("healthy", dto.state());
    assertEquals(14, dto.cpuCores());
    assertEquals(49152L, dto.memoryMib());
    assertEquals(500L, dto.diskGib());
  }

  @Test
  void portIncludesTokenizedUrlOnlyWithToken() {
    PortBinding binding =
        new PortBinding(UUID.randomUUID(), 8080, UUID.randomUUID(), "http", Instant.now());
    PortDto withToken =
        PortDto.from(
            binding, "richard", new GatewayEndpoint("https", "code.example.com", 443), "tok");
    assertEquals(8080, withToken.port());
    assertEquals("http", withToken.protocol());
    assertEquals("https://richard-8080.code.example.com/?token=tok", withToken.url());
    PortDto withoutToken =
        PortDto.from(
            binding, "richard", new GatewayEndpoint("https", "code.example.com", 443), null);
    assertNull(withoutToken.url());
    assertEquals(binding.registeredAt(), withoutToken.registeredAt());
  }
}
