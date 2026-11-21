/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import jakarta.persistence.EntityManager;

import java.util.Locale;

/** Best-effort database dialect detection. Unknown environments fail closed. */
final class AuditDialect {
    enum Kind {
        POSTGRES,
        SQLITE,
        UNKNOWN
    }

    private AuditDialect() {}

    static Kind of(EntityManager entities) {
        try {
            Object dialect =
                    entities.getEntityManagerFactory().getProperties().get("hibernate.dialect");
            if (dialect != null) {
                String name = dialect.toString().toLowerCase(Locale.ROOT);
                if (name.contains("postgres")) {
                    return Kind.POSTGRES;
                }
                if (name.contains("sqlite")) {
                    return Kind.SQLITE;
                }
                return Kind.UNKNOWN;
            }
        } catch (RuntimeException ignored) {
            // Fall through to the connection lookup.
        }
        try {
            java.sql.Connection connection = entities.unwrap(java.sql.Connection.class);
            if (connection == null) {
                return Kind.UNKNOWN;
            }
            String url = connection.getMetaData().getURL();
            if (url == null) {
                return Kind.UNKNOWN;
            }
            if (url.startsWith("jdbc:postgresql:")) {
                return Kind.POSTGRES;
            }
            if (url.startsWith("jdbc:sqlite:")) {
                return Kind.SQLITE;
            }
        } catch (Exception ignored) {
            return Kind.UNKNOWN;
        }
        return Kind.UNKNOWN;
    }
}
