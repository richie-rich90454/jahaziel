/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.config;

import dev.jahaziel.administration.api.AdminService;
import dev.jahaziel.administration.internal.AdminServiceImpl;
import dev.jahaziel.administration.persistence.AdminActionRepository;
import dev.jahaziel.agenttools.api.AgentToolService;
import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.audit.internal.AuditCollectorJob;
import dev.jahaziel.audit.internal.AuditRetentionJob;
import dev.jahaziel.audit.internal.AuditServiceImpl;
import dev.jahaziel.audit.internal.AuditSpoolReader;
import dev.jahaziel.audit.internal.PartitionProvisionJob;
import dev.jahaziel.audit.persistence.AuditEventRepository;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.authorization.internal.AuthorizationServiceImpl;
import dev.jahaziel.debug.internal.DebugAdapterProperties;
import dev.jahaziel.debug.internal.DebugServiceImpl;
import dev.jahaziel.extension.api.ExtensionPolicyService;
import dev.jahaziel.extension.internal.ExtensionPolicyServiceImpl;
import dev.jahaziel.extension.persistence.ExtensionPolicyRepository;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.internal.JwtServiceImpl;
import dev.jahaziel.identity.internal.UserServiceImpl;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.UserGroupRepository;
import dev.jahaziel.identity.persistence.UserRoleRepository;
import dev.jahaziel.language.api.LanguagePackService;
import dev.jahaziel.language.internal.LanguagePackServiceImpl;
import dev.jahaziel.language.internal.LanguageServerProcessManager;
import dev.jahaziel.monitoring.api.MetricsService;
import dev.jahaziel.monitoring.internal.MetricsServiceImpl;
import dev.jahaziel.monitoring.internal.ResourceSamplingJob;
import dev.jahaziel.monitoring.persistence.ResourceMetricRepository;
import dev.jahaziel.networking.api.GatewayRouteService;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.networking.internal.GatewayRouteServiceImpl;
import dev.jahaziel.networking.internal.GatewayRouteSyncJob;
import dev.jahaziel.networking.internal.LoopbackPortStatusProbe;
import dev.jahaziel.networking.internal.PortServiceImpl;
import dev.jahaziel.networking.persistence.WorkspacePortRepository;
import dev.jahaziel.node.api.NodeService;
import dev.jahaziel.node.internal.HealthCheckJob;
import dev.jahaziel.node.internal.NodeServiceImpl;
import dev.jahaziel.node.persistence.NodeRepository;
import dev.jahaziel.platform.internal.InProcessScheduler;
import dev.jahaziel.policy.api.PolicyService;
import dev.jahaziel.policy.internal.PolicyServiceImpl;
import dev.jahaziel.policy.persistence.PolicyRepository;
import dev.jahaziel.runtime.api.RuntimeService;
import dev.jahaziel.runtime.internal.RuntimeServiceImpl;
import dev.jahaziel.runtime.internal.VmIdleCheckJob;
import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.snapshot.internal.DailySnapshotJob;
import dev.jahaziel.snapshot.internal.SnapshotRetentionJob;
import dev.jahaziel.snapshot.internal.SnapshotServiceImpl;
import dev.jahaziel.snapshot.persistence.SnapshotRepository;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.WorkspaceStorage;
import dev.jahaziel.storage.internal.CacheWarmJob;
import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.terminal.internal.TerminalServiceImpl;
import dev.jahaziel.workspace.api.WorkspaceDirectoryService;
import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.api.WorkspaceProcessLifecycle;
import dev.jahaziel.workspace.api.WorkspaceProcessRegistry;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.internal.CleanupJob;
import dev.jahaziel.workspace.internal.WorkspaceDirectoryServiceImpl;
import dev.jahaziel.workspace.internal.WorkspaceFilesServiceImpl;
import dev.jahaziel.workspace.internal.WorkspaceProcessRegistryImpl;
import dev.jahaziel.workspace.internal.WorkspaceServiceImpl;
import dev.jahaziel.workspace.persistence.VirtualMachineRepository;
import dev.jahaziel.workspace.persistence.WorkspaceAclRepository;
import dev.jahaziel.workspace.persistence.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Service wiring. Every collaborator arrives by constructor; the scheduler starts after all jobs
 * are registered and stops with the context.
 */
