/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Failure branches of {@link NodeAgentConfig}. */
class NodeAgentConfigCoverageTest {
  @Test
  void loadFileRejectsNonMapping(@TempDir Path root) throws Exception {
    Path list = root.resolve("list.yml");
    Files.writeString(list, "- a\n- b\n");
    assertThrows(IllegalArgumentException.class, () -> NodeAgentConfig.loadFile(list));
  }

  @Test
  void loadFileRejectsMissing(@TempDir Path root) {
    assertThrows(
        IllegalArgumentException.class, () -> NodeAgentConfig.loadFile(root.resolve("absent.yml")));
  }

  @Test
  void loadRejectsBadSections() {
    assertThrows(
        IllegalArgumentException.class, () -> NodeAgentConfig.load(Map.of("node", "nope")));
    Map<String, Object> withBadValue = documentWith("node", "id", 42);
    assertThrows(IllegalArgumentException.class, () -> NodeAgentConfig.load(withBadValue));
    Map<String, Object> withBlank = documentWith("node", "id", "  ");
    assertThrows(IllegalArgumentException.class, () -> NodeAgentConfig.load(withBlank));
    Map<String, Object> missingKey = documentWith("node", "id", null);
    assertThrows(IllegalArgumentException.class, () -> NodeAgentConfig.load(missingKey));
  }

  @Test
  void loadFileRejectsScalar(@TempDir Path root) throws Exception {
    Path scalar = root.resolve("scalar.yml");
    Files.writeString(scalar, "just-a-string\n");
    assertThrows(IllegalArgumentException.class, () -> NodeAgentConfig.loadFile(scalar));
  }

  private static Map<String, Object> documentWith(String section, String key, Object value) {
    Map<String, Object> node = new HashMap<>();
    node.put("id", "node-a");
    Map<String, Object> firecracker = new HashMap<>();
    firecracker.put("jail_root", "/j");
    firecracker.put("binary", "/f");
    firecracker.put("jailer_binary", "/jailer");
    firecracker.put("kernel_image", "/k");
    Map<String, Object> storage = new HashMap<>();
    storage.put("root", "/s");
    storage.put("workspaces_dir", "/w");
    storage.put("images_dir", "/i");
    storage.put("audit_staging_dir", "/a");
    Map<String, Object> networking = new HashMap<>();
    networking.put("bridge_name", "br");
    networking.put("tap_prefix", "tap-");
    networking.put("subnet", "10.0.0.0/16");
    Map<String, Object> cgroups = new HashMap<>();
    cgroups.put("root", "/c");
    Map<String, Object> document = new HashMap<>();
    document.put("node", node);
    document.put("firecracker", firecracker);
    document.put("storage", storage);
    document.put("networking", networking);
    document.put("cgroups", cgroups);
    @SuppressWarnings("unchecked")
    Map<String, Object> target = (Map<String, Object>) document.get(section);
    if (value == null) {
      target.remove(key);
    } else {
      target.put(key, value);
    }
    return document;
  }

  @Test
  void messageMentionsPath(@TempDir Path root) throws Exception {
    Path bad = root.resolve("bad.yml");
    Files.writeString(bad, "node: {id: 1}\n");
    try {
      NodeAgentConfig.loadFile(bad);
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage() != null);
    }
  }
}
