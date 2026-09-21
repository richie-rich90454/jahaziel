/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Behavior of {@link NodeAgentConfig} with maps and a sample file. */
class NodeAgentConfigTest {
  @Test
  void loadsSampleFile() {
    Path path = Paths.get("src/test/resources/node-agent-test.yml");
    NodeAgentConfig config = NodeAgentConfig.loadFile(path);
    assertEquals("node-a", config.nodeId());
    assertEquals("/tmp/jahaziel-test/jail", config.jailRoot());
    assertEquals("/usr/local/bin/firecracker", config.firecrackerBinary());
    assertEquals("/tmp/jahaziel-test/vmlinux", config.kernelImage());
    assertEquals("/tmp/jahaziel-test/store", config.storageRoot());
    assertEquals("br-jahaziel", config.bridgeName());
    assertEquals("10.100.0.0/16", config.subnet());
    assertEquals("/tmp/jahaziel-test/cgroup", config.cgroupRoot());
  }

  @Test
  void missingSectionsAndKeysThrow() {
    assertThrows(NullPointerException.class, () -> NodeAgentConfig.load(null));
    assertThrows(IllegalArgumentException.class, () -> NodeAgentConfig.load(Map.of()));
  }
}
