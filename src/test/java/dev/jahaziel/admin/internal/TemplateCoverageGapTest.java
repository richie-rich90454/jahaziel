/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.api.WorkspaceDtoFactory;
import dev.jahaziel.api.dto.WorkspaceDto;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.spi.StorageUsage;
import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class TemplateCoverageGapTest {
    @TempDir private Path tmp;

    private Principal principal(final UUID id) {
        return new Principal() {
            @Override
            public String getName() {
                return id.toString();
            }
        };
    }

    private Principal badPrincipal() {
        return new Principal() {
            @Override
            public String getName() {
                return "nope";
            }
        };
    }

    private TemplateController controller(Path dir) {
        return controller(mock(WorkspaceService.class), mock(WorkspaceDtoFactory.class), dir);
    }

    private TemplateController controller(
            WorkspaceService workspaces, WorkspaceDtoFactory dtos, Path dir) {
        return controller(workspaces, dtos, mock(PortService.class), dir);
    }

    private TemplateController controller(
            WorkspaceService workspaces, WorkspaceDtoFactory dtos, PortService ports, Path dir) {
        WorkspaceFiles files = mock(WorkspaceFiles.class);
        when(files.forWorkspace(any())).thenReturn(mock(WorkspaceFiles.Operations.class));
        return new TemplateController(
                workspaces,
                files,
                mock(WorkspaceTaskService.class),
                ports,
                dtos,
                new ObjectMapper(),
                dir.toString(),
                "node-a");
    }

    private static Workspace newWorkspace(UserId owner, WorkspaceId id) {
        return new Workspace(
                id,
                owner,
                "ws",
                new WorkspaceQuota(21474836480L, 1000000L),
                new VirtualMachine(
                        new VirtualMachineId(UUID.randomUUID()),
                        id,
                        new VirtualMachineConfiguration(2, 2048)));
    }

    private static WorkspaceView viewOf(Workspace workspace) {
        return new WorkspaceView(
                workspace, Instant.now(), Instant.now(), new StorageUsage(0, 0, 0, 0));
    }

    private static MockedStatic.Verification isDirectory() {
        return new MockedStatic.Verification() {
            @Override
            public void apply() {
                Files.isDirectory(any(Path.class));
            }
        };
    }

    private static MockedStatic.Verification listFiles() {
        return new MockedStatic.Verification() {
            @Override
            public void apply() throws IOException {
                Files.list(any(Path.class));
            }
        };
    }

    private static MockedStatic.Verification isRegularFile() {
        return new MockedStatic.Verification() {
            @Override
            public void apply() {
                Files.isRegularFile(any(Path.class));
            }
        };
    }

    private static MockedStatic.Verification readAllBytes() {
        return new MockedStatic.Verification() {
            @Override
            public void apply() throws IOException {
                Files.readAllBytes(any(Path.class));
            }
        };
    }

    @Test
    void listEmptyWhenMissing() {
        assertTrue(controller(tmp.resolve("missing")).list().isEmpty());
    }

    @Test
    void listSortedSkipsNonTemplates() throws Exception {
        Path dir = tmp.resolve("templates");
        Files.createDirectories(dir.resolve("b"));
        Files.createDirectories(dir.resolve("a"));
        Files.createDirectories(dir.resolve("empty"));
        Files.writeString(dir.resolve("a").resolve("template.json"), "{}");
        Files.writeString(dir.resolve("b").resolve("template.json"), "{}");
        Files.writeString(dir.resolve("f.txt"), "hi");
        assertEquals(java.util.List.of("a", "b"), controller(dir).list());
    }

    @Test
    void listFailureWraps() {
        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
            files.when(isDirectory()).thenReturn(true);
            files.when(listFiles()).thenThrow(new IOException("boom"));
            assertThrows(
                    UncheckedIOException.class,
                    new Executable() {
                        @Override
                        public void execute() {
                            controller(tmp).list();
                        }
                    });
        }
    }

    @Test
    void getServesBytes() throws Exception {
        Path dir = tmp.resolve("t");
        Files.createDirectories(dir.resolve("foo"));
        Files.writeString(dir.resolve("foo").resolve("template.json"), "{\"v\":1}");
        ResponseEntity<byte[]> response = controller(dir).get("foo");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertEquals("{\"v\":1}", new String(response.getBody()));
    }

    @Test
    void getMissingIs404() throws Exception {
        Path dir = tmp.resolve("t404");
        Files.createDirectories(dir);
        assertEquals(HttpStatus.NOT_FOUND, controller(dir).get("nope").getStatusCode());
    }

    @Test
    void getRejectsTraversal() throws Exception {
        final Path dir = tmp.resolve("trav");
        Files.createDirectories(dir);
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller(dir).get("..");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller(dir).get("../x");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller(dir).get("/abs");
                    }
                });
    }

    @Test
    void getReadFailureWraps() {
        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {
            files.when(isRegularFile()).thenReturn(true);
            files.when(readAllBytes()).thenThrow(new IOException("boom"));
            assertThrows(
                    UncheckedIOException.class,
                    new Executable() {
                        @Override
                        public void execute() {
                            controller(tmp).get("foo");
                        }
                    });
        }
    }

    @Test
    void applyValidatesRequest() throws Exception {
        Path dir = tmp.resolve("apply-bad");
        Files.createDirectories(dir);
        final TemplateController controller = controller(dir);
        UUID user = UUID.randomUUID();
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.apply("t", null, principal(user));
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.apply(
                                "t", new TemplateController.ApplyRequest("  "), principal(user));
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.apply(
                                "t", new TemplateController.ApplyRequest(null), principal(user));
                    }
                });
    }

    @Test
    void applyRejectsStarterFilesAboveTheTotalByteLimitBeforeWorkspaceCreation() throws Exception {
        Path dir = tmp.resolve("apply-large-template");
        Files.createDirectories(dir.resolve("large"));
        java.util.Map<String, String> starterFiles = new java.util.LinkedHashMap<>();
        for (int index = 0; index < 8; index++) {
            starterFiles.put("file-" + index + ".txt", "x".repeat(256 * 1024));
        }
        starterFiles.put("overflow.txt", "x");
        Files.writeString(
                dir.resolve("large").resolve("template.json"),
                new ObjectMapper()
                        .writeValueAsString(java.util.Map.of("starterFiles", starterFiles)));

        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller(dir)
                                .apply(
                                        "large",
                                        new TemplateController.ApplyRequest("Large workspace"),
                                        principal(UUID.randomUUID()));
                    }
                });
    }

    @Test
    void applyRejectsMoreThanTheWorkspacePortLimitBeforeWorkspaceCreation() throws Exception {
        Path dir = tmp.resolve("apply-too-many-ports");
        Files.createDirectories(dir.resolve("many"));
        List<Integer> tooManyPorts = new ArrayList<Integer>();
        for (int port = 1; port <= PortService.MAX_PORTS_PER_WORKSPACE + 1; port++) {
            tooManyPorts.add(port);
        }
        Files.writeString(
                dir.resolve("many").resolve("template.json"),
                new ObjectMapper().writeValueAsString(java.util.Map.of("ports", tooManyPorts)));
        WorkspaceService workspaces = mock(WorkspaceService.class);
        final TemplateController controller =
                controller(workspaces, mock(WorkspaceDtoFactory.class), dir);

        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.apply(
                                "many",
                                new TemplateController.ApplyRequest("Too many ports"),
                                principal(UUID.randomUUID()));
                    }
                });
        Mockito.verify(workspaces, Mockito.never())
                .create(any(UserId.class), anyString(), anyInt(), anyLong(), any(NodeId.class));
    }

    @Test
    void applyCreatesWorkspace() throws Exception {
        Path dir = tmp.resolve("apply-ok");
        Files.createDirectories(dir.resolve("full"));
        Files.writeString(
                dir.resolve("full").resolve("template.json"),
                "{\"vcpuCount\":4,\"memoryMib\":1024}");
        WorkspaceService workspaces = mock(WorkspaceService.class);
        WorkspaceDtoFactory dtos = mock(WorkspaceDtoFactory.class);
        TemplateController controller = controller(workspaces, dtos, dir);
        UserId owner = new UserId(UUID.randomUUID());
        WorkspaceId id = new WorkspaceId(UUID.randomUUID());
        Workspace workspace =
                new Workspace(
                        id,
                        owner,
                        "ws",
                        new WorkspaceQuota(21474836480L, 1000000L),
                        new VirtualMachine(
                                new VirtualMachineId(UUID.randomUUID()),
                                id,
                                new VirtualMachineConfiguration(2, 2048)));
        WorkspaceView view =
                new WorkspaceView(
                        workspace, Instant.now(), Instant.now(), new StorageUsage(0, 0, 0, 0));
        WorkspaceDto dto = mock(WorkspaceDto.class);
        when(workspaces.create(any(), any(), any(int.class), any(long.class), any()))
                .thenReturn(workspace);
        when(workspaces.describe(id)).thenReturn(view);
        when(dtos.forUser(owner, view)).thenReturn(dto);
        assertSame(
                dto,
                controller.apply(
                        "full",
                        new TemplateController.ApplyRequest("ws"),
                        principal(owner.value())));
        verify(workspaces).create(owner, "ws", 4, 1024L, new NodeId("node-a"));
    }

    @Test
    void applyDefaultsAndRejectsBadPrincipal() throws Exception {
        Path dir = tmp.resolve("apply-defaults");
        Files.createDirectories(dir.resolve("mini"));
        Files.writeString(dir.resolve("mini").resolve("template.json"), "{}");
        WorkspaceService workspaces = mock(WorkspaceService.class);
        WorkspaceDtoFactory dtos = mock(WorkspaceDtoFactory.class);
        final TemplateController controller = controller(workspaces, dtos, dir);
        UserId owner = new UserId(UUID.randomUUID());
        WorkspaceId id = new WorkspaceId(UUID.randomUUID());
        Workspace workspace =
                new Workspace(
                        id,
                        owner,
                        "ws",
                        new WorkspaceQuota(21474836480L, 1000000L),
                        new VirtualMachine(
                                new VirtualMachineId(UUID.randomUUID()),
                                id,
                                new VirtualMachineConfiguration(2, 2048)));
        WorkspaceView view =
                new WorkspaceView(
                        workspace, Instant.now(), Instant.now(), new StorageUsage(0, 0, 0, 0));
        when(workspaces.create(any(), any(), any(int.class), any(long.class), any()))
                .thenReturn(workspace);
        when(workspaces.describe(id)).thenReturn(view);
        when(dtos.forUser(any(), any())).thenReturn(mock(WorkspaceDto.class));
        controller.apply(
                "mini", new TemplateController.ApplyRequest("ws"), principal(owner.value()));
        verify(workspaces).create(owner, "ws", 2, 2048L, new NodeId("node-a"));
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.apply(
                                "mini", new TemplateController.ApplyRequest("ws"), badPrincipal());
                    }
                });
    }

    @Test
    void applyMissingTemplateIsNotFound() throws Exception {
        Path dir = tmp.resolve("apply-missing");
        Files.createDirectories(dir);
        final TemplateController controller = controller(dir);
        org.springframework.web.server.ResponseStatusException missing =
                assertThrows(
                        org.springframework.web.server.ResponseStatusException.class,
                        new Executable() {
                            @Override
                            public void execute() {
                                controller.apply(
                                        "nope",
                                        new TemplateController.ApplyRequest("ws"),
                                        principal(UUID.randomUUID()));
                            }
                        });
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
    }

    @Test
    void applyAllowsSamePortAcrossWorkspacesOfSameUser() throws Exception {
        Path dir = tmp.resolve("apply-shared-port");
        Files.createDirectories(dir.resolve("web"));
        Files.writeString(dir.resolve("web").resolve("template.json"), "{\"ports\":[8080]}");
        WorkspaceService workspaces = mock(WorkspaceService.class);
        WorkspaceDtoFactory dtos = mock(WorkspaceDtoFactory.class);
        PortService ports = mock(PortService.class);
        TemplateController controller = controller(workspaces, dtos, ports, dir);

        UserId owner = new UserId(UUID.randomUUID());
        WorkspaceId firstId = new WorkspaceId(UUID.randomUUID());
        WorkspaceId secondId = new WorkspaceId(UUID.randomUUID());
        Workspace first = newWorkspace(owner, firstId);
        Workspace second = newWorkspace(owner, secondId);
        when(workspaces.create(any(), any(), any(int.class), any(long.class), any()))
                .thenReturn(first, second);
        when(workspaces.describe(firstId)).thenReturn(viewOf(first));
        when(workspaces.describe(secondId)).thenReturn(viewOf(second));
        when(dtos.forUser(any(), any())).thenReturn(mock(WorkspaceDto.class));
        when(ports.lookup(owner.value(), 8080))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(firstId));

        controller.apply(
                "web", new TemplateController.ApplyRequest("first"), principal(owner.value()));
        controller.apply(
                "web", new TemplateController.ApplyRequest("second"), principal(owner.value()));

        verify(ports).registerPort(owner, 8080, firstId, "http");
        verify(ports).registerPort(owner, 8080, secondId, "http");
    }

    @Test
    void applyRejectsDuplicatePortsWithinTemplate() throws Exception {
        Path dir = tmp.resolve("apply-duplicate-port");
        Files.createDirectories(dir.resolve("dup"));
        Files.writeString(dir.resolve("dup").resolve("template.json"), "{\"ports\":[8080,8080]}");
        WorkspaceService workspaces = mock(WorkspaceService.class);
        final TemplateController controller =
                controller(workspaces, mock(WorkspaceDtoFactory.class), dir);

        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.apply(
                                "dup",
                                new TemplateController.ApplyRequest("ws"),
                                principal(UUID.randomUUID()));
                    }
                });
        Mockito.verify(workspaces, Mockito.never())
                .create(any(UserId.class), anyString(), anyInt(), anyLong(), any(NodeId.class));
    }

    @Test
    void applyRejectsReservedPorts() throws Exception {
        Path dir = tmp.resolve("apply-reserved-port");
        Files.createDirectories(dir.resolve("reserved"));
        WorkspaceService workspaces = mock(WorkspaceService.class);
        final TemplateController controller =
                controller(workspaces, mock(WorkspaceDtoFactory.class), dir);
        for (int reserved : List.of(22, 25)) {
            Files.writeString(
                    dir.resolve("reserved").resolve("template.json"),
                    "{\"ports\":[" + reserved + "]}");
            IllegalArgumentException failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            new Executable() {
                                @Override
                                public void execute() {
                                    controller.apply(
                                            "reserved",
                                            new TemplateController.ApplyRequest("ws"),
                                            principal(UUID.randomUUID()));
                                }
                            });
            assertTrue(failure.getMessage().contains("reserved"));
        }
        Mockito.verify(workspaces, Mockito.never())
                .create(any(UserId.class), anyString(), anyInt(), anyLong(), any(NodeId.class));
    }
}
