/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jahaziel.agent.chat.AgentChatService;
import dev.jahaziel.agent.diff.DiffReviewService;
import dev.jahaziel.agent.model.ModelCatalog;
import dev.jahaziel.agent.prompt.PromptLibrary;
import dev.jahaziel.agent.prompt.PromptTemplate;
import dev.jahaziel.agent.session.AgentSession;
import dev.jahaziel.agent.session.AgentSessionService;
import dev.jahaziel.agent.usage.UsageMeters;
import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.club.api.ClubService;
import dev.jahaziel.club.checkpoint.CheckpointService;
import dev.jahaziel.club.domain.ClubRoles;
import dev.jahaziel.club.moderation.ModerationService;
import dev.jahaziel.club.session.ClubSessionService;
import dev.jahaziel.datatools.CellOutput;
import dev.jahaziel.datatools.CsvReader;
import dev.jahaziel.datatools.CsvWriter;
import dev.jahaziel.datatools.FileHistoryService;
import dev.jahaziel.datatools.IndexHit;
import dev.jahaziel.datatools.NotebookExecution;
import dev.jahaziel.datatools.NotebookRunner;
import dev.jahaziel.datatools.SearchIndex;
import dev.jahaziel.datatools.SqliteBrowser;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.ops.ApprovalQueue;
import dev.jahaziel.ops.AuditQueryService;
import dev.jahaziel.ops.GdprService;
import dev.jahaziel.ops.ScheduledBackupService;
import dev.jahaziel.ops.SupportBundleService;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Focused checks for the new API endpoints: auth, workspace scoping, path safety and delegation.
 */
final class ApiEndpointsTest {
    @TempDir Path tempDir;

    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();
    private final UserId user = new UserId(userId);
    private final WorkspaceId workspace = new WorkspaceId(workspaceId);

    private static Principal principal(UUID value) {
        return new Principal() {
            @Override
            public String getName() {
                return value.toString();
            }
        };
    }

    private static void expectStatus(ApiFailureException failure, int status) {
        assertEquals(status, failure.status());
    }

    private AuthorizationService allowedAuth() {
        AuthorizationService auth = mock(AuthorizationService.class);
        when(auth.isAllowed(eq(user), any(String.class), eq(workspace))).thenReturn(true);
        return auth;
    }

    @Test
    void agentSessionsRejectUnauthenticatedCallers() {
        final AgentSessionService sessions = mock(AgentSessionService.class);
        AgentSessionController controller = new AgentSessionController(sessions, allowedAuth());

        ApiFailureException failure =
                assertThrows(
                        ApiFailureException.class,
                        new Executable() {
                            @Override
                            public void execute() {
                                controller.list(workspaceId, null);
                            }
                        });
        expectStatus(failure, 401);
        verifyNoInteractions(sessions);
    }

    @Test
    void agentSessionsRejectCallersWithoutWorkspaceAccess() {
        final AgentSessionService sessions = mock(AgentSessionService.class);
        AuthorizationService auth = mock(AuthorizationService.class);
        when(auth.isAllowed(user, "read", workspace)).thenReturn(false);
        AgentSessionController controller = new AgentSessionController(sessions, auth);

        ApiFailureException failure =
                assertThrows(
                        ApiFailureException.class,
                        new Executable() {
                            @Override
                            public void execute() {
                                controller.list(workspaceId, principal(userId));
                            }
                        });
        expectStatus(failure, 403);
        verifyNoInteractions(sessions);
    }

    @Test
    void agentSessionCreateDelegatesToService() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        AgentSession created = new AgentSession();
        created.setId("session-1");
        created.setName("work");
        created.setStatus("CREATED");
        created.setWorkspaceId(workspaceId.toString());
        when(sessions.create(eq(user), eq(workspace), eq("work"))).thenReturn(created);
        AgentSessionController controller = new AgentSessionController(sessions, allowedAuth());

