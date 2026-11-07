/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.workspace.persistence.VirtualMachineEntity;
import dev.jahaziel.workspace.persistence.VirtualMachineRepository;
import dev.jahaziel.workspace.persistence.WorkspaceEntity;
import dev.jahaziel.workspace.persistence.WorkspaceRepository;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

class WorkspaceHomeMigrationTest {
  @TempDir Path temporary;

  @Test
  void copiesLegacyFilesIntoTheWorkspaceHomeAndRetainsTheSource() throws Exception {
    Fixture fixture = fixture();
    Files.createDirectories(fixture.legacyHome().resolve("src"));
    Files.writeString(fixture.legacyHome().resolve("src/main.c"), "int main(void) { return 0; }\n");

    fixture.migration().run(new DefaultApplicationArguments());

    assertEquals(
        "int main(void) { return 0; }\n",
        Files.readString(fixture.workspaceHome().resolve("src/main.c")));
    assertEquals(
        "int main(void) { return 0; }\n",
        Files.readString(fixture.legacyHome().resolve("src/main.c")));
    assertEquals(fixture.workspaceHome().toString(), fixture.machine().getHomePath());
    verify(fixture.machines()).save(fixture.machine());
  }

  @Test
  void fillsAnEmptyWorkspaceHomeFromTheLegacySource() throws Exception {
    Fixture fixture = fixture();
    Files.createDirectories(fixture.legacyHome());
    Files.writeString(fixture.legacyHome().resolve("README.md"), "legacy files\n");
    Files.createDirectories(fixture.workspaceHome());

    fixture.migration().run(new DefaultApplicationArguments());

    assertEquals("legacy files\n", Files.readString(fixture.workspaceHome().resolve("README.md")));
    assertTrue(Files.exists(fixture.legacyHome().resolve("README.md")));
  }

  @Test
  void mergesMissingFilesWithoutReplacingExistingWorkspaceFiles() throws Exception {
    Fixture fixture = fixture();
    Files.createDirectories(fixture.legacyHome().resolve("src"));
    Files.writeString(fixture.legacyHome().resolve("README.md"), "same file\n");
    Files.writeString(fixture.legacyHome().resolve("src/legacy.c"), "legacy source\n");
    Files.createDirectories(fixture.workspaceHome().resolve("src"));
    Files.writeString(fixture.workspaceHome().resolve("README.md"), "same file\n");
    Files.writeString(fixture.workspaceHome().resolve("src/current.c"), "workspace source\n");

    fixture.migration().run(new DefaultApplicationArguments());

    assertEquals("same file\n", Files.readString(fixture.workspaceHome().resolve("README.md")));
    assertEquals(
        "legacy source\n", Files.readString(fixture.workspaceHome().resolve("src/legacy.c")));
    assertEquals(
        "workspace source\n", Files.readString(fixture.workspaceHome().resolve("src/current.c")));
    assertTrue(Files.exists(fixture.legacyHome().resolve("src/legacy.c")));
  }

  @Test
  void refusesConflictingFilesWithoutChangingTheRecordedHome() throws Exception {
    Fixture fixture = fixture();
    Files.createDirectories(fixture.legacyHome());
    Files.writeString(fixture.legacyHome().resolve("README.md"), "legacy contents\n");
    Files.createDirectories(fixture.workspaceHome());
    Files.writeString(fixture.workspaceHome().resolve("README.md"), "workspace contents\n");

    UncheckedIOException failure =
        assertThrows(
            UncheckedIOException.class,
            () -> fixture.migration().run(new DefaultApplicationArguments()));

    assertTrue(failure.getCause().getMessage().contains("resolve it, then retry startup"));
    assertEquals("legacy contents\n", Files.readString(fixture.legacyHome().resolve("README.md")));
    assertEquals(
        "workspace contents\n", Files.readString(fixture.workspaceHome().resolve("README.md")));
    assertEquals(
        fixture.legacyHome().toString(),
        Path.of(fixture.machine().getHomePath()).toAbsolutePath().normalize().toString());
    verify(fixture.machines(), never()).save(fixture.machine());
  }

  private Fixture fixture() {
    Path storage = temporary.resolve("data");
    UUID ownerId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    WorkspaceEntity workspace = new WorkspaceEntity();
    workspace.setWorkspaceId(workspaceId);
    workspace.setOwnerUserId(ownerId);
    VirtualMachineEntity machine = new VirtualMachineEntity();
    machine.setWorkspaceId(workspaceId);
    machine.setHomePath(
        WorkspacePaths.legacyHomePath(
            storage.toString(), new dev.jahaziel.identity.domain.UserId(ownerId)));

    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    VirtualMachineRepository machines = mock(VirtualMachineRepository.class);
    when(workspaces.findAll()).thenReturn(List.of(workspace));
    when(machines.findAll()).thenReturn(List.of(machine));
    WorkspaceHomeMigration migration =
        new WorkspaceHomeMigration(workspaces, machines, storage.toString());
    return new Fixture(
        migration,
        machines,
        machine,
        Path.of(machine.getHomePath()),
        Path.of(
            WorkspacePaths.homePath(
                storage.toString(), new dev.jahaziel.workspace.domain.WorkspaceId(workspaceId))));
  }

  private record Fixture(
      WorkspaceHomeMigration migration,
      VirtualMachineRepository machines,
      VirtualMachineEntity machine,
      Path legacyHome,
      Path workspaceHome) {}
}
