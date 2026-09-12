/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent;

import dev.jahaziel.nodeagent.audit.AuditSpool;
import dev.jahaziel.nodeagent.cgroup.CgroupV2;
import dev.jahaziel.nodeagent.config.NodeAgentConfig;
import dev.jahaziel.nodeagent.firecracker.FirecrackerClient;
import dev.jahaziel.nodeagent.jail.Jailer;
import dev.jahaziel.nodeagent.service.FirecrackerBackend;
import dev.jahaziel.nodeagent.service.NodeAgentService;
import dev.jahaziel.nodeagent.storage.FolderStorage;
import dev.jahaziel.nodeagent.storage.ProcessCommandRunner;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Node agent entry point. Wires storage, backend, facade, jailer, Firecracker client, cgroups, and
 * the audit spool, then waits for shutdown. The mTLS gRPC transport binds in front of the facade
 * once the protobuf codegen lands.
 */
public final class NodeAgentMain {
  private NodeAgentMain() {}

  /**
   * Starts the node agent.
   *
   * @param args command line arguments, expecting --config with a path
   */
  public static void main(String[] args) throws InterruptedException {
    Path configPath = configPath(args);
    NodeAgentConfig config = NodeAgentConfig.loadFile(configPath);
    var commands = new ProcessCommandRunner();
    var storage = new FolderStorage(commands, config.storageRoot());
    var backend =
        new FirecrackerBackend(
            storage,
            new FirecrackerClient(),
            new Jailer(config.jailerBinary(), config.firecrackerBinary(), config.jailRoot()),
            new CgroupV2(Path.of(config.cgroupRoot())),
            commands,
            config);
    var service =
        new NodeAgentService(backend, storage, new AuditSpool(Path.of(config.auditStagingDir())));
    System.out.println(
        "Node agent " + config.nodeId() + " ready: " + service.getClass().getSimpleName());
    new CountDownLatch(1).await();
  }

  private static Path configPath(String[] args) {
    for (int i = 0; i + 1 < args.length; i++) {
      if (args[i].equals("--config")) {
        return Path.of(args[i + 1]);
      }
    }
    return Path.of("/etc/jahaziel/node-agent.yml");
  }
}
