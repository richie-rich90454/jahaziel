/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.yaml.snakeyaml.Yaml;

/**
 * Node agent configuration. Loaded explicitly from YAML maps, no reflection binding, so missing
 * keys fail with clear messages.
 *
 * @param nodeId the node identifier
 * @param jailRoot the jail base directory
 * @param firecrackerBinary the firecracker binary path
 * @param jailerBinary the jailer binary path
 * @param kernelImage the guest kernel image path
 * @param storageRoot the storage root
 * @param workspacesDir the workspaces directory
 * @param imagesDir the base images directory
 * @param auditStagingDir the audit spool directory
 * @param bridgeName the host bridge name
 * @param tapPrefix the TAP device prefix
 * @param subnet the private subnet in CIDR form
 * @param cgroupRoot the jahaziel cgroup root
 */
public record NodeAgentConfig(
    String nodeId,
    String jailRoot,
    String firecrackerBinary,
    String jailerBinary,
    String kernelImage,
    String storageRoot,
    String workspacesDir,
    String imagesDir,
    String auditStagingDir,
    String bridgeName,
    String tapPrefix,
    String subnet,
    String cgroupRoot) {

  /** Loads configuration from a parsed YAML document. */
  public static NodeAgentConfig load(Map<String, Object> document) {
    Objects.requireNonNull(document, "document");
    Map<String, Object> node = section(document, "node");
    Map<String, Object> firecracker = section(document, "firecracker");
    Map<String, Object> storage = section(document, "storage");
    Map<String, Object> networking = section(document, "networking");
    Map<String, Object> cgroups = section(document, "cgroups");
    return new NodeAgentConfig(
        text(node, "id"),
        text(firecracker, "jail_root"),
        text(firecracker, "binary"),
        text(firecracker, "jailer_binary"),
        text(firecracker, "kernel_image"),
        text(storage, "root"),
        text(storage, "workspaces_dir"),
        text(storage, "images_dir"),
        text(storage, "audit_staging_dir"),
        text(networking, "bridge_name"),
        text(networking, "tap_prefix"),
        text(networking, "subnet"),
        text(cgroups, "root"));
  }

  /**
   * Loads configuration from a YAML file.
   *
   * @param path the config file path
   * @return the configuration
   */
  public static NodeAgentConfig loadFile(Path path) {
    try {
      Object loaded = new Yaml().load(Files.readString(path));
      if (!(loaded instanceof Map)) {
        throw new IllegalArgumentException("config must be a YAML mapping: " + path);
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> document = (Map<String, Object>) loaded;
      return load(document);
    } catch (IOException failure) {
      throw new IllegalArgumentException("cannot read config: " + path, failure);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> section(Map<String, Object> document, String name) {
    Object section = document.get(name);
    if (!(section instanceof Map)) {
      throw new IllegalArgumentException("config misses section: " + name);
    }
    return (Map<String, Object>) section;
  }

  private static String text(Map<String, Object> section, String key) {
    Object value = section.get(key);
    if (!(value instanceof String text) || text.isBlank()) {
      throw new IllegalArgumentException("config misses key: " + key);
    }
    return text;
  }
}
