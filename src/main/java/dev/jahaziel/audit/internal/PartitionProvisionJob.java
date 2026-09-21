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

/** Provisions monthly audit partitions a few months ahead. Idempotent by IF NOT EXISTS. */
public final class PartitionProvisionJob implements PlatformJob {
  private final EntityManager entities;
  private final int monthsAhead;
  private final Clock clock;

  /**
   * Creates the job.
   *
   * @param entities the JPA entity manager for partition DDL
   * @param monthsAhead how many months to provision ahead
   * @param clock the clock
   */
  public PartitionProvisionJob(EntityManager entities, int monthsAhead, Clock clock) {
    this.entities = Objects.requireNonNull(entities, "entities");
    if (monthsAhead < 1) {
      throw new IllegalArgumentException("monthsAhead must be positive");
    }
    this.monthsAhead = monthsAhead;
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  @Transactional
  public void run(JobContext context) {
    if (!postgres()) {
      return;
    }
    for (String statement : provisionStatements()) {
      entities.createNativeQuery(statement).executeUpdate();
    }
  }

  /**
   * Computes the CREATE statements for the coming months.
   *
   * @return the statements oldest first
   */
  List<String> provisionStatements() {
    YearMonth current = YearMonth.from(LocalDate.now(clock));
    List<String> statements = new ArrayList<>();
    for (int i = 0; i < monthsAhead; i++) {
      YearMonth month = current.plusMonths(i);
      YearMonth next = month.plusMonths(1);
      String suffix = String.format("%04d_%02d", month.getYear(), month.getMonthValue());
      String from = String.format("%04d-%02d-01", month.getYear(), month.getMonthValue());
      String to = String.format("%04d-%02d-01", next.getYear(), next.getMonthValue());
      statements.add(
          "CREATE TABLE IF NOT EXISTS audit_event_"
              + suffix
              + " PARTITION OF audit_event FOR VALUES FROM ('"
              + from
              + "') TO ('"
              + to
              + "')");
      statements.add(
          "CREATE TABLE IF NOT EXISTS audit_terminal_"
              + suffix
              + " PARTITION OF audit_terminal FOR VALUES FROM ('"
              + from
              + "') TO ('"
              + to
              + "')");
      statements.add(
          "CREATE TABLE IF NOT EXISTS audit_process_"
              + suffix
              + " PARTITION OF audit_process FOR VALUES FROM ('"
              + from
              + "') TO ('"
              + to
              + "')");
    }
    return statements;
  }

  @Override
  public Duration period() {
    return Duration.ofHours(24);
  }

  /**
   * Returns whether the database partitions audit tables. Only Postgres does; SQLite keeps plain
   * tables. Unknown environments keep the legacy behavior so existing tests and deployments are
   * unaffected.
   *
   * @return true when partition DDL may run
   */
  private boolean postgres() {
    try {
      Object dialect = entities.getEntityManagerFactory().getProperties().get("hibernate.dialect");
      if (dialect != null) {
        return dialect.toString().toLowerCase(java.util.Locale.ROOT).contains("postgres");
      }
    } catch (RuntimeException ignored) {
      // Fall through to the connection check below.
    }
    try {
      java.sql.Connection connection = entities.unwrap(java.sql.Connection.class);
      return connection == null || connection.getMetaData().getURL().startsWith("jdbc:postgresql:");
    } catch (Exception ignored) {
      return true;
    }
  }

  @Override
  public JobScope scope() {
    return JobScope.CLUSTER;
  }
}
