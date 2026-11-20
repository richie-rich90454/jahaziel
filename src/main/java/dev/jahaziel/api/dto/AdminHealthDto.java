/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

/**
 * Control plane health for reads.
 *
 * @param status the status, ok when serving
 * @param nodes the registered node count
 * @param workspaces the workspace count
 */
public final class AdminHealthDto {

    private final String status;

    private final int nodes;

    private final int workspaces;

    public AdminHealthDto(String status, int nodes, int workspaces) {
        this.status = status;
        this.nodes = nodes;
        this.workspaces = workspaces;
    }

    public String status() {
        return this.status;
    }

    public String getStatus() {
        return this.status;
    }

    public int nodes() {
        return this.nodes;
    }

    public int getNodes() {
        return this.nodes;
    }

    public int workspaces() {
        return this.workspaces;
    }

    public int getWorkspaces() {
        return this.workspaces;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdminHealthDto)) return false;
        AdminHealthDto other = (AdminHealthDto) o;
        return (java.util.Objects.equals(this.status, other.status))
                && this.nodes == other.nodes
                && this.workspaces == other.workspaces;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.status, this.nodes, this.workspaces);
    }

    @Override
    public String toString() {
        return "AdminHealthDto["
                + "status="
                + this.status
                + ", "
                + "nodes="
                + this.nodes
                + ", "
                + "workspaces="
                + this.workspaces
                + "]";
    }
}
