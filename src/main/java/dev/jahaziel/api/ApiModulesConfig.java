/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.agent.AgentAudit;
import dev.jahaziel.agent.AgentStore;
import dev.jahaziel.agent.chat.AgentChatService;
import dev.jahaziel.agent.diff.DiffReviewService;
import dev.jahaziel.agent.diff.StrictPatchParser;
import dev.jahaziel.agent.model.ModelCatalog;
import dev.jahaziel.agent.prompt.PromptLibrary;
import dev.jahaziel.agent.session.AgentSessionService;
import dev.jahaziel.agent.usage.UsageMeters;
import dev.jahaziel.agenttools.api.AgentToolService;
import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.club.api.ClubService;
import dev.jahaziel.club.checkpoint.CheckpointService;
import dev.jahaziel.club.export.SessionExporter;
import dev.jahaziel.club.moderation.ModerationService;
import dev.jahaziel.club.session.ClubSessionService;
import dev.jahaziel.club.showcase.ShowcaseGallery;
import dev.jahaziel.club.tutorial.ProgressStore;
import dev.jahaziel.club.tutorial.StepVerifier;
import dev.jahaziel.datatools.CsvReader;
import dev.jahaziel.datatools.CsvWriter;
import dev.jahaziel.datatools.FileHistoryService;
import dev.jahaziel.datatools.NotebookRunner;
import dev.jahaziel.datatools.SearchIndex;
import dev.jahaziel.datatools.SqliteBrowser;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.ops.ApprovalQueue;
import dev.jahaziel.ops.AuditQueryService;
import dev.jahaziel.ops.GdprService;
import dev.jahaziel.ops.ScheduledBackupService;
import dev.jahaziel.ops.SupportBundleService;
import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.spi.AuditEvent;
import dev.jahaziel.spi.AuditQuery;
import dev.jahaziel.spi.AuditSink;
import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceTaskService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Wires the agent, data-tool, club and ops services used by the API controllers. */
@Configuration
public class ApiModulesConfig {
    @Bean
    @ConditionalOnMissingBean
    public AgentStore agentStore(
            @Value("${jahaziel.storage.root:data}") String storageRoot, ObjectMapper json) {
        return new AgentStore(Path.of(storageRoot), json);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditSink apiAuditSink(AuditService audit) {
        return new AuditSink() {
            @Override
            public void write(AuditEvent event) {
                audit.append(
                        new AuditRecord(
                                event.eventId(),
                                event.timestamp(),
                                event.actor(),
                                event.actorType(),
                                event.action(),
                                event.target(),
                                event.targetType(),
                                event.outcome(),
                                event.sourceIp(),
                                event.sourceNode(),
                                event.details(),
                                event.prevHash(),
                                event.hash()));
            }

            @Override
            public java.util.stream.Stream<AuditEvent> query(AuditQuery query) {
                return java.util.stream.Stream.<AuditEvent>empty();
            }

            @Override
            public int deleteBefore(java.time.Instant cutoff) {
                return 0;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentAudit agentAudit(AuditSink apiAuditSink) {
        return new AgentAudit(apiAuditSink);
    }

    @Bean
    @ConditionalOnMissingBean
    public StrictPatchParser strictPatchParser(AgentStore store) {
        return new StrictPatchParser(store);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentSessionService agentSessions(AgentStore store, AgentToolService tools) {
        return new AgentSessionService(store, tools);
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptLibrary promptLibrary(AgentStore store) {
        return new PromptLibrary(store);
    }

    @Bean
    @ConditionalOnMissingBean
    public DiffReviewService diffReview(
            AgentStore store, StrictPatchParser parser, AgentAudit audit) {
        return new DiffReviewService(store, parser, audit);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentChatService agentChat(AgentStore store) {
        return new AgentChatService(store);
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelCatalog modelCatalog(
            @Value("${jahaziel.agent.models:}") String modelIds,
            @Value("${jahaziel.agent.local-endpoint:}") String endpoint) {
        return new ModelCatalog(split(modelIds), blankToNull(endpoint));
    }

    @Bean
    @ConditionalOnMissingBean
    public UsageMeters usageMeters(AgentStore store) {
        return new UsageMeters(store);
    }

    @Bean
    @ConditionalOnMissingBean
    public SqliteBrowser sqliteBrowser(@Value("${jahaziel.storage.root:data}") String storageRoot) {
        return new SqliteBrowser(Path.of(storageRoot));
    }

    @Bean
    @ConditionalOnMissingBean
    public CsvReader csvReader() {
        return new CsvReader();
    }

    @Bean
    @ConditionalOnMissingBean
    public CsvWriter csvWriter(WorkspaceFiles files) {
        return new CsvWriter(files);
    }

    @Bean
    @ConditionalOnMissingBean
    public NotebookRunner notebookRunner(
            @Value("${jahaziel.storage.root:data}") String storageRoot,
            WorkspaceTaskService tasks) {
        return new NotebookRunner(Path.of(storageRoot), tasks);
    }

    @Bean
    @ConditionalOnMissingBean
    public SearchIndex searchIndex(
            @Value("${jahaziel.storage.root:data}") String storageRoot,
            @Value("${jahaziel.search.ignore:*.tmp,node_modules,.git}") String ignored) {
        return new SearchIndex(Path.of(storageRoot), split(ignored));
    }

    @Bean
    @ConditionalOnMissingBean
    public FileHistoryService fileHistory(SnapshotService snapshots, WorkspaceFiles files) {
        return new FileHistoryService(snapshots, files);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClubSessionService clubSessions(
            WorkspaceService workspaces,
            WorkspaceTaskService tasks,
            @Value("${jahaziel.default-node:node-a}") String node) {
        return new ClubSessionService(workspaces, tasks, new NodeId(node));
    }

    @Bean
    @ConditionalOnMissingBean
    public ShowcaseGallery showcase(AuthorizationService auth) {
        return new ShowcaseGallery(auth);
    }

    @Bean
    @ConditionalOnMissingBean
    public CheckpointService checkpoints(
            SnapshotService snapshots, ShowcaseGallery showcase, AuthorizationService auth) {
        return new CheckpointService(snapshots, showcase, auth);
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionExporter sessionExporter() {
        return new SessionExporter();
    }

    @Bean
    @ConditionalOnMissingBean
    public StepVerifier stepVerifier(WorkspaceTaskService tasks) {
        return new StepVerifier(tasks);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProgressStore clubProgress(@Value("${jahaziel.storage.root:data}") String storageRoot) {
        return new ProgressStore(Path.of(storageRoot).resolve("club"));
    }

    @Bean
    @ConditionalOnMissingBean
    public ModerationService clubModeration(
            @Value("${jahaziel.storage.root:data}") String storageRoot) {
        return new ModerationService(Path.of(storageRoot).resolve("club"));
    }

    @Bean
    @ConditionalOnMissingBean
    public ClubService clubService(
            ClubSessionService sessions,
            StepVerifier verifier,
            ProgressStore progress,
            CheckpointService checkpoints,
            SessionExporter exporter) {
        return new ClubService(sessions, verifier, progress, checkpoints, exporter);
    }

    @Bean
    @ConditionalOnMissingBean
    public SupportBundleService supportBundle(
            @Value("${jahaziel.storage.root:data}") String storageRoot, Clock clock) {
        return new SupportBundleService(Path.of(storageRoot), clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScheduledBackupService scheduledBackup(
            @Value("${jahaziel.storage.root:data}") String storageRoot, Clock clock) {
        return new ScheduledBackupService(Path.of(storageRoot), clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditQueryService auditQuery() {
        return new AuditQueryService();
    }

    @Bean
    @ConditionalOnMissingBean
    public GdprService gdpr(
            @Value("${jahaziel.storage.root:data}") String storageRoot, Clock clock) {
        return new GdprService(Path.of(storageRoot), clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApprovalQueue approvalQueue(
            @Value("${jahaziel.storage.root:data}") String storageRoot, Clock clock) {
        return new ApprovalQueue(Path.of(storageRoot), clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClubRegistry clubRegistry() {
        return new ClubRegistry();
    }

    private static List<String> split(String raw) {
        List<String> result = new ArrayList<String>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        String[] parts = raw.split(",");
        for (int index = 0; index < parts.length; index++) {
            String value = parts[index].trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
