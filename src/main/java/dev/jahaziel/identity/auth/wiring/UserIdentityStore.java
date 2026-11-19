/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.wiring;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Looks up and creates the durable (provider, subject) to local user link. */
public final class UserIdentityStore {
  private static final String FIND =
      "SELECT user_id FROM user_identity WHERE provider = ? AND subject = ?";
  private static final String LINK =
      "INSERT INTO user_identity (provider, subject, user_id, email, display_name, linked_at)"
          + " VALUES (?, ?, ?, ?, ?, ?)";

  private final JdbcTemplate jdbc;

  public UserIdentityStore(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  /** Returns the linked user id, or null when this identity has never been linked. */
  public UUID findUserId(String provider, String subject) {
    List<Map<String, Object>> rows = jdbc.queryForList(FIND, provider, subject);
    if (rows.isEmpty()) {
      return null;
    }
    Object value = rows.get(0).get("user_id");
    if (value instanceof UUID) {
      return (UUID) value;
    }
    return value == null ? null : UUID.fromString(String.valueOf(value));
  }

  /** Records a new link. Email and display name are informational only, never a join key. */
  public void link(
      String provider,
      String subject,
      UUID userId,
      String email,
      String displayName,
      Instant linkedAt) {
    jdbc.update(LINK, provider, subject, userId, email, displayName, Timestamp.from(linkedAt));
  }
}
