/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.audit.persistence.StringArrayConverter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Final gap tests: partition postgres detection and unterminated JSON arrays. */
class AuditFinalTest {
  @Test
  void converterRejectsUnterminated() {
    StringArrayConverter converter = new StringArrayConverter();
    assertThrows(
        IllegalArgumentException.class, () -> converter.convertToEntityAttribute("[\"abc"));
    assertThrows(
        IllegalArgumentException.class, () -> converter.convertToEntityAttribute("[\"abc\\"));
    assertEquals(1, converter.convertToEntityAttribute("[\"a\"]").length);
  }

  private record Fixture(PartitionProvisionJob job, EntityManager entities) {}

  private static Fixture fixture(Map<String, Object> properties, Connection connection)
      throws Exception {
    EntityManager entities = mock(EntityManager.class);
    EntityManagerFactory factory = mock(EntityManagerFactory.class);
    when(entities.getEntityManagerFactory()).thenReturn(factory);
    if (properties == null) {
      when(factory.getProperties()).thenThrow(new RuntimeException("no factory"));
    } else {
      when(factory.getProperties()).thenReturn(properties);
    }
    when(entities.unwrap(Connection.class)).thenReturn(connection);
    Query query = mock(Query.class);
    when(entities.createNativeQuery(anyString())).thenReturn(query);
    PartitionProvisionJob job =
        new PartitionProvisionJob(
            entities, 1, Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC));
    return new Fixture(job, entities);
  }

  private static Map<String, Object> dialect(String name) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("hibernate.dialect", name);
    return properties;
  }

  private static Connection connection(String url) throws Exception {
    Connection connection = mock(Connection.class);
    DatabaseMetaData meta = mock(DatabaseMetaData.class);
    when(connection.getMetaData()).thenReturn(meta);
    when(meta.getURL()).thenReturn(url);
    return connection;
  }

  @Test
  void runSkipsNonPostgresDialect() throws Exception {
    fixture(dialect("org.hibernate.dialect.H2Dialect"), null).job().run(null);
  }

  @Test
  void runUsesLiveConnection() throws Exception {
    fixture(new HashMap<>(), null).job().run(null);
    fixture(new HashMap<>(), connection("jdbc:postgresql://db:5432/j")).job().run(null);
    fixture(new HashMap<>(), connection("jdbc:sqlite:/tmp/j.db")).job().run(null);
  }

  @Test
  void runFallsBackWhenFactoryThrows() throws Exception {
    fixture(null, connection("jdbc:postgresql://db:5432/j")).job().run(null);
  }

  @Test
  void runUsesPostgresDialect() throws Exception {
    fixture(dialect("org.hibernate.dialect.PostgreSQLDialect"), null).job().run(null);
  }
}
