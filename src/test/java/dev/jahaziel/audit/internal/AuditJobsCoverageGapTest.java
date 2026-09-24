/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditJobsCoverageGapTest {
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC);

  private EntityManager entitiesWithDialect(String dialect) {
    EntityManager entities = mock(EntityManager.class);
    EntityManagerFactory factory = mock(EntityManagerFactory.class);
    if (dialect == null) {
      when(factory.getProperties()).thenReturn(Map.of());
    } else {
      when(factory.getProperties()).thenReturn(Map.of("hibernate.dialect", dialect));
    }
    when(entities.getEntityManagerFactory()).thenReturn(factory);
    return entities;
  }

  private EntityManager postgresEntities() {
    return entitiesWithDialect("org.hibernate.dialect.PostgreSQLDialect");
  }

  @Test
  void retentionCtorRejectsNonPositive() {
    EntityManager entities = mock(EntityManager.class);
    assertThrows(IllegalArgumentException.class, () -> new AuditRetentionJob(entities, 0, clock));
    assertThrows(IllegalArgumentException.class, () -> new AuditRetentionJob(entities, -2, clock));
  }

  @Test
  void provisionCtorRejectsNonPositive() {
    EntityManager entities = mock(EntityManager.class);
    assertThrows(
        IllegalArgumentException.class, () -> new PartitionProvisionJob(entities, 0, clock));
    assertThrows(
        IllegalArgumentException.class, () -> new PartitionProvisionJob(entities, -1, clock));
  }

  @Test
  void retentionPeriodAndScope() {
    AuditRetentionJob job = new AuditRetentionJob(mock(EntityManager.class), 30, clock);
    assertEquals(Duration.ofHours(24), job.period());
    assertEquals(JobScope.CLUSTER, job.scope());
  }

  @Test
  void provisionPeriodAndScope() {
    PartitionProvisionJob job = new PartitionProvisionJob(mock(EntityManager.class), 3, clock);
    assertEquals(Duration.ofHours(24), job.period());
    assertEquals(JobScope.CLUSTER, job.scope());
  }

  @Test
  void retentionRunsOnPostgresDialect() {
    EntityManager entities = postgresEntities();
    Query query = mock(Query.class);
    when(entities.createNativeQuery(anyString())).thenReturn(query);
    new AuditRetentionJob(entities, 30, clock).run(new JobContext(Instant.now()));
    verify(entities, times(3)).createNativeQuery(anyString());
  }

  @Test
  void provisionRunsOnPostgresDialect() {
    EntityManager entities = postgresEntities();
    Query query = mock(Query.class);
    when(entities.createNativeQuery(anyString())).thenReturn(query);
    new PartitionProvisionJob(entities, 1, clock).run(new JobContext(Instant.now()));
    verify(entities, times(3)).createNativeQuery(anyString());
  }

  @Test
  void retentionFallsBackToConnectionCheck() throws Exception {
    EntityManager entities = mock(EntityManager.class);
    EntityManagerFactory factory = mock(EntityManagerFactory.class);
    when(factory.getProperties()).thenReturn(Map.of());
    when(entities.getEntityManagerFactory()).thenReturn(factory);
    when(entities.unwrap(Connection.class)).thenReturn(null);
    Query query = mock(Query.class);
    when(entities.createNativeQuery(anyString())).thenReturn(query);
    new AuditRetentionJob(entities, 30, clock).run(new JobContext(Instant.now()));
    verify(entities, times(3)).createNativeQuery(anyString());
  }

  @Test
  void retentionHonorsConnectionUrl() throws Exception {
    EntityManager entities = mock(EntityManager.class);
    EntityManagerFactory factory = mock(EntityManagerFactory.class);
    when(factory.getProperties()).thenReturn(Map.of());
    when(entities.getEntityManagerFactory()).thenReturn(factory);
    Connection postgres = mock(Connection.class);
    DatabaseMetaData pgMeta = mock(DatabaseMetaData.class);
    when(pgMeta.getURL()).thenReturn("jdbc:postgresql://localhost/x");
    when(postgres.getMetaData()).thenReturn(pgMeta);
    when(entities.unwrap(Connection.class)).thenReturn(postgres);
    Query query = mock(Query.class);
    when(entities.createNativeQuery(anyString())).thenReturn(query);
    new AuditRetentionJob(entities, 30, clock).run(new JobContext(Instant.now()));
    verify(entities, times(3)).createNativeQuery(anyString());
    Connection sqlite = mock(Connection.class);
    DatabaseMetaData liteMeta = mock(DatabaseMetaData.class);
    when(liteMeta.getURL()).thenReturn("jdbc:sqlite:file:mem");
    when(sqlite.getMetaData()).thenReturn(liteMeta);
    EntityManager other = mock(EntityManager.class);
    when(other.getEntityManagerFactory()).thenReturn(factory);
    when(other.unwrap(Connection.class)).thenReturn(sqlite);
    new AuditRetentionJob(other, 30, clock).run(new JobContext(Instant.now()));
    verify(other, never()).createNativeQuery(anyString());
  }

  @Test
  void retentionFactoryFailureFallsThrough() {
    EntityManager entities = mock(EntityManager.class);
    when(entities.getEntityManagerFactory()).thenThrow(new RuntimeException("gone"));
    Query query = mock(Query.class);
    when(entities.createNativeQuery(anyString())).thenReturn(query);
    try {
      when(entities.unwrap(Connection.class)).thenReturn(null);
    } catch (Exception ignored) {
      // Stubbing only.
    }
    new AuditRetentionJob(entities, 30, clock).run(new JobContext(Instant.now()));
    verify(entities, times(3)).createNativeQuery(anyString());
  }

  @Test
  void retentionUnwrapFailureKeepsLegacyBehavior() throws Exception {
    EntityManager entities = mock(EntityManager.class);
    EntityManagerFactory factory = mock(EntityManagerFactory.class);
    when(factory.getProperties()).thenReturn(Map.of());
    when(entities.getEntityManagerFactory()).thenReturn(factory);
    when(entities.unwrap(Connection.class)).thenThrow(new RuntimeException("gone"));
    Query query = mock(Query.class);
    when(entities.createNativeQuery(anyString())).thenReturn(query);
    new AuditRetentionJob(entities, 30, clock).run(new JobContext(Instant.now()));
    verify(entities, times(3)).createNativeQuery(anyString());
  }

  @Test
  void provisionFallsBackToConnectionCheck() throws Exception {
    EntityManager entities = mock(EntityManager.class);
    EntityManagerFactory factory = mock(EntityManagerFactory.class);
    when(factory.getProperties()).thenReturn(Map.of());
    when(entities.getEntityManagerFactory()).thenReturn(factory);
    when(entities.unwrap(Connection.class)).thenReturn(null);
    Query query = mock(Query.class);
    when(entities.createNativeQuery(anyString())).thenReturn(query);
    new PartitionProvisionJob(entities, 1, clock).run(new JobContext(Instant.now()));
    verify(entities, times(3)).createNativeQuery(anyString());
    Connection sqlite = mock(Connection.class);
    DatabaseMetaData liteMeta = mock(DatabaseMetaData.class);
    when(liteMeta.getURL()).thenReturn("jdbc:sqlite:file:mem");
    when(sqlite.getMetaData()).thenReturn(liteMeta);
    EntityManager other = mock(EntityManager.class);
    when(other.getEntityManagerFactory()).thenReturn(factory);
    when(other.unwrap(Connection.class)).thenReturn(sqlite);
    new PartitionProvisionJob(other, 1, clock).run(new JobContext(Instant.now()));
    verify(other, never()).createNativeQuery(anyString());
  }

  @Test
  void provisionFactoryAndUnwrapFailuresKeepLegacy() {
    EntityManager broken = mock(EntityManager.class);
    when(broken.getEntityManagerFactory()).thenThrow(new RuntimeException("gone"));
    Query query = mock(Query.class);
    when(broken.createNativeQuery(anyString())).thenReturn(query);
    new PartitionProvisionJob(broken, 1, clock).run(new JobContext(Instant.now()));
    verify(broken, times(3)).createNativeQuery(anyString());
    EntityManager failing = mock(EntityManager.class);
    EntityManagerFactory factory = mock(EntityManagerFactory.class);
    when(factory.getProperties()).thenReturn(Map.of());
    when(failing.getEntityManagerFactory()).thenReturn(factory);
    when(failing.unwrap(Connection.class)).thenThrow(new RuntimeException("gone"));
    when(failing.createNativeQuery(anyString())).thenReturn(query);
    new PartitionProvisionJob(failing, 1, clock).run(new JobContext(Instant.now()));
    verify(failing, times(3)).createNativeQuery(anyString());
  }

  @Test
  void retentionFindsNothingBeforeFirstPartition() {
    EntityManager entities = postgresEntities();
    Query query = mock(Query.class);
    when(entities.createNativeQuery(anyString())).thenReturn(query);
    Clock early = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
    AuditRetentionJob job = new AuditRetentionJob(entities, 30, early);
    assertTrue(job.expiredPartitions().isEmpty());
    job.run(new JobContext(Instant.now()));
    verify(entities, never()).createNativeQuery(anyString());
  }
}
