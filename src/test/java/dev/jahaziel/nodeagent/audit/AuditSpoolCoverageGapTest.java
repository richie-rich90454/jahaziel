/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.audit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The unreadable-spool branch of {@link AuditSpool}. */
class AuditSpoolCoverageGapTest {
  @Test
  void spooledSurfacesListFailure(@TempDir Path root) throws Exception {
    AuditSpool spool = new AuditSpool(root);
    spool.append(UUID.randomUUID(), "{\"a\":1}");
    AclFileAttributeView view = Files.getFileAttributeView(root, AclFileAttributeView.class);
    assumeTrue(view != null, "ACLs unsupported on " + root);
    List<AclEntry> original = new ArrayList<>(view.getAcl());
    UserPrincipal owner = Files.getOwner(root);
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
      UncheckedIOException failure = assertThrows(UncheckedIOException.class, spool::spooled);
      assertTrue(failure.getMessage().contains(root.toString()));
    } finally {
      try {
        view.setAcl(original);
      } catch (Exception ignored) {
        // Best effort; cleanup still runs.
      }
    }
  }
}
