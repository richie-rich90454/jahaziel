/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.spi.JobContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Behavior of the audit partition jobs with a stubbed entity manager. */
class AuditPartitionJobTest {
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void retentionDropsExpiredPartitions() {
    EntityManager entities = mock(EntityManager.class);
    Query query = mock(Query.class);
    when(entities.createNativeQuery(anyString())).thenReturn(query);
    AuditRetentionJob job = new AuditRetentionJob(entities, 30, clock);
    job.run(new JobContext(Instant.now()));
    verify(entities).createNativeQuery("DROP TABLE IF EXISTS audit_event_2026_09");
    verify(entities).createNativeQuery("DROP TABLE IF EXISTS audit_terminal_2026_09");
    verify(entities, times(3)).createNativeQuery(anyString());
    assertTrue(job.expiredPartitions().contains("audit_process_2026_09"));
  }

  @Test
  void provisionCreatesComingMonths() {
    EntityManager entities = mock(EntityManager.class);
    Query query = mock(Query.class);
    when(entities.createNativeQuery(anyString())).thenReturn(query);
    PartitionProvisionJob job = new PartitionProvisionJob(entities, 3, clock);
    job.run(new JobContext(Instant.now()));
    verify(entities, times(9)).createNativeQuery(anyString());
    assertEquals(9, job.provisionStatements().size());
    assertTrue(
        job.provisionStatements()
            .get(0)
            .startsWith("CREATE TABLE IF NOT EXISTS audit_event_2026_11 PARTITION OF audit_event"));
  }

  @Test
  void retentionSkipsNonPostgres() {
    EntityManager entities = mock(EntityManager.class);
    EntityManagerFactory factory = mock(EntityManagerFactory.class);
    when(factory.getProperties())
        .thenReturn(Map.of("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect"));
    when(entities.getEntityManagerFactory()).thenReturn(factory);
    new AuditRetentionJob(entities, 30, clock).run(new JobContext(Instant.now()));
    verify(entities, times(0)).createNativeQuery(anyString());
  }

  @Test
  void provisionSkipsNonPostgres() {
    EntityManager entities = mock(EntityManager.class);
    EntityManagerFactory factory = mock(EntityManagerFactory.class);
    when(factory.getProperties())
        .thenReturn(Map.of("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect"));
    when(entities.getEntityManagerFactory()).thenReturn(factory);
    new PartitionProvisionJob(entities, 3, clock).run(new JobContext(Instant.now()));
    verify(entities, times(0)).createNativeQuery(anyString());
  }
}
