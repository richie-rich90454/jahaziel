/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import dev.jahaziel.nodeagent.audit.AuditSpool;
import dev.jahaziel.nodeagent.sync.SyncServer;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.WorkspaceHandle;
import dev.jahaziel.spi.WorkspaceStorage;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The snapshot-listing failure branches of {@link NodeAgentService}. */
class NodeAgentServiceCoverageGapTest {
  @Test
  void listSnapshotsSurfacesIoFailure(@TempDir Path root) throws Exception {
    NodeAgentService service =
        new NodeAgentService(
            mock(NodeBackend.class),
            mock(WorkspaceStorage.class),
            new AuditSpool(root),
            mock(SyncServer.class));
    UUID workspace = UUID.randomUUID();
    Path home = root.resolve("home");
    Files.createDirectories(home);
    Path snaps = home.getParent().resolve("snaps").resolve(workspace.toString());
    Files.createDirectories(snaps);
    AclFileAttributeView view = Files.getFileAttributeView(snaps, AclFileAttributeView.class);
    assumeTrue(view != null, "ACLs unsupported on " + snaps);
    List<AclEntry> original = new ArrayList<>(view.getAcl());
    UserPrincipal owner = Files.getOwner(snaps);
    List<AclEntry> updated = new ArrayList<>(original);
    updated.add(
        0,
        AclEntry.newBuilder()
            .setType(AclEntryType.DENY)
            .setPrincipal(owner)
            .setPermissions(AclEntryPermission.LIST_DIRECTORY)
            .build());
    view.setAcl(updated);
    try {
      WorkspaceHandle handle = new WorkspaceHandle(workspace, home, root.resolve("jail"));
      UncheckedIOException failure =
          assertThrows(UncheckedIOException.class, () -> service.listSnapshots(handle));
      assertTrue(failure.getMessage().contains(workspace.toString()));
    } finally {
      try {
        view.setAcl(original);
      } catch (Exception ignored) {
        // Best effort; cleanup still runs.
      }
    }
  }

  @Test
  void modifiedAtFallsBackToNow(@TempDir Path root) throws Exception {
    Method modifiedAt = NodeAgentService.class.getDeclaredMethod("modifiedAt", Path.class);
    modifiedAt.setAccessible(true);
    Path missing = root.resolve("gone-" + System.nanoTime());
    Instant before = Instant.now();
    Instant fallback = (Instant) modifiedAt.invoke(null, missing);
    assertTrue(!fallback.isBefore(before.minusSeconds(5)));
    assertTrue(!fallback.isAfter(Instant.now().plusSeconds(5)));
  }
}