@Configuration
@EnableConfigurationProperties(DebugAdapterProperties.class)
public class AppConfig {
  /** Returns the system clock. */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  /** Returns users. */
  @Bean
  public UserService users(
      AppUserRepository users, UserRoleRepository roles, UserGroupRepository groups) {
    return new UserServiceImpl(users, roles, groups);
  }

  /** Returns session tokens. Keys load from PEM files, failing fast when absent. */
  @Bean
  public JwtService tokens(
      @Value("${jahaziel.jwt.private-key-file}") String privateKeyFile,
      @Value("${jahaziel.jwt.public-key-file}") String publicKeyFile,
      @Value("${jahaziel.jwt.issuer}") String issuer) {
    RSAPrivateKey privateKey = JwtServiceImpl.loadPrivateKey(Path.of(privateKeyFile));
    RSAPublicKey publicKey = JwtServiceImpl.loadPublicKey(Path.of(publicKeyFile));
    return new JwtServiceImpl(privateKey, publicKey, issuer, "jahaziel-code-server");
  }

  /** Returns authorization checks. */
  @Bean
  public AuthorizationService auth(UserService users, WorkspaceService workspaces) {
    return new AuthorizationServiceImpl(users, workspaces);
  }

  /** Returns workspaces. */
  @Bean
  public WorkspaceService workspaces(
      WorkspaceRepository workspaces,
      WorkspaceAclRepository acl,
      VirtualMachineRepository machines,
      UserService users,
      @Value("${jahaziel.storage.root}") String storageRoot,
      WorkspaceProcessLifecycle processes) {
    return new WorkspaceServiceImpl(workspaces, acl, machines, users, storageRoot, processes);
  }

  /** Returns workspace-rooted file operations. */
  @Bean
  public WorkspaceFiles workspaceFiles(@Value("${jahaziel.storage.root}") String storageRoot) {
    return new WorkspaceFilesServiceImpl(storageRoot);
  }

