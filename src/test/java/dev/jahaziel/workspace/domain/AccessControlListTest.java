/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link AccessControlList}. */
class AccessControlListTest {
  @Test
  void emptyGrantsNothing() {
    assertFalse(AccessControlList.empty().grants(UUID.randomUUID(), "read"));
  }

  @Test
  void grantAddsPermission() {
    UUID user = UUID.randomUUID();
    AccessControlList acl = AccessControlList.empty().withGrant(user, "read");
    assertTrue(acl.grants(user, "read"));
    assertFalse(acl.grants(user, "write"));
  }

  @Test
  void revokeRemovesPermission() {
    UUID user = UUID.randomUUID();
    AccessControlList acl =
        AccessControlList.empty().withGrant(user, "read").withoutGrant(user, "read");
    assertFalse(acl.grants(user, "read"));
  }

  @Test
  void updatesReturnNewInstances() {
    UUID user = UUID.randomUUID();
    AccessControlList empty = AccessControlList.empty();
    empty.withGrant(user, "read");
    assertFalse(empty.grants(user, "read"));
  }

  @Test
  void nullGrantsAreRejected() {
    assertThrows(NullPointerException.class, () -> new AccessControlList(null));
  }

  @Test
  void storedGrantsAreCopied() {
    UUID user = UUID.randomUUID();
    Map<UUID, Set<String>> grants = new HashMap<>();
    grants.put(user, new HashSet<>(Set.of("read")));
    AccessControlList acl = new AccessControlList(grants);
    grants.get(user).add("admin");
    assertFalse(acl.grants(user, "admin"));
  }
}
