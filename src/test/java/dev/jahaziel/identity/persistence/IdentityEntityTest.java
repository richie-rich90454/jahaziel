/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Coverage of the identity persistence entities and their composite keys. */
class IdentityEntityTest {
  @Test
  void appUserStoresAllFields() {
    AppUserEntity entity = new AppUserEntity();
    UUID userId = UUID.randomUUID();
    entity.setUserId(userId);
    entity.setExternalId("sub-1");
    entity.setEmail("user@example.com");
    entity.setDisplayName("User");
    entity.setCreatedAt(Instant.EPOCH);
    entity.setLastLoginAt(Instant.now());
    entity.setStatus("ACTIVE");
    entity.setMfaEnabled(true);
    assertEquals(userId, entity.getUserId());
    assertEquals("sub-1", entity.getExternalId());
    assertEquals("user@example.com", entity.getEmail());
    assertEquals("User", entity.getDisplayName());
    assertEquals(Instant.EPOCH, entity.getCreatedAt());
    assertEquals("ACTIVE", entity.getStatus());
    assertTrue(entity.isMfaEnabled());
  }

  @Test
  void appUserEqualsById() {
    UUID userId = UUID.randomUUID();
    AppUserEntity first = new AppUserEntity();
    first.setUserId(userId);
    first.setEmail("a@example.com");
    AppUserEntity second = new AppUserEntity();
    second.setUserId(userId);
    second.setEmail("b@example.com");
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setUserId(UUID.randomUUID());
    assertFalse(first.equals(second));
  }

  @Test
  void bffSessionStoresAllFields() {
    BffSessionEntity entity = new BffSessionEntity();
    UUID userId = UUID.randomUUID();
    entity.setSessionId("sess-1");
    entity.setUserId(userId);
    entity.setCreatedAt(Instant.EPOCH);
    entity.setExpiresAt(Instant.now());
    assertEquals("sess-1", entity.getSessionId());
    assertEquals(userId, entity.getUserId());
    assertEquals(Instant.EPOCH, entity.getCreatedAt());
  }

  @Test
  void bffSessionEqualsById() {
    BffSessionEntity first = new BffSessionEntity();
    first.setSessionId("sess-1");
    BffSessionEntity second = new BffSessionEntity();
    second.setSessionId("sess-1");
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setSessionId("sess-2");
    assertFalse(first.equals(second));
  }

  @Test
  void oidcPendingStoresAllFields() {
    OidcPendingEntity entity = new OidcPendingEntity();
    entity.setState("state-1");
    entity.setNonce("nonce-1");
    entity.setCodeVerifier("verifier");
    entity.setRedirectUri("https://app.example.com/callback");
    entity.setCreatedAt(Instant.EPOCH);
    entity.setExpiresAt(Instant.now());
    assertEquals("state-1", entity.getState());
    assertEquals("nonce-1", entity.getNonce());
    assertEquals("verifier", entity.getCodeVerifier());
    assertEquals("https://app.example.com/callback", entity.getRedirectUri());
    assertEquals(Instant.EPOCH, entity.getCreatedAt());
  }

  @Test
  void oidcPendingEqualsById() {
    OidcPendingEntity first = new OidcPendingEntity();
    first.setState("state-1");
    OidcPendingEntity second = new OidcPendingEntity();
    second.setState("state-1");
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setState("state-2");
    assertFalse(first.equals(second));
  }

  @Test
  void userGroupStoresFieldsAndCompares() {
    UserGroupEntity entity = new UserGroupEntity();
    UserGroupEntity.Id id = new UserGroupEntity.Id(UUID.randomUUID(), "devs");
    entity.setId(id);
    entity.setAddedAt(Instant.EPOCH);
    assertEquals(id, entity.getId());
    assertEquals(Instant.EPOCH, entity.getAddedAt());
    UserGroupEntity same = new UserGroupEntity();
    same.setId(id);
    assertTrue(entity.equals(entity));
    assertTrue(entity.equals(same));
    assertEquals(entity.hashCode(), same.hashCode());
    assertFalse(entity.equals(null));
    assertFalse(entity.equals((Object) "other"));
    UserGroupEntity different = new UserGroupEntity();
    different.setId(new UserGroupEntity.Id(UUID.randomUUID(), "devs"));
    assertFalse(entity.equals(different));
  }

  @Test
  void userGroupIdStoresFieldsAndCompares() {
    UUID userId = UUID.randomUUID();
    UserGroupEntity.Id id = new UserGroupEntity.Id();
    id.setUserId(userId);
    id.setGroupName("devs");
    assertEquals(userId, id.getUserId());
    assertEquals("devs", id.getGroupName());
    UserGroupEntity.Id same = new UserGroupEntity.Id(userId, "devs");
    assertTrue(id.equals(id));
    assertTrue(id.equals(same));
    assertEquals(id.hashCode(), same.hashCode());
    assertFalse(id.equals(null));
    assertFalse(id.equals((Object) "other"));
    assertFalse(id.equals(new UserGroupEntity.Id(UUID.randomUUID(), "devs")));
    assertFalse(id.equals(new UserGroupEntity.Id(userId, "ops")));
  }

  @Test
  void userRoleStoresFieldsAndCompares() {
    UserRoleEntity entity = new UserRoleEntity();
    UserRoleEntity.Id id = new UserRoleEntity.Id(UUID.randomUUID(), "admin");
    entity.setId(id);
    entity.setGrantedAt(Instant.EPOCH);
    entity.setGrantedBy("root");
    assertEquals(id, entity.getId());
    assertEquals(Instant.EPOCH, entity.getGrantedAt());
    assertEquals("root", entity.getGrantedBy());
    UserRoleEntity same = new UserRoleEntity();
    same.setId(id);
    assertTrue(entity.equals(entity));
    assertTrue(entity.equals(same));
    assertEquals(entity.hashCode(), same.hashCode());
    assertFalse(entity.equals(null));
    assertFalse(entity.equals((Object) "other"));
    UserRoleEntity different = new UserRoleEntity();
    different.setId(new UserRoleEntity.Id(UUID.randomUUID(), "admin"));
    assertFalse(entity.equals(different));
  }

  @Test
  void userRoleIdStoresFieldsAndCompares() {
    UUID userId = UUID.randomUUID();
    UserRoleEntity.Id id = new UserRoleEntity.Id();
    id.setUserId(userId);
    id.setRole("admin");
    assertEquals(userId, id.getUserId());
    assertEquals("admin", id.getRole());
    UserRoleEntity.Id same = new UserRoleEntity.Id(userId, "admin");
    assertTrue(id.equals(id));
    assertTrue(id.equals(same));
    assertEquals(id.hashCode(), same.hashCode());
    assertFalse(id.equals(null));
    assertFalse(id.equals((Object) "other"));
    assertFalse(id.equals(new UserRoleEntity.Id(UUID.randomUUID(), "admin")));
    assertFalse(id.equals(new UserRoleEntity.Id(userId, "viewer")));
  }
}
