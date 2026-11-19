/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.support;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.snapshot.domain.SnapshotId;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;
import dev.jahaziel.spi.StorageUsage;
import dev.jahaziel.workspace.api.WorkspaceNotFoundException;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Hand-written fakes for club tests. No mocking framework and no lambdas. */
public final class Fakes {
    private Fakes() {}

    /** In-memory workspace service that records forked workspaces and template initialization. */
    public static final class Workspace implements WorkspaceService {
        private final java.util.Map<WorkspaceId, dev.jahaziel.workspace.domain.Workspace> rows =
                new java.util.HashMap<WorkspaceId, dev.jahaziel.workspace.domain.Workspace>();
        public final List<String> initializedTemplates = new ArrayList<String>();
        public final List<WorkspaceId> initializedIds = new ArrayList<WorkspaceId>();

        @Override
        public dev.jahaziel.workspace.domain.Workspace create(
                UserId owner, String name, int vcpuCount, long memoryMib, NodeId nodeId) {
            WorkspaceId id = new WorkspaceId(UUID.randomUUID());
            VirtualMachine vm =
                    new VirtualMachine(
                            new VirtualMachineId(UUID.randomUUID()),
                            id,
                            new VirtualMachineConfiguration(vcpuCount, memoryMib));
            dev.jahaziel.workspace.domain.Workspace workspace =
                    new dev.jahaziel.workspace.domain.Workspace(
                            id, owner, name, WorkspaceQuota.defaults(), vm);
            rows.put(id, workspace);
            return workspace;
        }

        @Override
        public dev.jahaziel.workspace.domain.Workspace get(WorkspaceId id) {
            dev.jahaziel.workspace.domain.Workspace workspace = rows.get(id);
            if (workspace == null) {
                throw new WorkspaceNotFoundException(id);
            }
            return workspace;
        }

        @Override
        public WorkspaceView describe(WorkspaceId id) {
            Instant now = Instant.now();
            return new WorkspaceView(get(id), now, now, new StorageUsage(0, 0, 0, 0));
        }

        @Override
        public List<WorkspaceView> describeByOwner(UserId owner) {
            List<WorkspaceView> views = new ArrayList<WorkspaceView>();
            for (dev.jahaziel.workspace.domain.Workspace workspace : rows.values()) {
                if (workspace.owner().equals(owner)) {
                    views.add(describe(workspace.id()));
                }
            }
            return views;
        }

        @Override
        public List<WorkspaceView> describeAll() {
            List<WorkspaceView> views = new ArrayList<WorkspaceView>();
            for (dev.jahaziel.workspace.domain.Workspace workspace : rows.values()) {
                views.add(describe(workspace.id()));
            }
            return views;
        }

        @Override
        public void save(dev.jahaziel.workspace.domain.Workspace workspace) {
            rows.put(workspace.id(), workspace);
        }

        @Override
        public void delete(WorkspaceId id) {
            rows.remove(id);
        }

        /** Records a template association exactly like the template controller does. */
        public void initialize(WorkspaceId id, String template) {
            initializedIds.add(id);
            initializedTemplates.add(template);
        }
    }

    /** In-memory task service with a configurable uniform outcome. */
    public static final class Tasks implements WorkspaceTaskService {
        private String status = "SUCCEEDED";
        private int exitCode = 0;
        private String output = "ok";
        public final List<String> startedTasks = new ArrayList<String>();
        public final List<String> initializedTemplates = new ArrayList<String>();
        public final List<WorkspaceId> initializedIds = new ArrayList<WorkspaceId>();
        private final java.util.Map<UUID, WorkspaceTaskService.TaskRun> runs =
                new java.util.HashMap<UUID, WorkspaceTaskService.TaskRun>();

        /** Sets the outcome returned by every started task. */
        public void outcome(String status, int exitCode, String output) {
            this.status = status;
            this.exitCode = exitCode;
            this.output = output;
        }

        @Override
        public void initialize(WorkspaceId workspaceId, String templateName) {
            initializedIds.add(workspaceId);
            initializedTemplates.add(templateName);
        }

        @Override
        public SetupStatus setupStatus(WorkspaceId workspaceId) {
            return new SetupStatus("READY", "", "", Instant.now(), UUID.randomUUID());
        }

        @Override
        public SetupStatus retrySetup(WorkspaceId workspaceId) {
            return setupStatus(workspaceId);
        }

        @Override
        public List<Task> tasks(WorkspaceId workspaceId) {
            return new ArrayList<Task>();
        }

        @Override
        public List<DebugTarget> debugTargets(WorkspaceId workspaceId) {
            return new ArrayList<DebugTarget>();
        }

        @Override
        public DebugTarget debugTarget(WorkspaceId workspaceId, UUID targetId) {
            return null;
        }

        @Override
        public TaskRun startTask(WorkspaceId workspaceId, String taskId) {
            startedTasks.add(taskId);
            Instant now = Instant.now();
            TaskRun run =
                    new TaskRun(
                            UUID.randomUUID(),
                            workspaceId.value(),
                            taskId,
                            taskId,
                            status,
                            output,
                            Integer.valueOf(exitCode),
                            now,
                            now);
            runs.put(run.id(), run);
            return run;
        }

        @Override
        public TaskRun taskRun(UUID runId) {
            return runs.get(runId);
        }

        @Override
        public TaskRun latestTaskRun(WorkspaceId workspaceId) {
            return null;
        }

        @Override
        public TaskRun stopTask(WorkspaceId workspaceId, UUID runId) {
            return runs.get(runId);
        }

        @Override
        public void closeByWorkspace(WorkspaceId workspaceId) {}

        @Override
        public void allowByWorkspace(WorkspaceId workspaceId) {}

        @Override
        public void closeAll() {}
    }

    /** In-memory snapshot service that records created tags. */
    public static final class Snapshots implements SnapshotService {
        public final List<String> tags = new ArrayList<String>();

        @Override
        public SnapshotMetadata create(
                WorkspaceId workspaceId, String tag, SnapshotKind kind, String createdBy) {
            tags.add(tag);
            return new SnapshotMetadata(
                    new SnapshotId(UUID.randomUUID()),
                    workspaceId,
                    tag,
                    kind,
                    Instant.now(),
                    createdBy,
                    Paths.get("data", "club", "snapshots", tag),
                    0,
                    0,
                    Optional.<SnapshotId>empty(),
                    "pending");
        }

        @Override
        public List<SnapshotMetadata> list(WorkspaceId workspaceId) {
            return new ArrayList<SnapshotMetadata>();
        }

        @Override
        public void restore(WorkspaceId workspaceId, SnapshotId snapshotId) {}

        @Override
        public void delete(WorkspaceId workspaceId, SnapshotId snapshotId) {}
    }

    /** In-memory authorization service with an explicit admin set. */
    public static final class Authorization implements AuthorizationService {
        private final Set<UserId> admins = new HashSet<UserId>();

        /** Grants the administrator role. */
        public void addAdmin(UserId user) {
            admins.add(user);
        }

        @Override
        public boolean isAllowed(
                UserId user,
                String permission,
                dev.jahaziel.workspace.domain.WorkspaceId workspace) {
            return true;
        }

        @Override
        public boolean isAdmin(UserId user) {
            return admins.contains(user);
        }
    }
}
