/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.workspace.domain.WorkspaceId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Server-managed template setup and run tasks for one workspace. */
public abstract class WorkspaceTaskService {
    /** Associates the server-owned template with a newly created workspace. */
    public abstract void initialize(WorkspaceId workspaceId, String templateName);

    /** Returns current setup progress and bounded output. */
    public abstract SetupStatus setupStatus(WorkspaceId workspaceId);

    /** Starts the template's setup steps again after a failure. */
    public abstract SetupStatus retrySetup(WorkspaceId workspaceId);

    /** Returns the safe task labels that may be run; no command data is exposed. */
    public abstract List<Task> tasks(WorkspaceId workspaceId);

    /** Returns active targets created by server-declared debuggable tasks. */
    public abstract List<DebugTarget> debugTargets(WorkspaceId workspaceId);

    /** Resolves one target only while it remains active in the specified workspace. */
    public abstract DebugTarget debugTarget(WorkspaceId workspaceId, UUID targetId);

    /**
     * Returns packs declared by this workspace's server-managed template. An empty list means the
     * template places no restriction on the server catalog.
     */
    public List<String> supportedLanguagePacks(WorkspaceId workspaceId) {
        return List.of();
    }

    /** Starts a declared task by its server-owned id. */
    public abstract TaskRun startTask(WorkspaceId workspaceId, String taskId);

    /** Reads one run, including its bounded output. */
    public abstract TaskRun taskRun(UUID runId);

    /** Returns the most recent run for a workspace so the workbench can recover its status. */
    public abstract TaskRun latestTaskRun(WorkspaceId workspaceId);

    /** Stops a running declared task belonging to the specified workspace. */
    public abstract TaskRun stopTask(WorkspaceId workspaceId, UUID runId);

    /** Stops template setup and run processes when the workspace is stopped or destroyed. */
    public abstract void closeByWorkspace(WorkspaceId workspaceId);

    /** Allows setup retries after the workspace has started again. */
    public abstract void allowByWorkspace(WorkspaceId workspaceId);

    /** Stops every setup and run process during server shutdown. */
    public abstract void closeAll();

    public static final class SetupStatus {
        private final String status;
        private final String output;
        private final String error;
        private final Instant updatedAt;
        private final UUID correlationId;

        public SetupStatus(
                String status, String output, String error, Instant updatedAt, UUID correlationId) {
            this.status = status;
            this.output = output;
            this.error = error;
            this.updatedAt = updatedAt;
            this.correlationId = correlationId;
        }

        public String status() {
            return status;
        }

        public String getStatus() {
            return status;
        }

        public String output() {
            return output;
        }

        public String getOutput() {
            return output;
        }

        public String error() {
            return error;
        }

        public String getError() {
            return error;
        }

        public Instant updatedAt() {
            return updatedAt;
        }

        public Instant getUpdatedAt() {
            return updatedAt;
        }

        public UUID correlationId() {
            return correlationId;
        }

        public UUID getCorrelationId() {
            return correlationId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SetupStatus other = (SetupStatus) o;
            return Objects.equals(status, other.status)
                    && Objects.equals(output, other.output)
                    && Objects.equals(error, other.error)
                    && Objects.equals(updatedAt, other.updatedAt)
                    && Objects.equals(correlationId, other.correlationId);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(status);
            result = 31 * result + Objects.hashCode(output);
            result = 31 * result + Objects.hashCode(error);
            result = 31 * result + Objects.hashCode(updatedAt);
            result = 31 * result + Objects.hashCode(correlationId);
            return result;
        }

        @Override
        public String toString() {
            return "SetupStatus{"
                    + "status="
                    + status
                    + ", output="
                    + output
                    + ", error="
                    + error
                    + ", updatedAt="
                    + updatedAt
                    + ", correlationId="
                    + correlationId
                    + '}';
        }
    }

    public static final class Task {
        private final String id;
        private final String label;
        private final Integer port;

        public Task(String id, String label, Integer port) {
            this.id = id;
            this.label = label;
            this.port = port;
        }

        public String id() {
            return id;
        }

        public String getId() {
            return id;
        }

        public String label() {
            return label;
        }

        public String getLabel() {
            return label;
        }

        public Integer port() {
            return port;
        }

        public Integer getPort() {
            return port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Task other = (Task) o;
            return Objects.equals(id, other.id)
                    && Objects.equals(label, other.label)
                    && Objects.equals(port, other.port);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(id);
            result = 31 * result + Objects.hashCode(label);
            result = 31 * result + Objects.hashCode(port);
            return result;
        }

        @Override
        public String toString() {
            return "Task{" + "id=" + id + ", label=" + label + ", port=" + port + '}';
        }
    }

    /** Internal attach details for a live server-owned task process. */
    public static final class DebugTarget {
        private final UUID id;
        private final UUID workspaceId;
        private final UUID taskRunId;
        private final String languageId;
        private final String label;
        private final long processId;
        private final Integer debugPort;
        private final String program;

        public DebugTarget(
                UUID id,
                UUID workspaceId,
                UUID taskRunId,
                String languageId,
                String label,
                long processId,
                Integer debugPort,
                String program) {
            this.id = id;
            this.workspaceId = workspaceId;
            this.taskRunId = taskRunId;
            this.languageId = languageId;
            this.label = label;
            this.processId = processId;
            this.debugPort = debugPort;
            this.program = program;
        }

