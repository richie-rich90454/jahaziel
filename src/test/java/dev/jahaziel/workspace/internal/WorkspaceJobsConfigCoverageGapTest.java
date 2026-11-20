/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.nodeagent.service.LocalProcessBackend;
import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.UserIdentity;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import dev.jahaziel.workspace.domain.WorkspaceState;
import dev.jahaziel.workspace.persistence.VirtualMachineEntity;
import dev.jahaziel.workspace.persistence.VirtualMachineRepository;
import dev.jahaziel.workspace.persistence.WorkspaceAclRepository;
import dev.jahaziel.workspace.persistence.WorkspaceEntity;
import dev.jahaziel.workspace.persistence.WorkspaceRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class WorkspaceJobsConfigCoverageGapTest {
    @Test
    void cleanupCtorGuards() {
        dev.jahaziel.workspace.api.WorkspaceService workspaces =
                mock(dev.jahaziel.workspace.api.WorkspaceService.class);
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        new CleanupJob(null, "/r");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        new CleanupJob(workspaces, null);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        new CleanupJob(workspaces, "  ");
                    }
                });
    }

    @Test
    void cleanupNoJailReturns(@TempDir Path root) {
        dev.jahaziel.workspace.api.WorkspaceService workspaces =
                mock(dev.jahaziel.workspace.api.WorkspaceService.class);
        when(workspaces.describeAll()).thenReturn(List.of());
        new CleanupJob(workspaces, root.toString()).run(JobContext.now());
    }

    @Test
    void cleanupKeepsKnownAndDeletesOrphan(@TempDir Path root) throws Exception {
        UUID knownId = UUID.randomUUID();
        UUID orphanId = UUID.randomUUID();
        Path jail = root.resolve("jail");
        Files.createDirectories(jail.resolve(knownId.toString()));
        Files.createDirectories(jail.resolve(orphanId.toString()));
        Files.writeString(jail.resolve(orphanId.toString()).resolve("x.txt"), "x");
        Files.writeString(jail.resolve("not-a-uuid"), "x");
        dev.jahaziel.workspace.api.WorkspaceService workspaces =
                mock(dev.jahaziel.workspace.api.WorkspaceService.class);
        Workspace workspace = workspace(knownId);
        WorkspaceView view = mock(WorkspaceView.class);
        when(view.workspace()).thenReturn(workspace);
        when(workspaces.describeAll()).thenReturn(List.of(view));
        new CleanupJob(workspaces, root.toString()).run(JobContext.now());
        assertTrue(Files.isDirectory(jail.resolve(knownId.toString())));
        assertTrue(Files.notExists(jail.resolve(orphanId.toString())));
        assertTrue(Files.exists(jail.resolve("not-a-uuid")));
        CleanupJob job = new CleanupJob(workspaces, root.toString());
        assertEquals(Duration.ofHours(24), job.period());
        assertEquals(JobScope.NODE, job.scope());
    }

    @Test
    void cleanupListFailureThrows(@TempDir Path root) throws Exception {
        Path jail = root.resolve("jail");
        Files.createDirectories(jail);
        dev.jahaziel.workspace.api.WorkspaceService workspaces =
                mock(dev.jahaziel.workspace.api.WorkspaceService.class);
        when(workspaces.describeAll()).thenReturn(List.of());
        try (org.mockito.MockedStatic<java.nio.file.Files> files =
                org.mockito.Mockito.mockStatic(
                        Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            files.when(
                            new org.mockito.MockedStatic.Verification() {
                                @Override
                                public void apply() throws Throwable {
                                    Files.list(any(Path.class));
                                }
                            })
                    .thenThrow(new java.io.IOException("boom"));
            assertThrows(
                    java.io.UncheckedIOException.class,
                    new org.junit.jupiter.api.function.Executable() {
                        @Override
                        public void execute() throws Throwable {
                            new CleanupJob(workspaces, root.toString()).run(JobContext.now());
                        }
                    });
        }
    }

    @Test
    void backendConfigSelectsOnlyVerifiedBackend() {
        WorkspaceBackendConfig config = new WorkspaceBackendConfig();
        dev.jahaziel.spi.WorkspaceStorage storage = mock(dev.jahaziel.spi.WorkspaceStorage.class);
        NodeBackend local = config.nodeBackend("local", storage);
        assertInstanceOf(LocalProcessBackend.class, local);
        assertInstanceOf(LocalProcessBackend.class, config.nodeBackend("  LOCAL  ", storage));
        IllegalArgumentException unsupported =
                assertThrows(
                        IllegalArgumentException.class,
                        new org.junit.jupiter.api.function.Executable() {
                            @Override
                            public void execute() throws Throwable {
                                config.nodeBackend("firecracker", storage);
                            }
                        });
        assertTrue(unsupported.getMessage().contains("WORKSPACE_BACKEND_UNSUPPORTED"));
        assertTrue(unsupported.getMessage().matches(".*correlationId=[0-9a-f-]{36}:.*"));
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        config.nodeBackend("  FIRECRACKER  ", storage);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        config.nodeBackend("other", storage);
                    }
                });
    }

    @Test
    void backendConfigReadsDocumentedEnvironmentProperty() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "test", Map.of("JAHAZIEL_BACKEND", "firecracker")));
            context.registerBean(
                    dev.jahaziel.spi.WorkspaceStorage.class,
                    new java.util.function.Supplier<dev.jahaziel.spi.WorkspaceStorage>() {
                        @Override
                        public dev.jahaziel.spi.WorkspaceStorage get() {
                            return mock(dev.jahaziel.spi.WorkspaceStorage.class);
                        }
                    });
            context.register(WorkspaceBackendConfig.class);

            assertThrows(
                    BeanCreationException.class,
                    new org.junit.jupiter.api.function.Executable() {
                        @Override
                        public void execute() throws Throwable {
                            context.refresh();
                        }
                    });
        }
    }

    @Test
    void pathsGuards() {
        WorkspaceId id = new WorkspaceId(UUID.randomUUID());
        assertTrue(WorkspacePaths.jailPath("/r", id).equals("/r/jail/" + id.value()));
        assertTrue(
                WorkspacePaths.homePath("/r", id).equals("/r/workspaces/" + id.value() + "/home"));
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        WorkspacePaths.jailPath(null, id);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        WorkspacePaths.jailPath("  ", id);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        WorkspacePaths.homePath("", id);
                    }
                });
    }

    @Test
    void serviceCtorGuards() {
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        WorkspaceAclRepository acl = mock(WorkspaceAclRepository.class);
        VirtualMachineRepository machines = mock(VirtualMachineRepository.class);
        dev.jahaziel.identity.api.UserService users =
                mock(dev.jahaziel.identity.api.UserService.class);
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        new WorkspaceServiceImpl(workspaces, acl, machines, users, null);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        new WorkspaceServiceImpl(workspaces, acl, machines, users, "  ");
                    }
                });
    }

    @Test
    void createRollsBackWhenMachineFails() {
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        WorkspaceAclRepository acl = mock(WorkspaceAclRepository.class);
        VirtualMachineRepository machines = mock(VirtualMachineRepository.class);
        dev.jahaziel.identity.api.UserService users =
                mock(dev.jahaziel.identity.api.UserService.class);
        WorkspaceServiceImpl service =
                new WorkspaceServiceImpl(workspaces, acl, machines, users, "/r");
        UserId owner = new UserId(UUID.randomUUID());
        when(users.get(owner))
                .thenReturn(new UserIdentity("s", "a@b.c", "A", List.of(), List.of()));
        when(workspaces.findByOwnerUserId(owner.value())).thenReturn(List.of());
        when(machines.save(any())).thenThrow(new RuntimeException("boom"));
        assertThrows(
                RuntimeException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        service.create(owner, "site", 1, 512, new NodeId("n"));
                    }
                });
        verify(workspaces).deleteById(any(UUID.class));
    }

    @Test
    void getWithoutMachineRowThrows() {
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        WorkspaceAclRepository acl = mock(WorkspaceAclRepository.class);
        VirtualMachineRepository machines = mock(VirtualMachineRepository.class);
        dev.jahaziel.identity.api.UserService users =
                mock(dev.jahaziel.identity.api.UserService.class);
        WorkspaceServiceImpl service =
                new WorkspaceServiceImpl(workspaces, acl, machines, users, "/r");
        WorkspaceId id = new WorkspaceId(UUID.randomUUID());
        WorkspaceEntity row = new WorkspaceEntity();
        row.setWorkspaceId(id.value());
        row.setOwnerUserId(UUID.randomUUID());
        row.setName("site");
        row.setState("requested");
        row.setStateChangedAt(Instant.now());
        row.setLogicalQuotaBytes(1L);
        row.setInodeQuota(1L);
        row.setCreatedAt(Instant.now());
        when(workspaces.findById(id.value())).thenReturn(Optional.of(row));
        when(acl.findByIdWorkspaceId(id.value())).thenReturn(List.of());
        when(machines.findByWorkspaceId(id.value())).thenReturn(Optional.empty());
        assertThrows(
                IllegalStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        service.get(id);
                    }
                });
    }

    @Test
    void createRejectsNullAndLongNames() {
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        WorkspaceAclRepository acl = mock(WorkspaceAclRepository.class);
        VirtualMachineRepository machines = mock(VirtualMachineRepository.class);
        dev.jahaziel.identity.api.UserService users =
                mock(dev.jahaziel.identity.api.UserService.class);
        WorkspaceServiceImpl service =
                new WorkspaceServiceImpl(workspaces, acl, machines, users, "/r");
        UserId owner = new UserId(UUID.randomUUID());
        when(users.get(owner))
                .thenReturn(new UserIdentity("s", "a@b.c", "A", List.of(), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        service.create(owner, null, 1, 512, new NodeId("n"));
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        service.create(owner, "x".repeat(65), 1, 512, new NodeId("n"));
                    }
                });
    }

    @Test
    void mapperIdleAndUnknown() {
        WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
        UserId owner = new UserId(UUID.randomUUID());
        Instant at = Instant.now();
        WorkspaceEntity row = mock(WorkspaceEntity.class);
        when(row.getWorkspaceId()).thenReturn(workspaceId.value());
        when(row.getOwnerUserId()).thenReturn(owner.value());
        when(row.getName()).thenReturn("club");
        when(row.getState()).thenReturn("idle");
        when(row.getStateChangedAt()).thenReturn(at);
        when(row.getLogicalQuotaBytes()).thenReturn(1L);
        when(row.getInodeQuota()).thenReturn(1L);
        VirtualMachineEntity machine = mock(VirtualMachineEntity.class);
        when(machine.getVmId()).thenReturn(UUID.randomUUID());
        when(machine.getVcpuCount()).thenReturn(1);
        when(machine.getMemoryMib()).thenReturn(512L);
        Workspace workspace = WorkspaceMapper.toDomain(row, List.of(), machine, List.of());
        assertInstanceOf(WorkspaceState.Idle.class, workspace.state());
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        WorkspaceMapper.stateToString(null);
                    }
                });
    }

    private Workspace workspace(UUID id) {
        WorkspaceId workspaceId = new WorkspaceId(id);
        return new Workspace(
                workspaceId,
                new UserId(UUID.randomUUID()),
                "club",
                new WorkspaceQuota(1L, 1L),
                new VirtualMachine(
                        new VirtualMachineId(UUID.randomUUID()),
                        workspaceId,
                        new VirtualMachineConfiguration(1, 512)));
    }
}
