/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.jail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link Jailer} command assembly. Never starts processes in tests. */
class JailerTest {
  @Test
  void jailCommandMatchesInvocation() {
    UUID workspace = UUID.randomUUID();
    Jailer jailer = new Jailer("/usr/local/bin/jailer", "/usr/local/bin/firecracker", "/jail");
    List<String> command = jailer.jailCommand(workspace, 100001, 100001, "/var/run/netns/x");
    assertEquals("/usr/local/bin/jailer", command.get(0));
    assertTrue(command.contains("--id"));
    assertTrue(command.contains(workspace.toString()));
    assertTrue(command.contains("--uid"));
    assertTrue(command.contains("100001"));
    assertTrue(command.contains("--netns"));
    assertTrue(command.contains("/var/run/netns/x"));
    assertTrue(command.contains("--cgroup-version"));
    assertTrue(command.contains("--resource-limits"));
    assertTrue(command.contains("--"));
  }
}