        public UUID id() {
            return id;
        }

        public UUID getId() {
            return id;
        }

        public UUID workspaceId() {
            return workspaceId;
        }

        public UUID getWorkspaceId() {
            return workspaceId;
        }

        public UUID taskRunId() {
            return taskRunId;
        }

        public UUID getTaskRunId() {
            return taskRunId;
        }

        public String languageId() {
            return languageId;
        }

        public String getLanguageId() {
            return languageId;
        }

        public String label() {
            return label;
        }

        public String getLabel() {
            return label;
        }

        public long processId() {
            return processId;
        }

        public long getProcessId() {
            return processId;
        }

        public Integer debugPort() {
            return debugPort;
        }

        public Integer getDebugPort() {
            return debugPort;
        }

        public String program() {
            return program;
        }

        public String getProgram() {
            return program;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DebugTarget other = (DebugTarget) o;
            return Objects.equals(id, other.id)
                    && Objects.equals(workspaceId, other.workspaceId)
                    && Objects.equals(taskRunId, other.taskRunId)
                    && Objects.equals(languageId, other.languageId)
                    && Objects.equals(label, other.label)
                    && processId == other.processId
                    && Objects.equals(debugPort, other.debugPort)
                    && Objects.equals(program, other.program);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(id);
            result = 31 * result + Objects.hashCode(workspaceId);
            result = 31 * result + Objects.hashCode(taskRunId);
            result = 31 * result + Objects.hashCode(languageId);
            result = 31 * result + Objects.hashCode(label);
            result = 31 * result + (int) (processId ^ (processId >>> 32));
            result = 31 * result + Objects.hashCode(debugPort);
            result = 31 * result + Objects.hashCode(program);
            return result;
        }

        @Override
        public String toString() {
            return "DebugTarget{"
                    + "id="
                    + id
                    + ", workspaceId="
                    + workspaceId
                    + ", taskRunId="
                    + taskRunId
                    + ", languageId="
                    + languageId
                    + ", label="
                    + label
                    + ", processId="
                    + processId
                    + ", debugPort="
                    + debugPort
                    + ", program="
                    + program
                    + '}';
        }
    }

    public static final class TaskRun {
        private final UUID id;
        private final UUID workspaceId;
        private final String taskId;
        private final String label;
        private final String status;
        private final String output;
        private final Integer exitCode;
        private final Instant startedAt;
        private final Instant finishedAt;

        public TaskRun(
                UUID id,
                UUID workspaceId,
                String taskId,
                String label,
                String status,
                String output,
                Integer exitCode,
                Instant startedAt,
                Instant finishedAt) {
            this.id = id;
            this.workspaceId = workspaceId;
            this.taskId = taskId;
            this.label = label;
            this.status = status;
            this.output = output;
            this.exitCode = exitCode;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
        }

        public UUID id() {
            return id;
        }

        public UUID getId() {
            return id;
        }

        public UUID workspaceId() {
            return workspaceId;
        }

        public UUID getWorkspaceId() {
            return workspaceId;
        }

        public String taskId() {
            return taskId;
        }

        public String getTaskId() {
            return taskId;
        }

        public String label() {
            return label;
        }

        public String getLabel() {
            return label;
        }

        public String status() {
            return status;
        }

        public String getStatus() {
            return status;
        }

        public String output() {
            return output;
        }

        public String getOutput() {
            return output;
        }

        public Integer exitCode() {
            return exitCode;
        }

        public Integer getExitCode() {
            return exitCode;
        }

        public Instant startedAt() {
            return startedAt;
        }

        public Instant getStartedAt() {
            return startedAt;
        }

        public Instant finishedAt() {
            return finishedAt;
        }

        public Instant getFinishedAt() {
            return finishedAt;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TaskRun other = (TaskRun) o;
            return Objects.equals(id, other.id)
                    && Objects.equals(workspaceId, other.workspaceId)
                    && Objects.equals(taskId, other.taskId)
                    && Objects.equals(label, other.label)
                    && Objects.equals(status, other.status)
                    && Objects.equals(output, other.output)
                    && Objects.equals(exitCode, other.exitCode)
                    && Objects.equals(startedAt, other.startedAt)
                    && Objects.equals(finishedAt, other.finishedAt);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(id);
            result = 31 * result + Objects.hashCode(workspaceId);
            result = 31 * result + Objects.hashCode(taskId);
            result = 31 * result + Objects.hashCode(label);
            result = 31 * result + Objects.hashCode(status);
            result = 31 * result + Objects.hashCode(output);
            result = 31 * result + Objects.hashCode(exitCode);
            result = 31 * result + Objects.hashCode(startedAt);
            result = 31 * result + Objects.hashCode(finishedAt);
            return result;
        }

        @Override
        public String toString() {
            return "TaskRun{"
                    + "id="
                    + id
                    + ", workspaceId="
                    + workspaceId
                    + ", taskId="
                    + taskId
                    + ", label="
                    + label
                    + ", status="
                    + status
                    + ", output="
                    + output
                    + ", exitCode="
                    + exitCode
                    + ", startedAt="
                    + startedAt
                    + ", finishedAt="
                    + finishedAt
                    + '}';
        }
    }
}
