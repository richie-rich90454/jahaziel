/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.wiring;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Looks up and creates the durable (provider, subject) to local user link. */
public final class UserIdentityStore {
    private static final String FIND =
            "SELECT user_id FROM user_identity WHERE provider = ? AND subject = ?";
    private static final String LINK =
            "INSERT INTO user_identity (provider, subject, user_id, email, display_name, linked_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?)";
    private static final String CREATE =
            "CREATE TABLE IF NOT EXISTS user_identity ("
                    + "provider TEXT NOT NULL, subject TEXT NOT NULL, user_id TEXT NOT NULL,"
                    + " email TEXT, display_name TEXT, linked_at TEXT NOT NULL,"
                    + " PRIMARY KEY (provider, subject))";

    private final JdbcTemplate jdbc;

    public UserIdentityStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        // The Flyway migration owns this table in deployment; tests disable Flyway and use
        // JPA ddl-auto, which does not know about raw JDBC tables.
        this.jdbc.execute(CREATE);
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
