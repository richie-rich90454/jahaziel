/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jahaziel.identity.domain.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link AppUser}. */
class AppUserTest {
  private final UserId id = new UserId(UUID.randomUUID());

  @Test
  void storesFields() {
    AppUser user = new AppUser(id, "sub", "a@b.c", "A", List.of("r"), List.of("g"), true);
    assertEquals(id, user.id());
    assertEquals("sub", user.externalId());
    assertEquals("a@b.c", user.email());
    assertEquals(List.of("r"), user.roles());
    assertEquals(List.of("g"), user.groups());
  }

  @Test
  void collectionsAreCopied() {
    ArrayList<String> roles = new ArrayList<>(List.of("r"));
    ArrayList<String> groups = new ArrayList<>(List.of("g"));
    AppUser user = new AppUser(id, "sub", "a@b.c", "A", roles, groups, false);
    roles.add("evil");
    groups.add("evil");
    assertEquals(List.of("r"), user.roles());
    assertEquals(List.of("g"), user.groups());
  }

  @Test
  void nullsAreRejected() {
    assertThrows(
        NullPointerException.class,
        () -> new AppUser(null, "s", "e", "d", List.of(), List.of(), false));
    assertThrows(
        NullPointerException.class,
        () -> new AppUser(id, "s", "e", "d", null, List.of(), false));
    assertThrows(
        NullPointerException.class,
        () -> new AppUser(id, "s", "e", "d", List.of(), null, false));
  }
}
