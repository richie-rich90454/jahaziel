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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

class WorkspaceHomeMigrationTest {
    @TempDir Path temporary;

    @Test
    void copiesLegacyFilesIntoTheWorkspaceHomeAndRetainsTheSource() throws Exception {
        Fixture fixture = fixture();
        Files.createDirectories(fixture.legacyHome().resolve("src"));
        Files.writeString(
                fixture.legacyHome().resolve("src/main.c"), "int main(void) { return 0; }\n");

        fixture.migration().run(new DefaultApplicationArguments());

        assertEquals(
                "int main(void) { return 0; }\n",
                Files.readString(fixture.workspaceHome().resolve("src/main.c")));
        assertEquals(
                "int main(void) { return 0; }\n",
                Files.readString(fixture.legacyHome().resolve("src/main.c")));
        assertEquals(
                fixture.workspaceHome().toRealPath().toString(),
                Path.of(fixture.machine().getHomePath()).toRealPath().toString());
        verify(fixture.machines()).save(fixture.machine());
    }

    @Test
    void fillsAnEmptyWorkspaceHomeFromTheLegacySource() throws Exception {
        Fixture fixture = fixture();
        Files.createDirectories(fixture.legacyHome());
        Files.writeString(fixture.legacyHome().resolve("README.md"), "legacy files\n");
        Files.createDirectories(fixture.workspaceHome());

        fixture.migration().run(new DefaultApplicationArguments());

        assertEquals(
                "legacy files\n", Files.readString(fixture.workspaceHome().resolve("README.md")));
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
                "legacy source\n",
                Files.readString(fixture.workspaceHome().resolve("src/legacy.c")));
        assertEquals(
                "workspace source\n",
                Files.readString(fixture.workspaceHome().resolve("src/current.c")));
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
                        new org.junit.jupiter.api.function.Executable() {
                            @Override
                            public void execute() throws Throwable {
                                fixture.migration().run(new DefaultApplicationArguments());
                            }
                        });

        assertTrue(failure.getCause().getMessage().contains("resolve it, then retry startup"));
        assertEquals(
                "legacy contents\n", Files.readString(fixture.legacyHome().resolve("README.md")));
        assertEquals(
                "workspace contents\n",
                Files.readString(fixture.workspaceHome().resolve("README.md")));
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
                                storage.toString(),
                                new dev.jahaziel.workspace.domain.WorkspaceId(workspaceId))));
    }

    private static final class Fixture {
        private final WorkspaceHomeMigration migration;
        private final VirtualMachineRepository machines;
        private final VirtualMachineEntity machine;
        private final Path legacyHome;
        private final Path workspaceHome;

        public Fixture(
                WorkspaceHomeMigration migration,
                VirtualMachineRepository machines,
                VirtualMachineEntity machine,
                Path legacyHome,
                Path workspaceHome) {
            this.migration = migration;
            this.machines = machines;
            this.machine = machine;
            this.legacyHome = legacyHome;
            this.workspaceHome = workspaceHome;
        }

        public WorkspaceHomeMigration migration() {
            return migration;
        }

        public VirtualMachineRepository machines() {
            return machines;
        }

        public VirtualMachineEntity machine() {
            return machine;
        }

        public Path legacyHome() {
            return legacyHome;
        }

        public Path workspaceHome() {
            return workspaceHome;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Fixture other = (Fixture) o;
            return Objects.equals(migration, other.migration)
                    && Objects.equals(machines, other.machines)
                    && Objects.equals(machine, other.machine)
                    && Objects.equals(legacyHome, other.legacyHome)
                    && Objects.equals(workspaceHome, other.workspaceHome);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(migration);
            result = 31 * result + Objects.hashCode(machines);
            result = 31 * result + Objects.hashCode(machine);
            result = 31 * result + Objects.hashCode(legacyHome);
            result = 31 * result + Objects.hashCode(workspaceHome);
            return result;
        }

        @Override
        public String toString() {
            return "Fixture{"
                    + "migration="
                    + migration
                    + ", machines="
                    + machines
                    + ", machine="
                    + machine
                    + ", legacyHome="
                    + legacyHome
                    + ", workspaceHome="
                    + workspaceHome
                    + '}';
        }
    }
}
