/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.persistence;

import dev.jahaziel.club.domain.ClubSession;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC persistence for the club tables created by {@code V20261119a__club.sql}.
 *
 * <p>This is additive: the tables are independent of existing platform tables, and the store never
 * writes to a workspace. Git remains the source of truth for project content.
 */
public final class ClubSessionStore {
  private final JdbcTemplate jdbc;

  /**
   * Creates the store.
   *
   * @param dataSource the platform data source
   */
  public ClubSessionStore(DataSource dataSource) {
    this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
  }

  /**
   * Inserts or updates one session row.
   *
   * @param session the session
   */
  public void save(ClubSession session) {
    Objects.requireNonNull(session, "session");
    jdbc.update(
        "INSERT INTO club_session (session_id, host_user_id, template_name, starts_at, ends_at, "
            + "join_code, capacity, locked, leaderboard_enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT (session_id) DO UPDATE SET host_user_id = excluded.host_user_id, "
            + "template_name = excluded.template_name, starts_at = excluded.starts_at, "
            + "ends_at = excluded.ends_at, join_code = excluded.join_code, "
            + "capacity = excluded.capacity, locked = excluded.locked, "
            + "leaderboard_enabled = excluded.leaderboard_enabled",
        session.id(),
        session.host().value().toString(),
        session.template(),
        Timestamp.from(session.startsAt()),
        Timestamp.from(session.endsAt()),
        session.joinCode(),
        Integer.valueOf(session.capacity()),
        Boolean.valueOf(session.isLocked()),
        Boolean.valueOf(session.isLeaderboardEnabled()));
  }

  /**
   * Returns all stored session rows.
   *
   * @return the rows, empty when no session exists
   */
  public List<Map<String, Object>> all() {
    return jdbc.queryForList("SELECT * FROM club_session");
  }

  /**
   * Deletes one session row.
   *
   * @param sessionId the session identifier
   * @return the number of removed rows
   */
  public int delete(String sessionId) {
    return jdbc.update("DELETE FROM club_session WHERE session_id = ?", sessionId);
  }
}
