/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.audit.persistence.AuditEventEntity;
import dev.jahaziel.audit.persistence.AuditEventRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Database-backed audit log with a hash chain. Chain contract: each hash is lowercase hex SHA-256
 * over the UTF-8 string {@code prevHash|eventId|timestamp|actor|action|target|outcome}, where
 * {@code prevHash} is the previous hash or {@code genesis}, {@code target} is empty when absent,
 * and the remaining fields render with {@code toString} ({@code name} for the outcome).
 */
public final class AuditServiceImpl implements AuditService {
  private final AuditEventRepository events;
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Creates the service.
   *
   * @param events the event rows
   */
  public AuditServiceImpl(AuditEventRepository events) {
    this.events = Objects.requireNonNull(events, "events");
  }

  @Override
  public synchronized AuditRecord append(AuditRecord event) {
    Objects.requireNonNull(event, "event");
    List<AuditEventEntity> previous = events.findLatest(PageRequest.of(0, 1));
    String prevHash = "genesis";
    if (!previous.isEmpty()) {
      prevHash = previous.get(0).getHash();
    }
    Optional<String> prevLink = previous.isEmpty() ? Optional.empty() : Optional.of(prevHash);
    String hash = chainHash(prevHash, event);
    AuditRecord stored =
        new AuditRecord(
            event.eventId(),
            event.timestamp(),
            event.actor(),
            event.actorType(),
            event.action(),
            event.target(),
            event.targetType(),
            event.outcome(),
            event.sourceIp(),
            event.sourceNode(),
            event.details(),
            prevLink,
            hash);
    events.save(AuditMapper.toEntity(stored, mapper));
    return stored;
  }

  @Override
  public List<AuditRecord> latest(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("Limit must be positive, was " + limit);
    }
    return events.findLatest(PageRequest.of(0, limit)).stream()
        .map(row -> AuditMapper.toDomain(row, mapper))
        .toList();
  }

  private static String chainHash(String prevHash, AuditRecord event) {
    String payload =
        String.join(
            "|",
            prevHash,
            event.eventId().toString(),
            event.timestamp().toString(),
            event.actor(),
            event.action(),
            event.target().orElse(""),
            event.outcome().name());
    try {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      byte[] digest = sha.digest(payload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
