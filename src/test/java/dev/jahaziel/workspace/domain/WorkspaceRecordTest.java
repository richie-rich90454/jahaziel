/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Edge cases of the workspace value types. */
class WorkspaceRecordTest {
  @Test
  void withoutGrantKeepsOtherPermissions() {
    UUID user = UUID.randomUUID();
    AccessControlList list =
        AccessControlList.empty().withGrant(user, "read").withGrant(user, "write");
    assertTrue(list.grants(user, "read"));
    AccessControlList kept = list.withoutGrant(user, "read");
    assertFalse(kept.grants(user, "read"));
    assertTrue(kept.grants(user, "write"));
    AccessControlList removed = kept.withoutGrant(user, "write");
    assertFalse(removed.grants(user, "write"));
    assertThrows(NullPointerException.class, () -> new AccessControlList(null));
    AccessControlList copied = new AccessControlList(Map.of(user, Set.of("read")));
    assertEquals(Set.of("read"), copied.grants().get(user));
  }

  @Test
  void runningGuardsHostname() {
    Instant at = Instant.now();
    VirtualMachineState.Running running = new VirtualMachineState.Running(at, "vm-1");
    assertEquals("vm-1", running.hostname());
    assertThrows(NullPointerException.class, () -> new VirtualMachineState.Running(null, "h"));
    assertThrows(IllegalArgumentException.class, () -> new VirtualMachineState.Running(at, null));
    assertThrows(IllegalArgumentException.class, () -> new VirtualMachineState.Running(at, "  "));
  }

  @Test
  void crashedGuardsReason() {
    Instant at = Instant.now();
    VirtualMachineState.Crashed crashed = new VirtualMachineState.Crashed(at, "panic");
    assertEquals("panic", crashed.reason());
    assertThrows(NullPointerException.class, () -> new VirtualMachineState.Crashed(null, "r"));
    assertThrows(IllegalArgumentException.class, () -> new VirtualMachineState.Crashed(at, null));
    assertThrows(IllegalArgumentException.class, () -> new VirtualMachineState.Crashed(at, "  "));
  }

  @Test
  void illegalStateExposesFields() {
    WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
    WorkspaceState state = new WorkspaceState.Requested(Instant.now());
    IllegalWorkspaceStateException failure =
        new IllegalWorkspaceStateException(workspaceId, state, "boot");
    assertEquals(workspaceId, failure.workspaceId());
    assertEquals(state, failure.state());
    assertEquals("boot", failure.action());
  }
}
