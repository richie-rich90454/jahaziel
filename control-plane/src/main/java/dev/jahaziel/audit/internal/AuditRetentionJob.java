/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import dev.jahaziel.platform.api.JobContext;
import dev.jahaziel.platform.api.JobScope;
import dev.jahaziel.platform.api.PlatformJob;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit retention. Drops monthly partitions older than the retention window; the drop itself is
 * logged by the caller as an administrative system event. Never row-deletes.
 */
public final class AuditRetentionJob implements PlatformJob {
  private static final YearMonth FIRST_PARTITION = YearMonth.of(2026, 9);

  private final EntityManager entities;
  private final int retentionDays;
  private final Clock clock;

  /**
   * Creates the job.
   *
   * @param entities the JPA entity manager for partition DDL
   * @param retentionDays how long partitions survive
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
    for (String partition : expiredPartitions()) {
      entities.createNativeQuery("DROP TABLE IF EXISTS " + partition).executeUpdate();
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
    YearMonth last = YearMonth.from(cutoff);
    while (!month.isAfter(last)) {
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
