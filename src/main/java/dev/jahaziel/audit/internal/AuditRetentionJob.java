/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import dev.jahaziel.spi.PlatformJob;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Audit retention. Postgres drops monthly partitions older than the window; SQLite deletes old rows
 * in bounded batches by timestamp. Unknown dialects fail closed. The drop is logged by the caller
 * as an administrative system event.
 */
public final class AuditRetentionJob extends PlatformJob {
    private static final YearMonth FIRST_PARTITION = YearMonth.of(2026, 9);
    private static final int BATCH = 500;
    private static final String[] TABLES = {"audit_event", "audit_terminal", "audit_process"};

    private final EntityManager entities;
    private final int retentionDays;
    private final Clock clock;

    /**
     * Creates the job.
     *
     * @param entities the JPA entity manager for retention DDL/DML
     * @param retentionDays how long records survive
     * @param clock the clock
     */
    public AuditRetentionJob(EntityManager entities, int retentionDays, Clock clock) {
        this.entities = Objects.requireNonNull(entities, "entities");
        if (retentionDays < 1) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        this.retentionDays = retentionDays;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public void run(JobContext context) {
        AuditDialect.Kind kind = AuditDialect.of(entities);
        if (kind == AuditDialect.Kind.POSTGRES) {
            for (String partition : expiredPartitions()) {
                entities.createNativeQuery("DROP TABLE IF EXISTS " + partition).executeUpdate();
            }
        } else if (kind == AuditDialect.Kind.SQLITE) {
            Instant cutoff =
                    LocalDate.now(clock)
                            .minusDays(retentionDays)
                            .atStartOfDay(java.time.ZoneOffset.UTC)
                            .toInstant();
            for (int i = 0; i < TABLES.length; i++) {
                deleteExpired(TABLES[i], cutoff);
            }
        }
    }

    private void deleteExpired(String table, Instant cutoff) {
        String sql =
                "DELETE FROM "
                        + table
                        + " WHERE rowid IN (SELECT rowid FROM "
                        + table
                        + " WHERE timestamp < ?1 LIMIT ?2)";
        while (true) {
            Query query = entities.createNativeQuery(sql);
            query.setParameter(1, Timestamp.from(cutoff));
            query.setParameter(2, BATCH);
            if (query.executeUpdate() == 0) {
                return;
            }
        }
    }

    /**
     * Computes expired monthly partitions for the three audit tables.
     *
     * @return the partition names oldest first
     */
    List<String> expiredPartitions() {
        LocalDate cutoff = LocalDate.now(clock).minusDays(retentionDays);
        List<String> partitions = new ArrayList<>();
        YearMonth month = FIRST_PARTITION;
        while (!month.plusMonths(1).atDay(1).isAfter(cutoff)) {
            String suffix = String.format("%04d_%02d", month.getYear(), month.getMonthValue());
            partitions.add("audit_event_" + suffix);
            partitions.add("audit_terminal_" + suffix);
            partitions.add("audit_process_" + suffix);
            month = month.plusMonths(1);
        }
        return partitions;
    }

    @Override
    public Duration period() {
        return Duration.ofHours(24);
    }

    @Override
    public JobScope scope() {
        return JobScope.CLUSTER;
    }
}