        AgentDtos.AgentRequest request = new AgentDtos.AgentRequest();
        request.setName("work");
        AgentDtos.SessionDto result = controller.create(workspaceId, request, principal(userId));
        assertEquals("session-1", result.getId());
        assertEquals("CREATED", result.getStatus());
        verify(sessions).create(user, workspace, "work");
    }

    @Test
    void promptEndpointsRequireAuthenticationAndDelegate() {
        PromptLibrary prompts = mock(PromptLibrary.class);
        final AgentPromptController controller = new AgentPromptController(prompts, allowedAuth());

        assertThrows(
                ApiFailureException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.list(null);
                    }
                });

        PromptTemplate template = new PromptTemplate();
        template.setId("prompt-1");
        template.setName("review");
        template.setBody("${file}");
        when(prompts.createUser(eq(user), eq("review"), eq("${file}"))).thenReturn(template);
        AgentDtos.AgentRequest request = new AgentDtos.AgentRequest();
        request.setName("review");
        request.setText("${file}");
        assertEquals("prompt-1", controller.create(request, principal(userId)).getId());
        verify(prompts).createUser(user, "review", "${file}");
    }

    @Test
    void diffAndChatEndpointsEnforceWorkspaceScoping() {
        final DiffReviewService diffs = mock(DiffReviewService.class);
        final AgentChatService chat = mock(AgentChatService.class);
        AuthorizationService denied = mock(AuthorizationService.class);
        when(denied.isAllowed(user, "write", workspace)).thenReturn(false);

        AgentDiffController diffController = new AgentDiffController(diffs, denied);
        AgentChatController chatController = new AgentChatController(chat, denied);
        final AgentDtos.AgentRequest request = new AgentDtos.AgentRequest();
        request.setText("hello");

        expectStatus(
                assertThrows(
                        ApiFailureException.class,
                        new Executable() {
                            @Override
                            public void execute() {
                                diffController.stage(workspaceId, request, principal(userId));
                            }
                        }),
                403);
        expectStatus(
                assertThrows(
                        ApiFailureException.class,
                        new Executable() {
                            @Override
                            public void execute() {
                                chatController.send(workspaceId, request, principal(userId));
                            }
                        }),
                403);
        verifyNoInteractions(diffs, chat);
    }

    @Test
    void modelCatalogRequiresAuthenticationAndListsAllowlistedModels() {
        ModelCatalog catalog = new ModelCatalog(Arrays.asList("local"), null);
        final AgentMetaController controller =
                new AgentMetaController(catalog, mock(UsageMeters.class), allowedAuth());

        assertThrows(
                ApiFailureException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.catalog(null);
                    }
                });
        List<AgentDtos.ModelDto> found = controller.catalog(principal(userId));
        assertEquals(1, found.size());
        assertEquals("local", found.get(0).getId());
        assertEquals(ModelCatalog.UNAVAILABLE, found.get(0).getCapability());
    }

    @Test
    void sqliteBrowserRejectsTraversalAndDelegatesSafePaths() throws Exception {
        Path root =
                Files.createDirectories(
                        tempDir.resolve("workspaces")
                                .resolve(workspaceId.toString())
                                .resolve("home"));
        final SqliteBrowser browser = mock(SqliteBrowser.class);
        when(browser.tables(eq(workspace), eq("data.db"))).thenReturn(Arrays.asList("users"));
        SqliteController controller =
                new SqliteController(browser, allowedAuth(), tempDir.toString());

        expectStatus(
                assertThrows(
                        ApiFailureException.class,
                        new Executable() {
                            @Override
                            public void execute() {
                                controller.tables(workspaceId, "../secret.db", principal(userId));
                            }
                        }),
                400);
        verifyNoInteractions(browser);

        assertEquals(1, controller.tables(workspaceId, "data.db", principal(userId)).size());
        verify(browser).tables(workspace, "data.db");
        assertTrue(Files.isDirectory(root));
    }

    @Test
    void dataToolsRejectTraversalOnSaveAndRestore() throws Exception {
        CsvReader reader = new CsvReader();
        final CsvWriter writer = mock(CsvWriter.class);
        NotebookRunner notebooks = mock(NotebookRunner.class);
        SearchIndex search = mock(SearchIndex.class);
        final FileHistoryService history = mock(FileHistoryService.class);
        WorkspaceDataController controller =
                new WorkspaceDataController(
                        reader,
                        writer,
                        notebooks,
                        search,
                        history,
                        allowedAuth(),
                        tempDir.toString());
        Files.createDirectories(
                tempDir.resolve("workspaces").resolve(workspaceId.toString()).resolve("home"));

        final DataDtos.CsvRequest csv = new DataDtos.CsvRequest();
        csv.setPath("../bad.csv");
        csv.setContent("a,b\n1,2");
        expectStatus(
                assertThrows(
                        ApiFailureException.class,
                        new Executable() {
                            @Override
                            public void execute() {
                                controller.saveCsv(workspaceId, csv, principal(userId));
                            }
                        }),
                400);
        verifyNoInteractions(writer);

        final DataDtos.RestoreRequest restore = new DataDtos.RestoreRequest();
        restore.setPath("..\\bad.txt");
        restore.setSnapshotId(UUID.randomUUID().toString());
        expectStatus(
                assertThrows(
                        ApiFailureException.class,
                        new Executable() {
                            @Override
                            public void execute() {
                                controller.restore(workspaceId, restore, principal(userId));
                            }
                        }),
                400);
        verifyNoInteractions(history);
    }

    @Test
    void dataToolsDelegateOnSuccessPaths() throws Exception {
        Files.createDirectories(
                tempDir.resolve("workspaces").resolve(workspaceId.toString()).resolve("home"));
        CsvReader reader = new CsvReader();
        CsvWriter writer = mock(CsvWriter.class);
        NotebookRunner notebooks = mock(NotebookRunner.class);
        SearchIndex search = mock(SearchIndex.class);
        FileHistoryService history = mock(FileHistoryService.class);
        WorkspaceDataController controller =
                new WorkspaceDataController(
                        reader,
                        writer,
                        notebooks,
                        search,
                        history,
                        allowedAuth(),
                        tempDir.toString());

        DataDtos.CsvRequest csv = new DataDtos.CsvRequest();
        csv.setContent("name,age\nAda,36");
        DataDtos.TableDto parsed = controller.parseCsv(workspaceId, csv, principal(userId));
        assertEquals(2, parsed.getColumns().size());
        assertEquals(1, parsed.getRows().size());

        DataDtos.SearchRequest query = new DataDtos.SearchRequest();
        query.setQuery("Ada");
        when(search.search(eq(workspace), eq("Ada"), eq(SearchIndex.MAX_RESULTS)))
                .thenReturn(Arrays.asList(new IndexHit("notes.txt", "Ada")));
        List<DataDtos.HitDto> hits = controller.query(workspaceId, query, principal(userId));
        assertEquals("notes.txt", hits.get(0).getPath());
        verify(search).search(workspace, "Ada", SearchIndex.MAX_RESULTS);

        when(notebooks.run(eq(workspace), eq("nb1"), eq("task-1")))
                .thenReturn(
                        new NotebookExecution(
                                "nb1", "run-1", Arrays.asList(CellOutput.text("ok"))));
        DataDtos.NotebookRunRequest run = new DataDtos.NotebookRunRequest();
        run.setTaskId("task-1");
        assertEquals(
                "run-1",
                controller.runNotebook(workspaceId, "nb1", run, principal(userId)).getRunId());
        verify(notebooks).run(workspace, "nb1", "task-1");
    }

    @Test
    void clubSessionRequiresAuthenticationAndValidatesJoinCode() {
        ClubSessionService sessions = mock(ClubSessionService.class);
        ClubRegistry registry = new ClubRegistry();
        final ClubSessionController controller =
                new ClubSessionController(sessions, registry, allowedAuth());

        assertThrows(
                ApiFailureException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.create(new ClubDtos.CreateRequest(), null);
                    }
                });

        ClubDtos.CreateRequest request = new ClubDtos.CreateRequest();
        request.setTemplate("java");
        request.setStartsAt("2026-11-19T10:00:00Z");
        request.setEndsAt("2026-11-19T12:00:00Z");
        request.setCapacity(4);
        ClubDtos.SessionDto session = controller.create(request, principal(userId));
        assertNotNull(session.getId());
        assertEquals(1, session.getParticipants());

        final ClubDtos.JoinRequest bad = new ClubDtos.JoinRequest();
        bad.setCode("wrong");
        bad.setRole(ClubRoles.ATTENDEE);
        final String sessionId = session.getId();
        expectStatus(
                assertThrows(
                        ApiFailureException.class,
                        new Executable() {
                            @Override
                            public void execute() {
                                controller.join(sessionId, bad, principal(UUID.randomUUID()));
                            }
                        }),
                403);

        ClubDtos.JoinRequest good = new ClubDtos.JoinRequest();
        good.setCode(session.getJoinCode());
        good.setRole(ClubRoles.ATTENDEE);
        assertEquals(
                2,
                controller
                        .join(session.getId(), good, principal(UUID.randomUUID()))
                        .getParticipants());
    }

    @Test
    void clubShowcaseAndLessonEndpointsRequireAuthentication() {
        ClubService club = mock(ClubService.class);
        CheckpointService checkpoints = mock(CheckpointService.class);
        final ClubLearningController controller =
                new ClubLearningController(club, checkpoints, new ClubRegistry(), allowedAuth());

        assertThrows(
                ApiFailureException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.showcase(null);
                    }
                });
        assertThrows(
                ApiFailureException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.lesson("missing", principal(userId));
                    }
                });
    }

    @Test
    void clubModerationRequiresAuthentication() {
        final ClubModerationController controller =
                new ClubModerationController(
                        mock(ModerationService.class), new ClubRegistry(), allowedAuth());
        assertThrows(
                ApiFailureException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.history("missing", null);
                    }
                });
    }

    @Test
    void opsEndpointsRejectNonAdmins() {
        AuthorizationService auth = mock(AuthorizationService.class);
        when(auth.isAdmin(user)).thenReturn(false);
        final OpsAdminController controller =
                new OpsAdminController(
                        mock(SupportBundleService.class),
                        mock(ScheduledBackupService.class),
                        mock(AuditQueryService.class),
                        mock(AuditService.class),
                        mock(GdprService.class),
                        mock(ApprovalQueue.class),
                        auth);

        expectStatus(
                assertThrows(
                        ApiFailureException.class,
                        new Executable() {
                            @Override
                            public void execute() {
                                controller.backupStatus(principal(userId));
                            }
                        }),
                403);
        expectStatus(
                assertThrows(
                        ApiFailureException.class,
                        new Executable() {
                            @Override
                            public void execute() {
                                controller.queryAudit(
                                        new OpsDtos.AuditQueryRequest(), principal(userId));
                            }
                        }),
                403);
    }

    @Test
    void opsBackupStatusDelegatesToService() {
        ScheduledBackupService backups = mock(ScheduledBackupService.class);
        Path archive = tempDir.resolve("backup-1.zip");
        when(backups.latest()).thenReturn(archive);
        when(backups.listBackups()).thenReturn(Arrays.asList(archive));
        AuthorizationService auth = mock(AuthorizationService.class);
        when(auth.isAdmin(user)).thenReturn(true);
        OpsAdminController controller =
                new OpsAdminController(
                        mock(SupportBundleService.class),
                        backups,
                        mock(AuditQueryService.class),
                        mock(AuditService.class),
                        mock(GdprService.class),
                        mock(ApprovalQueue.class),
                        auth);

        OpsDtos.BackupStatusDto status = controller.backupStatus(principal(userId));
        assertEquals("backup-1.zip", status.getLatest());
        assertEquals(1, status.getCount());
        assertEquals(ScheduledBackupService.RPO_MINUTES, status.getRpoMinutes());
        verify(backups).latest();
        verify(backups).listBackups();
    }

    @Test
    void standaloneMvcReturnsRfc9457ProblemForUnauthenticatedRequest() throws Exception {
        AgentSessionService sessions = mock(AgentSessionService.class);
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(new AgentSessionController(sessions, allowedAuth()))
                        .setControllerAdvice(new ApiProblemHandler())
                        .build();

        mvc.perform(get("/api/v1/agent/workspaces/" + workspaceId + "/sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Authentication is required"));
        verifyNoInteractions(sessions);
    }

    @Test
    void standaloneMvcReturnsProblemForWorkspaceDenial() throws Exception {
        AgentSessionService sessions = mock(AgentSessionService.class);
        AuthorizationService auth = mock(AuthorizationService.class);
        when(auth.isAllowed(user, "read", workspace)).thenReturn(false);
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(new AgentSessionController(sessions, auth))
                        .setControllerAdvice(new ApiProblemHandler())
                        .build();

        mvc.perform(
                        get("/api/v1/agent/workspaces/" + workspaceId + "/sessions")
                                .principal(principal(userId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("You may not perform this action"));
        verifyNoInteractions(sessions);
    }

    @Test
    void standaloneMvcReturnsSuccessBodyWithPrincipal() throws Exception {
        AgentSessionService sessions = mock(AgentSessionService.class);
        when(sessions.list(eq(user), eq(workspace))).thenReturn(new ArrayList<AgentSession>());
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(new AgentSessionController(sessions, allowedAuth()))
                        .setControllerAdvice(new ApiProblemHandler())
                        .build();

        mvc.perform(
                        get("/api/v1/agent/workspaces/" + workspaceId + "/sessions")
                                .principal(principal(userId)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$").isArray());
        verify(sessions).list(user, workspace);
    }

    @Test
    void problemBodyNeverContainsRawExceptionMessage() {
        ApiProblemHandler handler = new ApiProblemHandler();
        IllegalArgumentException raw =
                new IllegalArgumentException("parent traversal is rejected: ../../secret");
        org.springframework.http.ResponseEntity<ApiProblem> response =
                handler.invalidInput(raw, null);
        assertEquals(400, response.getBody().getStatus());
        assertEquals("The request contains an invalid value", response.getBody().getDetail());
        assertTrue(response.getBody().getDetail().indexOf("secret") < 0);
    }
}
