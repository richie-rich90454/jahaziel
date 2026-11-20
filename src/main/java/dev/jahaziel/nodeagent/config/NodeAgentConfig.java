/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

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
public final class NodeAgentConfig {
    private final String nodeId;
    private final String jailRoot;
    private final String firecrackerBinary;
    private final String jailerBinary;
    private final String kernelImage;
    private final String storageRoot;
    private final String workspacesDir;
    private final String imagesDir;
    private final String auditStagingDir;
    private final String bridgeName;
    private final String tapPrefix;
    private final String subnet;
    private final String cgroupRoot;

    /**
     * Creates a configuration.
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
    public NodeAgentConfig(
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
        this.nodeId = nodeId;
        this.jailRoot = jailRoot;
        this.firecrackerBinary = firecrackerBinary;
        this.jailerBinary = jailerBinary;
        this.kernelImage = kernelImage;
        this.storageRoot = storageRoot;
        this.workspacesDir = workspacesDir;
        this.imagesDir = imagesDir;
        this.auditStagingDir = auditStagingDir;
        this.bridgeName = bridgeName;
        this.tapPrefix = tapPrefix;
        this.subnet = subnet;
        this.cgroupRoot = cgroupRoot;
    }

    /** Returns the node identifier. */
    public String nodeId() {
        return nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }

    /** Returns the jail base directory. */
    public String jailRoot() {
        return jailRoot;
    }

    public String getJailRoot() {
        return jailRoot;
    }

    /** Returns the firecracker binary path. */
    public String firecrackerBinary() {
        return firecrackerBinary;
    }

    public String getFirecrackerBinary() {
        return firecrackerBinary;
    }

    /** Returns the jailer binary path. */
    public String jailerBinary() {
        return jailerBinary;
    }

    public String getJailerBinary() {
        return jailerBinary;
    }

    /** Returns the guest kernel image path. */
    public String kernelImage() {
        return kernelImage;
    }

    public String getKernelImage() {
        return kernelImage;
    }

    /** Returns the storage root. */
    public String storageRoot() {
        return storageRoot;
    }

    public String getStorageRoot() {
        return storageRoot;
    }

    /** Returns the workspaces directory. */
    public String workspacesDir() {
        return workspacesDir;
    }

    public String getWorkspacesDir() {
        return workspacesDir;
    }

    /** Returns the base images directory. */
    public String imagesDir() {
        return imagesDir;
    }

    public String getImagesDir() {
        return imagesDir;
    }

    /** Returns the audit spool directory. */
    public String auditStagingDir() {
        return auditStagingDir;
    }

    public String getAuditStagingDir() {
        return auditStagingDir;
    }

    /** Returns the host bridge name. */
    public String bridgeName() {
        return bridgeName;
    }

    public String getBridgeName() {
        return bridgeName;
    }

    /** Returns the TAP device prefix. */
    public String tapPrefix() {
        return tapPrefix;
    }

    public String getTapPrefix() {
        return tapPrefix;
    }

    /** Returns the private subnet in CIDR form. */
    public String subnet() {
        return subnet;
    }

    public String getSubnet() {
        return subnet;
    }

    /** Returns the jahaziel cgroup root. */
    public String cgroupRoot() {
        return cgroupRoot;
    }

    public String getCgroupRoot() {
        return cgroupRoot;
    }

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
        if (value instanceof String) {
            String text = (String) value;
            if (!text.isBlank()) {
                return text;
            }
        }
        throw new IllegalArgumentException("config misses key: " + key);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        NodeAgentConfig that = (NodeAgentConfig) other;
        return Objects.equals(nodeId, that.nodeId)
                && Objects.equals(jailRoot, that.jailRoot)
                && Objects.equals(firecrackerBinary, that.firecrackerBinary)
                && Objects.equals(jailerBinary, that.jailerBinary)
                && Objects.equals(kernelImage, that.kernelImage)
                && Objects.equals(storageRoot, that.storageRoot)
                && Objects.equals(workspacesDir, that.workspacesDir)
                && Objects.equals(imagesDir, that.imagesDir)
                && Objects.equals(auditStagingDir, that.auditStagingDir)
                && Objects.equals(bridgeName, that.bridgeName)
                && Objects.equals(tapPrefix, that.tapPrefix)
                && Objects.equals(subnet, that.subnet)
                && Objects.equals(cgroupRoot, that.cgroupRoot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                nodeId,
                jailRoot,
                firecrackerBinary,
                jailerBinary,
                kernelImage,
                storageRoot,
                workspacesDir,
                imagesDir,
                auditStagingDir,
                bridgeName,
                tapPrefix,
                subnet,
                cgroupRoot);
    }

    @Override
    public String toString() {
        return "NodeAgentConfig[nodeId="
                + nodeId
                + ", jailRoot="
                + jailRoot
                + ", firecrackerBinary="
                + firecrackerBinary
                + ", jailerBinary="
                + jailerBinary
                + ", kernelImage="
                + kernelImage
                + ", storageRoot="
                + storageRoot
                + ", workspacesDir="
                + workspacesDir
                + ", imagesDir="
                + imagesDir
                + ", auditStagingDir="
                + auditStagingDir
                + ", bridgeName="
                + bridgeName
                + ", tapPrefix="
                + tapPrefix
                + ", subnet="
                + subnet
                + ", cgroupRoot="
                + cgroupRoot
                + "]";
    }
}
