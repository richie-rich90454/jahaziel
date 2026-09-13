/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.config;

import dev.jahaziel.administration.api.AdminService;
import dev.jahaziel.administration.internal.AdminServiceImpl;
import dev.jahaziel.administration.persistence.AdminActionRepository;
import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.audit.internal.AuditCollectorJob;
import dev.jahaziel.audit.internal.AuditRetentionJob;
import dev.jahaziel.audit.internal.AuditServiceImpl;
import dev.jahaziel.audit.internal.AuditSpoolReader;
import dev.jahaziel.audit.internal.PartitionProvisionJob;
import dev.jahaziel.audit.persistence.AuditEventRepository;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.authorization.internal.AuthorizationServiceImpl;
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
import dev.jahaziel.monitoring.api.MetricsService;
import dev.jahaziel.monitoring.internal.MetricsServiceImpl;
import dev.jahaziel.monitoring.internal.ResourceSamplingJob;
import dev.jahaziel.monitoring.persistence.ResourceMetricRepository;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.networking.internal.PortServiceImpl;
import dev.jahaziel.networking.persistence.WorkspacePortRepository;
import dev.jahaziel.node.api.NodeService;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.node.internal.HealthCheckJob;
import dev.jahaziel.node.internal.NodeServiceImpl;
import dev.jahaziel.node.persistence.NodeRepository;
import dev.jahaziel.platform.internal.CleanupJob;
import dev.jahaziel.platform.internal.InProcessScheduler;
import dev.jahaziel.policy.api.PolicyService;
import dev.jahaziel.policy.internal.PolicyServiceImpl;
import dev.jahaziel.policy.persistence.PolicyRepository;
import dev.jahaziel.runtime.api.NodeAgentUnreachableException;
import dev.jahaziel.runtime.api.RuntimeService;
import dev.jahaziel.runtime.internal.RuntimeServiceImpl;
import dev.jahaziel.runtime.internal.VmIdleCheckJob;
import dev.jahaziel.runtime.persistence.VirtualMachineRepository;
import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.snapshot.internal.DailySnapshotJob;
import dev.jahaziel.snapshot.internal.SnapshotRetentionJob;
import dev.jahaziel.snapshot.internal.SnapshotServiceImpl;
import dev.jahaziel.snapshot.persistence.SnapshotRepository;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.WorkspaceHandle;
import dev.jahaziel.spi.WorkspaceStorage;
import dev.jahaziel.storage.internal.CacheWarmJob;
import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.terminal.internal.TerminalServiceImpl;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.internal.WorkspaceServiceImpl;
import dev.jahaziel.workspace.persistence.WorkspaceAclRepository;
import dev.jahaziel.workspace.persistence.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Service wiring. Every collaborator arrives by constructor; the scheduler starts after all jobs
 * are registered and stops with the context.
 */
@Configuration
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
      SnapshotRepository snapshots,
      UserService users,
      @Value("${jahaziel.storage.root}") String storageRoot) {
    return new WorkspaceServiceImpl(workspaces, acl, machines, snapshots, users, storageRoot);
  }

  /**
   * Returns the node backend. Fail-safe stub until the mTLS gRPC transport lands: every call
   * refuses, so starts fail closed instead of half-provisioning.
   */
  @Bean
  public NodeBackend backend() {
    return new UnreachableBackend();
  }

  /** Returns workspace storage. Same fail-safe stub as the backend. */
  @Bean
  public WorkspaceStorage storage() {
    return new UnreachableStorage();
  }

  /** Returns runtime lifecycle orchestration. */
  @Bean
  public RuntimeService runtime(
      NodeBackend backend,
      WorkspaceService workspaces,
      @Value("${jahaziel.image-ref:base-image:2026.09}") String imageRef,
      @Value("${jahaziel.node-id:node-a}") String nodeId) {
    return new RuntimeServiceImpl(backend, workspaces, new ImageRef(imageRef), new NodeId(nodeId));
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

  /** Returns metric rollups. */
  @Bean
  public MetricsService metrics(ResourceMetricRepository metrics) {
    return new MetricsServiceImpl(metrics);
  }

  /** Returns terminal sessions. */
  @Bean
  public TerminalService terminals() {
    return new TerminalServiceImpl();
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
    return scheduler;
  }

  /** Fail-safe node backend. Refuses every call until the agent transport lands. */
  static final class UnreachableBackend implements NodeBackend {
    @Override
    public String name() {
      return "unreachable";
    }

    @Override
    public WorkspaceHandle prepare(PrepareRequest request) {
      throw new NodeAgentUnreachableException("node agent transport not configured");
    }

    @Override
    public RuntimeHandle start(RuntimeRequest request) {
      throw new NodeAgentUnreachableException("node agent transport not configured");
    }

    @Override
    public void stop(RuntimeHandle handle, boolean graceful) {
      throw new NodeAgentUnreachableException("node agent transport not configured");
    }

    @Override
    public void destroy(WorkspaceHandle handle) {
      throw new NodeAgentUnreachableException("node agent transport not configured");
    }
  }

  /** Fail-safe workspace storage. Refuses every call until the agent transport lands. */
  static final class UnreachableStorage implements WorkspaceStorage {
    @Override
    public WorkspaceHandle prepare(
        java.util.UUID workspaceId,
        java.util.UUID ownerId,
        dev.jahaziel.spi.ImageRef image,
        QuotaBytes quota) {
      throw new NodeAgentUnreachableException("node agent transport not configured");
    }

    @Override
    public void destroy(WorkspaceHandle handle) {
      throw new NodeAgentUnreachableException("node agent transport not configured");
    }

    @Override
    public dev.jahaziel.spi.SnapshotHandle snapshot(
        WorkspaceHandle handle, dev.jahaziel.spi.SnapshotTag tag) {
      throw new NodeAgentUnreachableException("node agent transport not configured");
    }

    @Override
    public void deleteSnapshot(dev.jahaziel.spi.SnapshotHandle snapshot) {
      throw new NodeAgentUnreachableException("node agent transport not configured");
    }

    @Override
    public void restore(WorkspaceHandle handle, dev.jahaziel.spi.SnapshotHandle snapshot) {
      throw new NodeAgentUnreachableException("node agent transport not configured");
    }

    @Override
    public dev.jahaziel.spi.StorageUsage usage(WorkspaceHandle handle) {
      throw new NodeAgentUnreachableException("node agent transport not configured");
    }
  }
}
