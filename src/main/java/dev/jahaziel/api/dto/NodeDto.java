/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import dev.jahaziel.node.domain.Node;

import java.util.Locale;

/**
 * A node for reads.
 *
 * @param nodeId the node identifier
 * @param name the human-readable name
 * @param region the hosting region
 * @param state the lowercase health state
 * @param cpuCores CPU core count
 * @param memoryMib memory in MiB
 * @param diskGib disk in GiB
 */
public final class NodeDto {

    private final String nodeId;

    private final String name;

    private final String region;

    private final String state;

    private final int cpuCores;

    private final long memoryMib;

    private final long diskGib;

    public NodeDto(
            String nodeId,
            String name,
            String region,
            String state,
            int cpuCores,
            long memoryMib,
            long diskGib) {
        this.nodeId = nodeId;
        this.name = name;
        this.region = region;
        this.state = state;
        this.cpuCores = cpuCores;
        this.memoryMib = memoryMib;
        this.diskGib = diskGib;
    }

    /**
     * Maps a node to a DTO.
     *
     * @param node the node
     * @return the DTO
     */
    public static NodeDto from(Node node) {
        return new NodeDto(
                node.id().value(),
                node.name(),
                node.region(),
                node.state().getClass().getSimpleName().toLowerCase(Locale.ROOT),
                node.capacity().cpuCores(),
                node.capacity().memoryMib(),
                node.capacity().diskGib());
    }

    public String nodeId() {
        return this.nodeId;
    }

    public String getNodeId() {
        return this.nodeId;
    }

    public String name() {
        return this.name;
    }

    public String getName() {
        return this.name;
    }

    public String region() {
        return this.region;
    }

    public String getRegion() {
        return this.region;
    }

    public String state() {
        return this.state;
    }

    public String getState() {
        return this.state;
    }

    public int cpuCores() {
        return this.cpuCores;
    }

    public int getCpuCores() {
        return this.cpuCores;
    }

    public long memoryMib() {
        return this.memoryMib;
    }

    public long getMemoryMib() {
        return this.memoryMib;
    }

    public long diskGib() {
        return this.diskGib;
    }

    public long getDiskGib() {
        return this.diskGib;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeDto)) return false;
        NodeDto other = (NodeDto) o;
        return (java.util.Objects.equals(this.nodeId, other.nodeId))
                && java.util.Objects.equals(this.name, other.name)
                && java.util.Objects.equals(this.region, other.region)
                && java.util.Objects.equals(this.state, other.state)
                && this.cpuCores == other.cpuCores
                && this.memoryMib == other.memoryMib
                && this.diskGib == other.diskGib;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                this.nodeId,
                this.name,
                this.region,
                this.state,
                this.cpuCores,
                this.memoryMib,
                this.diskGib);
    }

    @Override
    public String toString() {
        return "NodeDto["
                + "nodeId="
                + this.nodeId
                + ", "
                + "name="
                + this.name
                + ", "
                + "region="
                + this.region
                + ", "
                + "state="
                + this.state
                + ", "
                + "cpuCores="
                + this.cpuCores
                + ", "
                + "memoryMib="
                + this.memoryMib
                + ", "
                + "diskGib="
                + this.diskGib
                + "]";
    }
}