  /** Returns the bounded HTTPS client used only after an explicit language-pack selection. */
  @Bean
  public HttpClient languagePackHttpClient() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }

  /** Returns the shared, pinned, opt-in language-pack cache. */
  @Bean
  public LanguagePackService languagePacks(
      @Value("${jahaziel.storage.root}") String storageRoot,
      HttpClient languagePackHttpClient,
      @Value("${jahaziel.language-tools.node-executable:/usr/bin/node}") String nodeExecutable) {
    return new LanguagePackServiceImpl(storageRoot, languagePackHttpClient, nodeExecutable);
  }

  /** Returns confined workspace home paths to server-side process services. */
  @Bean
  public WorkspaceDirectoryService workspaceDirectories(
      @Value("${jahaziel.storage.root}") String storageRoot) {
    return new WorkspaceDirectoryServiceImpl(storageRoot);
  }

  /** Returns the persistent registry for server-managed workspace processes. */
  @Bean
  public WorkspaceProcessRegistry workspaceProcessRegistry(
      @Value("${jahaziel.storage.root}") String storageRoot) {
    return new WorkspaceProcessRegistryImpl(storageRoot);
  }

  /** Returns the bounded language-server process manager. */
  @Bean(destroyMethod = "closeAll")
  public LanguageServerProcessManager languageServerProcesses(
      LanguagePackService packs,
      WorkspaceDirectoryService directories,
      tools.jackson.databind.ObjectMapper json,
      @Value("${jahaziel.storage.root}") String storageRoot,
      WorkspaceProcessRegistry processRegistry) {
    return new LanguageServerProcessManager(packs, directories, json, storageRoot, processRegistry);
  }

  /** Combines workspace shutdown across language-service and debugger processes. */
  @Bean
  @Primary
  public WorkspaceProcessLifecycle workspaceProcessLifecycle(
      LanguageServerProcessManager languageProcesses,
      ObjectProvider<DebugServiceImpl> debugProcesses) {
    return new WorkspaceProcessLifecycleComposite(languageProcesses, debugProcesses);
  }

  /** Returns runtime lifecycle orchestration. */
  @Bean
  public RuntimeService runtime(
      NodeBackend backend,
      WorkspaceService workspaces,
      @Value("${jahaziel.image-ref:base-image:2026.09}") String imageRef,
      PortService ports,
      dev.jahaziel.runtime.internal.BootProgressService progress,
      TerminalService terminals,
      dev.jahaziel.workspace.api.WorkspaceTaskService tasks,
      WorkspaceProcessLifecycle languageProcesses) {
    return new RuntimeServiceImpl(
        backend,
        workspaces,
        new ImageRef(imageRef),
        ports,
        progress,
        terminals,
        tasks,
        languageProcesses);
  }

  /** Returns nodes. */
  @Bean
  public NodeService nodes(NodeRepository nodes) {
    return new NodeServiceImpl(nodes);
  }

  /** Returns the audit log. */
  @Bean
  public AuditService audit(AuditEventRepository events) {
    return new AuditServiceImpl(events);
  }

  /** Returns policies. */
  @Bean
  public PolicyService policies(PolicyRepository policies) {
    return new PolicyServiceImpl(policies);
  }

  /** Returns extension policies. */
  @Bean
  public ExtensionPolicyService extensionPolicies(ExtensionPolicyRepository policies) {
    return new ExtensionPolicyServiceImpl(policies);
  }

  /** Returns port bindings. */
  @Bean
  public PortService ports(
      WorkspacePortRepository ports, UserService users, WorkspaceService workspaces) {
    return new PortServiceImpl(ports, users, workspaces);
  }

  /** Returns the bounded loopback listener probe used by workspace preview controls. */
  @Bean
  public dev.jahaziel.networking.api.PortStatusProbe portStatusProbe() {
    return new LoopbackPortStatusProbe();
  }

  /** Returns the gateway route reconciler. No registry means the split topology: a no-op. */
  @Bean
  public GatewayRouteService gatewayRoutes(
      java.util.Optional<dev.jahaziel.spi.RouteRegistry> registry,
      WorkspaceService workspaces,
      UserService users,
      PortService ports) {
    return new GatewayRouteServiceImpl(registry, workspaces, users, ports);
  }

  /**
   * Returns the public gateway address behind which workspace ports are served. The URL names a
   * listener that actually relays workspace hosts: the standalone socket gateway when it is
   * enabled, otherwise the application port, whose relay filter always serves them.
   */
  @Bean
  public dev.jahaziel.networking.api.GatewayEndpoint gatewayEndpoint(
      @Value("${jahaziel.gateway.public-scheme:http}") String scheme,
      @Value("${jahaziel.domain}") String domain,
      @Value("${jahaziel.dist.gateway-enabled:false}") boolean gatewayEnabled,
      @Value("${jahaziel.dist.gateway-port:8081}") int gatewayPort,
      @Value("${server.port:11451}") int serverPort) {
    return new dev.jahaziel.networking.api.GatewayEndpoint(
        scheme, domain, gatewayEnabled ? gatewayPort : serverPort);
  }

  /** Returns the live boot-phase reports. */
  @Bean
  public dev.jahaziel.runtime.internal.BootProgressService bootProgress() {
    return new dev.jahaziel.runtime.internal.BootProgressService();
  }

  /** Returns the workspace read-model factory, which attaches tokenized port URLs. */
  @Bean
  public dev.jahaziel.api.WorkspaceDtoFactory workspaceDtos(
      PortService ports,
      UserService users,
      JwtService tokens,
      dev.jahaziel.networking.api.GatewayEndpoint endpoint,
      dev.jahaziel.runtime.internal.BootProgressService progress,
      @Value("${jahaziel.jwt.ttl-seconds:900}") long tokenTtlSeconds) {
    return new dev.jahaziel.api.WorkspaceDtoFactory(
        ports, users, tokens, endpoint, progress, tokenTtlSeconds);
  }

  /** Returns metric rollups. */
  @Bean
  public MetricsService metrics(ResourceMetricRepository metrics) {
    return new MetricsServiceImpl(metrics);
  }

  /** Returns terminal sessions. */
  @Bean(destroyMethod = "closeAll")
  public TerminalService terminals(
      WorkspaceDirectoryService directories,
      WorkspaceProcessRegistry processRegistry,
      WorkspaceService workspaces,
      AgentToolService agentTools) {
    return new TerminalServiceImpl(directories, processRegistry, workspaces, agentTools);
  }

  /** Returns snapshots. */
  @Bean
  public SnapshotService snapshots(
      SnapshotRepository snapshots,
      WorkspaceService workspaces,
      WorkspaceStorage storage,
      AuditService audit,
      @Value("${jahaziel.storage.root}") String storageRoot) {
    return new SnapshotServiceImpl(snapshots, workspaces, storage, audit, storageRoot);
  }

  /** Returns administration. */
  @Bean
  public AdminService admin(
      UserService users, WorkspaceService workspaces, AdminActionRepository actions) {
    return new AdminServiceImpl(users, workspaces, actions);
  }

  /**
   * Returns the scheduler with every job registered, started with the context.
   *
   * @return the running scheduler
   */
  @Bean(initMethod = "start", destroyMethod = "stop")
  public InProcessScheduler scheduler(
      SnapshotService snapshots,
      WorkspaceService workspaces,
      AuditService audit,
      EntityManager entities,
      MetricsService metrics,
      NodeService nodes,
      RuntimeService runtime,
      GatewayRouteService gatewayRoutes,
      Clock clock,
      @Value("${jahaziel.storage.root}") String storageRoot,
      @Value("${jahaziel.audit.retention-days:30}") int retentionDays,
      @Value("${jahaziel.audit.partition-months-ahead:3}") int monthsAhead,
      @Value("${jahaziel.audit.staging-dir:${jahaziel.storage.root}/audit}") String stagingDir,
      @Value("${jahaziel.snapshot.auto-retention-count:30}") int autoRetention,
      @Value("${jahaziel.snapshot.pre-delete-retention-days:90}") int preDeleteDays) {
    InProcessScheduler scheduler = new InProcessScheduler();
    scheduler.register(new DailySnapshotJob(snapshots, workspaces));
    scheduler.register(
        new SnapshotRetentionJob(snapshots, workspaces, autoRetention, preDeleteDays, clock));
    scheduler.register(new AuditRetentionJob(entities, retentionDays, clock));
    scheduler.register(new PartitionProvisionJob(entities, monthsAhead, clock));
    scheduler.register(new VmIdleCheckJob(workspaces, runtime));
    scheduler.register(new ResourceSamplingJob(metrics, workspaces, clock));
    scheduler.register(new HealthCheckJob(nodes, Duration.ofSeconds(60), clock));
    scheduler.register(new CleanupJob(workspaces, storageRoot));
    scheduler.register(new CacheWarmJob(storageRoot));
    scheduler.register(new AuditCollectorJob(new AuditSpoolReader(audit), Path.of(stagingDir)));
    scheduler.register(new GatewayRouteSyncJob(gatewayRoutes));
    return scheduler;
  }

    /** Returns the agent's persistent store. */
    @Bean
    public dev.jahaziel.agent.AgentStore agentStore(
            @Value("${jahaziel.storage.root}") String storageRoot,
            tools.jackson.databind.ObjectMapper json) {
        return new dev.jahaziel.agent.AgentStore(Path.of(storageRoot), json);
    }

    /** Returns the append-only audit sink used by agent decisions. */
    @Bean
    public dev.jahaziel.spi.AuditSink auditSink(AuditService audit) {
        return new AuditSinkAdapter(audit);
    }

    /** Returns agent decision auditing. */
    @Bean
    public dev.jahaziel.agent.AgentAudit agentAudit(dev.jahaziel.spi.AuditSink sink) {
        return new dev.jahaziel.agent.AgentAudit(sink);
    }

    /** Returns agent sessions backed by workspace PTYs. */
    @Bean
    public dev.jahaziel.agent.session.AgentSessionService agentSessions(
            dev.jahaziel.agent.AgentStore store, AgentToolService tools) {
        return new dev.jahaziel.agent.session.AgentSessionService(store, tools);
    }

    /** Returns the prompt library. */
    @Bean
    public dev.jahaziel.agent.prompt.PromptLibrary promptLibrary(dev.jahaziel.agent.AgentStore store) {
        return new dev.jahaziel.agent.prompt.PromptLibrary(store);
    }

    /** Returns the strict patch parser. */
    @Bean
    public dev.jahaziel.agent.diff.StrictPatchParser strictPatchParser(
            dev.jahaziel.agent.AgentStore store) {
        return new dev.jahaziel.agent.diff.StrictPatchParser(store);
    }

    /** Returns diff review backed by the patch parser. */
    @Bean
    public dev.jahaziel.agent.diff.DiffReviewService diffReviews(
            dev.jahaziel.agent.AgentStore store,
            dev.jahaziel.agent.diff.StrictPatchParser parser,
            dev.jahaziel.agent.AgentAudit audit) {
        return new dev.jahaziel.agent.diff.DiffReviewService(store, parser, audit);
    }

    /** Returns agent chat history. */
    @Bean
    public dev.jahaziel.agent.chat.AgentChatService agentChat(dev.jahaziel.agent.AgentStore store) {
        return new dev.jahaziel.agent.chat.AgentChatService(store);
    }

    /** Returns the configuration-driven model allowlist. */
    @Bean
    public dev.jahaziel.agent.model.ModelCatalog modelCatalog(
            @Value("${jahaziel.agent.models:}") String allowedIds,
            @Value("${jahaziel.agent.local-endpoint:}") String endpoint) {
        return new dev.jahaziel.agent.model.ModelCatalog(
                ConfigValues.csv(allowedIds), ConfigValues.emptyToNull(endpoint));
    }

    /** Returns per-agent usage meters. */
    @Bean
    public dev.jahaziel.agent.usage.UsageMeters usageMeters(dev.jahaziel.agent.AgentStore store) {
        return new dev.jahaziel.agent.usage.UsageMeters(store);
    }

    /** Returns bounded SQLite browsing under the data root. */
    @Bean
    public dev.jahaziel.datatools.SqliteBrowser sqliteBrowser(
            @Value("${jahaziel.storage.root}") String storageRoot) {
        return new dev.jahaziel.datatools.SqliteBrowser(Path.of(storageRoot));
    }

    /** Returns the stateless CSV parser. */
    @Bean
    public dev.jahaziel.datatools.CsvReader csvReader() {
        return new dev.jahaziel.datatools.CsvReader();
    }

    /** Returns workspace-rooted CSV writing. */
    @Bean
    public dev.jahaziel.datatools.CsvWriter csvWriter(WorkspaceFiles files) {
        return new dev.jahaziel.datatools.CsvWriter(files);
    }

    /** Returns bounded notebook execution. */
    @Bean
    public dev.jahaziel.datatools.NotebookRunner notebookRunner(
            @Value("${jahaziel.storage.root}") String storageRoot,
            dev.jahaziel.workspace.api.WorkspaceTaskService tasks) {
        return new dev.jahaziel.datatools.NotebookRunner(Path.of(storageRoot), tasks);
    }

    /** Returns the content search index. */
    @Bean
    public dev.jahaziel.datatools.SearchIndex searchIndex(
            @Value("${jahaziel.storage.root}") String storageRoot,
            @Value("${jahaziel.search.ignore-patterns:}") String ignorePatterns) {
        return new dev.jahaziel.datatools.SearchIndex(
                Path.of(storageRoot), ConfigValues.csv(ignorePatterns));
    }

    /** Returns file history over existing snapshots. */
    @Bean
    public dev.jahaziel.datatools.FileHistoryService fileHistory(
            SnapshotService snapshots, WorkspaceFiles files) {
        return new dev.jahaziel.datatools.FileHistoryService(snapshots, files);
    }

    /** Returns the JDBC-backed collaboration store. */
    @Bean
    @ConditionalOnMissingBean(dev.jahaziel.collaboration.CollaborationStore.class)
    public dev.jahaziel.collaboration.CollaborationStore collaborationStore(
            javax.sql.DataSource dataSource) {
        return new dev.jahaziel.collaboration.JdbcCollaborationStore(dataSource);
    }

    /** Returns collaboration auditing. */
    @Bean
    public dev.jahaziel.collaboration.CollaborationAudit collaborationAudit(
            dev.jahaziel.collaboration.CollaborationStore store) {
        return new dev.jahaziel.collaboration.CollaborationAudit(store);
    }

    /** Returns membership management. */
    @Bean
    @ConditionalOnMissingBean(dev.jahaziel.collaboration.MembershipService.class)
    public dev.jahaziel.collaboration.MembershipService membership(
            dev.jahaziel.collaboration.CollaborationStore store,
            dev.jahaziel.collaboration.CollaborationAudit audit) {
        return new dev.jahaziel.collaboration.MembershipService(store, audit);
    }

    /** Returns live presence tracking. */
    @Bean
    @ConditionalOnMissingBean(dev.jahaziel.collaboration.PresenceService.class)
    public dev.jahaziel.collaboration.PresenceService presence() {
        return new dev.jahaziel.collaboration.PresenceService();
    }

    /** Returns file comments and replies. */
    @Bean
    @ConditionalOnMissingBean(dev.jahaziel.collaboration.CommentService.class)
    public dev.jahaziel.collaboration.CommentService comments(
            dev.jahaziel.collaboration.CollaborationStore store,
            dev.jahaziel.collaboration.CollaborationAudit audit,
            dev.jahaziel.collaboration.MembershipService membership) {
        return new dev.jahaziel.collaboration.CommentService(store, audit, membership);
    }

    /** Returns live session control. */
    @Bean
    @ConditionalOnMissingBean(dev.jahaziel.collaboration.LiveSessionService.class)
    public dev.jahaziel.collaboration.LiveSessionService liveSessions(
            dev.jahaziel.collaboration.CollaborationStore store,
            dev.jahaziel.collaboration.CollaborationAudit audit,
            dev.jahaziel.collaboration.MembershipService membership) {
        return new dev.jahaziel.collaboration.LiveSessionService(store, audit, membership);
    }

    /** Returns persisted share links. */
    @Bean
    @ConditionalOnMissingBean(dev.jahaziel.collaboration.ShareLinkService.class)
    public dev.jahaziel.collaboration.ShareLinkService shareLinks(
            dev.jahaziel.collaboration.CollaborationStore store,
            dev.jahaziel.collaboration.CollaborationAudit audit,
            dev.jahaziel.collaboration.MembershipService membership) {
        return new dev.jahaziel.collaboration.ShareLinkService(store, audit, membership);
    }

    /** Returns the collaboration controller. */
    @Bean
    @ConditionalOnMissingBean(dev.jahaziel.collaboration.CollaborationController.class)
    public dev.jahaziel.collaboration.CollaborationController collaborationController(
            dev.jahaziel.collaboration.MembershipService membership,
            dev.jahaziel.collaboration.PresenceService presence,
            dev.jahaziel.collaboration.CommentService comments,
            dev.jahaziel.collaboration.LiveSessionService sessions,
            dev.jahaziel.collaboration.ShareLinkService shares,
            dev.jahaziel.collaboration.CollaborationStore store) {
        return new dev.jahaziel.collaboration.CollaborationController(
                membership, presence, comments, sessions, shares, store);
    }

    /** Returns the JDBC-backed club session store. */
    @Bean
    public dev.jahaziel.club.persistence.ClubSessionStore clubSessionStore(
            javax.sql.DataSource dataSource) {
        return new dev.jahaziel.club.persistence.ClubSessionStore(dataSource);
    }

    /** Returns the stateless tutorial parser. */
    @Bean
    public dev.jahaziel.club.tutorial.TutorialParser tutorialParser() {
        return new dev.jahaziel.club.tutorial.TutorialParser();
    }

    /** Returns lesson step verification. */
    @Bean
    public dev.jahaziel.club.tutorial.StepVerifier stepVerifier(
            dev.jahaziel.workspace.api.WorkspaceTaskService tasks) {
        return new dev.jahaziel.club.tutorial.StepVerifier(tasks);
    }

    /** Returns club progress storage under the data root. */
    @Bean
    public dev.jahaziel.club.tutorial.ProgressStore progressStore(
            @Value("${jahaziel.storage.root}") String storageRoot) {
        return new dev.jahaziel.club.tutorial.ProgressStore(
                Path.of(storageRoot, "club", "progress"));
    }

    /** Returns the curated showcase gallery. */
    @Bean
    public dev.jahaziel.club.showcase.ShowcaseGallery showcaseGallery(
            AuthorizationService authorization) {
        return new dev.jahaziel.club.showcase.ShowcaseGallery(authorization);
    }

    /** Returns checkpoint submission handling. */
    @Bean
    public dev.jahaziel.club.checkpoint.CheckpointService checkpoints(
            SnapshotService snapshots,
            dev.jahaziel.club.showcase.ShowcaseGallery gallery,
            AuthorizationService authorization) {
        return new dev.jahaziel.club.checkpoint.CheckpointService(snapshots, gallery, authorization);
    }

    /** Returns club moderation state. */
    @Bean
    public dev.jahaziel.club.moderation.ModerationService moderation(
            @Value("${jahaziel.storage.root}") String storageRoot) {
        return new dev.jahaziel.club.moderation.ModerationService(Path.of(storageRoot, "club"));
    }

    /** Returns the stateless session exporter. */
    @Bean
    public dev.jahaziel.club.export.SessionExporter sessionExporter() {
        return new dev.jahaziel.club.export.SessionExporter();
    }

    /** Returns club session orchestration. */
    @Bean
    public dev.jahaziel.club.session.ClubSessionService clubSessions(
            WorkspaceService workspaces,
            dev.jahaziel.workspace.api.WorkspaceTaskService tasks,
            @Value("${jahaziel.node.id:local}") String nodeId) {
        return new dev.jahaziel.club.session.ClubSessionService(
                workspaces, tasks, new dev.jahaziel.node.domain.NodeId(nodeId));
    }

    /** Returns the club facade. */
    @Bean
    public dev.jahaziel.club.api.ClubService club(
            dev.jahaziel.club.session.ClubSessionService sessions,
            dev.jahaziel.club.tutorial.StepVerifier verifier,
            dev.jahaziel.club.tutorial.ProgressStore progress,
            dev.jahaziel.club.checkpoint.CheckpointService checkpoints,
            dev.jahaziel.club.export.SessionExporter exporter) {
        return new dev.jahaziel.club.api.ClubService(
                sessions, verifier, progress, checkpoints, exporter);
    }

    /** Returns startup configuration validation. */
    @Bean
    public dev.jahaziel.ops.ConfigValidationService configValidation(
            @Value("${jahaziel.storage.root}") String storageRoot) {
        return new dev.jahaziel.ops.ConfigValidationService(Path.of(storageRoot));
    }

    /** Returns structured operational logging. */
    @Bean
    public dev.jahaziel.ops.StructuredLogger structuredLogger(
            @Value("${jahaziel.storage.root}") String storageRoot,
            @Value("${jahaziel.ops.log-max-bytes:10485760}") long maxBytes,
            Clock clock) {
        return new dev.jahaziel.ops.StructuredLogger(Path.of(storageRoot), maxBytes, clock);
    }

    /** Returns support-bundle creation. */
    @Bean
    public dev.jahaziel.ops.SupportBundleService supportBundles(
            @Value("${jahaziel.storage.root}") String storageRoot, Clock clock) {
        return new dev.jahaziel.ops.SupportBundleService(Path.of(storageRoot), clock);
    }

    /** Returns graceful-drain coordination for shutdown. */
    @Bean
    public dev.jahaziel.ops.GracefulDrainService gracefulDrain() {
        return new dev.jahaziel.ops.GracefulDrainService();
    }

    /** Returns scheduled backup handling. */
    @Bean
    public dev.jahaziel.ops.ScheduledBackupService scheduledBackups(
            @Value("${jahaziel.storage.root}") String storageRoot, Clock clock) {
        return new dev.jahaziel.ops.ScheduledBackupService(Path.of(storageRoot), clock);
    }

    /** Returns restore-drill handling. */
    @Bean
    public dev.jahaziel.ops.RestoreDrillService restoreDrills(
            @Value("${jahaziel.storage.root}") String storageRoot, Clock clock) {
        return new dev.jahaziel.ops.RestoreDrillService(Path.of(storageRoot), clock);
    }

    /** Returns per-user and per-workspace quotas. */
    @Bean
    public dev.jahaziel.ops.QuotaService quotas() {
        return new dev.jahaziel.ops.QuotaService();
    }

    /** Returns the shared API rate limiter. */
    @Bean
    public dev.jahaziel.ops.RateLimiter rateLimiter(Clock clock) {
        return new dev.jahaziel.ops.RateLimiter(clock);
    }

    /** Returns audit query and chain verification. */
    @Bean
    public dev.jahaziel.ops.AuditQueryService auditQueries() {
        return new dev.jahaziel.ops.AuditQueryService();
    }

    /** Returns GDPR data-subject operations. */
    @Bean
    public dev.jahaziel.ops.GdprService gdpr(
            @Value("${jahaziel.storage.root}") String storageRoot, Clock clock) {
        return new dev.jahaziel.ops.GdprService(Path.of(storageRoot), clock);
    }

    /** Returns the approval queue for sensitive operations. */
    @Bean
    public dev.jahaziel.ops.ApprovalQueue approvalQueue(
            @Value("${jahaziel.storage.root}") String storageRoot, Clock clock) {
        return new dev.jahaziel.ops.ApprovalQueue(Path.of(storageRoot), clock);
    }

    /** Returns signed outbound webhook delivery. */
    @Bean
    public dev.jahaziel.ops.OutboundWebhooks outboundWebhooks(
            @Value("${jahaziel.storage.root}") String storageRoot, Clock clock) {
        return new dev.jahaziel.ops.OutboundWebhooks(Path.of(storageRoot), clock);
    }

    /** Returns the command-name allowlist. */
    @Bean
    public dev.jahaziel.security.CommandAllowlist commandAllowlist() {
        return new dev.jahaziel.security.CommandAllowlist();
    }

    /** Returns the WebSocket origin and workspace binding guard. */
    @Bean
    public dev.jahaziel.security.WebSocketOriginGuard webSocketOriginGuard() {
        return new dev.jahaziel.security.WebSocketOriginGuard();
    }

    /** Returns session rotation and idle-expiry indexes. */
    @Bean
    public dev.jahaziel.security.SessionHardening sessionHardening(
            @Value("${jahaziel.session.idle-timeout-ms:1800000}") long idleTimeoutMillis) {
        return new dev.jahaziel.security.SessionHardening(idleTimeoutMillis);
    }

    /** Returns the dependency-audit result model. */
    @Bean
    public dev.jahaziel.security.DependencyAuditReport dependencyAuditReport() {
        return new dev.jahaziel.security.DependencyAuditReport();
    }
}
