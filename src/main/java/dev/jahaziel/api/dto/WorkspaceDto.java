/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.WorkspaceState;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Workspace view for reads.
 *
 * @param workspaceId the workspace identifier
 * @param name the workspace name
 * @param state the lowercase lifecycle state
 * @param stateReason why the workspace failed, null unless failed
 * @param createdAt when the workspace was created
 * @param lastActivityAt when the workspace entered its current state
 * @param bootPhase the live boot sub-step while starting, null otherwise
 * @param bootPhaseAt when the current boot sub-step started, null when bootPhase is null
 * @param quota the quota view
 * @param ports the registered ports, each with its tokenized public URL
 */
public final class WorkspaceDto {

    private final UUID workspaceId;

    private final String name;

    private final String state;

    private final String stateReason;

    private final Instant createdAt;

    private final Instant lastActivityAt;

    private final String bootPhase;

    private final Instant bootPhaseAt;

    private final WorkspaceQuotaDto quota;

    private final List<PortDto> ports;

    public WorkspaceDto(
            UUID workspaceId,
            String name,
            String state,
            String stateReason,
            Instant createdAt,
            Instant lastActivityAt,
            String bootPhase,
            Instant bootPhaseAt,
            WorkspaceQuotaDto quota,
            List<PortDto> ports) {
        this.workspaceId = workspaceId;
        this.name = name;
        this.state = state;
        this.stateReason = stateReason;
        this.createdAt = createdAt;
        this.lastActivityAt = lastActivityAt;
        this.bootPhase = bootPhase;
        this.bootPhaseAt = bootPhaseAt;
        this.quota = quota;
        this.ports = ports;
    }

    /**
     * Maps a view to a DTO.
     *
     * @param view the view
     * @param ports the workspace's ports
     * @param bootPhase the live boot sub-step while starting, null otherwise
     * @param bootPhaseAt when the current boot sub-step started, null when bootPhase is null
     * @return the DTO
     */
    public static WorkspaceDto from(
            WorkspaceView view, List<PortDto> ports, String bootPhase, Instant bootPhaseAt) {
        dev.jahaziel.workspace.domain.Workspace workspace = view.workspace();
        WorkspaceQuotaDto quotaDto =
                new WorkspaceQuotaDto(
                        0, workspace.quota().logicalBytes(), 0, workspace.quota().inodeLimit());
        String stateReason = null;
        if (workspace.state() instanceof WorkspaceState.Failed) {
            WorkspaceState.Failed failed = (WorkspaceState.Failed) workspace.state();
            stateReason = failed.reason();
        }
        return new WorkspaceDto(
                workspace.id().value(),
                workspace.name(),
                workspace.state().getClass().getSimpleName().toLowerCase(Locale.ROOT),
                stateReason,
                view.createdAt(),
                view.lastActivityAt(),
                bootPhase,
                bootPhaseAt,
                quotaDto,
                List.copyOf(ports));
    }

    public UUID workspaceId() {
        return this.workspaceId;
    }

    public UUID getWorkspaceId() {
        return this.workspaceId;
    }

    public String name() {
        return this.name;
    }

    public String getName() {
        return this.name;
    }

    public String state() {
        return this.state;
    }

    public String getState() {
        return this.state;
    }

    public String stateReason() {
        return this.stateReason;
    }

    public String getStateReason() {
        return this.stateReason;
    }

    public Instant createdAt() {
        return this.createdAt;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public Instant lastActivityAt() {
        return this.lastActivityAt;
    }

    public Instant getLastActivityAt() {
        return this.lastActivityAt;
    }

    public String bootPhase() {
        return this.bootPhase;
    }

    public String getBootPhase() {
        return this.bootPhase;
    }

    public Instant bootPhaseAt() {
        return this.bootPhaseAt;
    }

    public Instant getBootPhaseAt() {
        return this.bootPhaseAt;
    }

    public WorkspaceQuotaDto quota() {
        return this.quota;
    }

    public WorkspaceQuotaDto getQuota() {
        return this.quota;
    }

    public List<PortDto> ports() {
        return this.ports;
    }

    public List<PortDto> getPorts() {
        return this.ports;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkspaceDto)) return false;
        WorkspaceDto other = (WorkspaceDto) o;
        return (java.util.Objects.equals(this.workspaceId, other.workspaceId))
                && java.util.Objects.equals(this.name, other.name)
                && java.util.Objects.equals(this.state, other.state)
                && java.util.Objects.equals(this.stateReason, other.stateReason)
                && java.util.Objects.equals(this.createdAt, other.createdAt)
                && java.util.Objects.equals(this.lastActivityAt, other.lastActivityAt)
                && java.util.Objects.equals(this.bootPhase, other.bootPhase)
                && java.util.Objects.equals(this.bootPhaseAt, other.bootPhaseAt)
                && java.util.Objects.equals(this.quota, other.quota)
                && java.util.Objects.equals(this.ports, other.ports);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                this.workspaceId,
                this.name,
                this.state,
                this.stateReason,
                this.createdAt,
                this.lastActivityAt,
                this.bootPhase,
                this.bootPhaseAt,
                this.quota,
                this.ports);
    }

    @Override
    public String toString() {
        return "WorkspaceDto["
                + "workspaceId="
                + this.workspaceId
                + ", "
                + "name="
                + this.name
                + ", "
                + "state="
                + this.state
                + ", "
                + "stateReason="
                + this.stateReason
                + ", "
                + "createdAt="
                + this.createdAt
                + ", "
                + "lastActivityAt="
                + this.lastActivityAt
                + ", "
                + "bootPhase="
                + this.bootPhase
                + ", "
                + "bootPhaseAt="
                + this.bootPhaseAt
                + ", "
                + "quota="
                + this.quota
                + ", "
                + "ports="
                + this.ports
                + "]";
    }
}
