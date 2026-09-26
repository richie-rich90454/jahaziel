/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import dev.jahaziel.gateway.auth.JwtKeys;
import dev.jahaziel.gateway.auth.JwtVerifier;
import dev.jahaziel.gateway.proxy.GatewayRelayFilter;
import dev.jahaziel.gateway.proxy.GatewayServer;
import dev.jahaziel.gateway.proxy.LoopbackTargetResolver;
import dev.jahaziel.gateway.routing.HostParser;
import dev.jahaziel.gateway.routing.WorkspaceDirectory;
import dev.jahaziel.nodeagent.audit.AuditSpool;
import dev.jahaziel.nodeagent.service.LocalProcessBackend;
import dev.jahaziel.nodeagent.service.NodeAgentService;
import dev.jahaziel.nodeagent.storage.FolderStorage;
import dev.jahaziel.nodeagent.storage.NioCommandRunner;
import dev.jahaziel.nodeagent.sync.SyncServer;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.WorkspaceStorage;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;

/**
 * Portable single-process wiring. Active only when the fat jar sets {@code
 * jahaziel.dist.enabled=true}: the file backend, folder storage, sync server, and gateway replace
 * the split-topology stubs. The plain control-plane jar never sees these beans.
 */
@Configuration
@ConditionalOnProperty(name = "jahaziel.dist.enabled", havingValue = "true")
public class DistConfig {
  /**
   * Returns folder storage over the data root. Pure NIO, so every OS works.
   *
   * @param storageRoot the data root
   * @return the storage
   */
  @Bean
  @Primary
  public WorkspaceStorage distStorage(@Value("${jahaziel.storage.root}") String storageRoot) {
    return new FolderStorage(new NioCommandRunner(), storageRoot);
  }

  /**
   * Returns the zero-install local backend.
   *
   * @param storage the folder storage
   * @return the backend
   */
  @Bean
  @Primary
  public NodeBackend distBackend(WorkspaceStorage storage) {
    return new LocalProcessBackend(storage);
  }

  /**
   * Starts the file-sync server on loopback.
   *
   * @param storageRoot the data root
   * @return the running server, stopped with the context
   */
  @Bean(destroyMethod = "stop")
  public SyncServer distSyncServer(@Value("${jahaziel.storage.root}") String storageRoot)
      throws java.io.IOException {
    SyncServer sync = new SyncServer(SyncServer.LOOPBACK_PORT, Path.of(storageRoot, "sync"));
    sync.start();
    return sync;
  }

  /**
   * Returns the node facade over the local backend.
   *
   * @param backend the local backend
   * @param storage the folder storage
   * @param sync the sync server
   * @param storageRoot the data root
   * @return the facade
   */
  @Bean
  public NodeAgentService distNodeAgent(
      NodeBackend backend,
      WorkspaceStorage storage,
      SyncServer sync,
      @Value("${jahaziel.storage.root}") String storageRoot) {
    return new NodeAgentService(
        backend, storage, new AuditSpool(Path.of(storageRoot, "audit")), sync);
  }

  /**
   * Returns the gateway routing table. One instance per process: the control plane registers routes
   * into it and the gateway reads them.
   *
   * @return the route registry
   */
  @Bean
  public WorkspaceDirectory distRouteRegistry() {
    return new WorkspaceDirectory();
  }

  /**
   * Relays workspace traffic on the application port, so browsers use a single origin. Runs before
   * Spring Security: workspace hosts are authenticated by session token here and never reach the
   * API chain; every other host passes through untouched.
   *
   * @param domain the public domain
   * @param publicKeyFile the session public key
   * @param issuer the token issuer
   * @param jwtTtlSeconds the control plane token lifetime, the gateway's acceptance ceiling
   * @param routeRegistry the shared routing table
   * @param sessions the session minter, for cookie refresh
   * @return the registration
   */
  @Bean
  public FilterRegistrationBean<GatewayRelayFilter> gatewayRelayFilter(
      @Value("${jahaziel.dist.domain:${jahaziel.domain}}") String domain,
      @Value("${jahaziel.jwt.public-key-file}") String publicKeyFile,
      @Value("${jahaziel.jwt.issuer:https://code.example.com}") String issuer,
      @Value("${jahaziel.jwt.ttl-seconds:900}") long jwtTtlSeconds,
      WorkspaceDirectory distRouteRegistry,
      dev.jahaziel.identity.api.JwtService sessions) {
    RSAPublicKey key = JwtKeys.loadPublicKey(Path.of(publicKeyFile));
    GatewayRelayFilter filter =
        new GatewayRelayFilter(
            new HostParser(domain),
            new JwtVerifier(
                key,
                issuer,
                "jahaziel-code-server",
                java.time.Duration.ofSeconds(jwtTtlSeconds + 60)),
            distRouteRegistry,
            new LoopbackTargetResolver(),
            sessions,
            java.time.Duration.ofSeconds(jwtTtlSeconds));
    FilterRegistrationBean<GatewayRelayFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  /**
   * Runs the gateway beside the API server. Disabled in bare control-plane mode.
   *
   * @param port the gateway port
   * @param domain the public domain
   * @param publicKeyFile the session public key
   * @param issuer the token issuer
   * @param jwtTtlSeconds the control plane token lifetime, the gateway's acceptance ceiling
   * @param routeRegistry the shared routing table
   * @return the lifecycle
   */
  @Bean
  @ConditionalOnProperty(name = "jahaziel.dist.gateway-enabled", havingValue = "true")
  public SmartLifecycle distGateway(
      @Value("${jahaziel.dist.gateway-port:8081}") int port,
      @Value("${jahaziel.dist.domain:${jahaziel.domain}}") String domain,
      @Value("${jahaziel.jwt.public-key-file}") String publicKeyFile,
      @Value("${jahaziel.jwt.issuer:https://code.example.com}") String issuer,
      @Value("${jahaziel.jwt.ttl-seconds:900}") long jwtTtlSeconds,
      WorkspaceDirectory distRouteRegistry) {
    RSAPublicKey key = JwtKeys.loadPublicKey(Path.of(publicKeyFile));
    GatewayServer server =
        new GatewayServer(
            new HostParser(domain),
            new JwtVerifier(
                key,
                issuer,
                "jahaziel-code-server",
                java.time.Duration.ofSeconds(jwtTtlSeconds + 60)),
            distRouteRegistry,
            new LoopbackTargetResolver());
    return new SmartLifecycle() {
      private volatile Thread thread;

      @Override
      public void start() {
        thread = Thread.ofVirtual().name("dist-gateway").start(() -> server.serve(port));
      }

      @Override
      public void stop() {
        server.stop();
      }

      @Override
      public boolean isRunning() {
        Thread current = thread;
        return current != null && current.isAlive();
      }
    };
  }
}
