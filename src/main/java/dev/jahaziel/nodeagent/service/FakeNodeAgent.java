/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.SnapshotHandle;
import dev.jahaziel.spi.SnapshotTag;
import dev.jahaziel.spi.StorageUsage;
import dev.jahaziel.spi.WorkspaceHandle;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/** In-memory node agent for development and tests, with no host calls and instant transitions. */
public final class FakeNodeAgent extends NodeBackend {
    private final ConcurrentMap<UUID, WorkspaceHandle> prepared = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, List<SnapshotSummary>> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> ports = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public WorkspaceHandle prepare(PrepareRequest request) {
        Objects.requireNonNull(request, "request");
        return prepareWorkspace(
                request.workspaceId(),
                request.ownerId(),
                request.image().value(),
                request.memoryMib(),
                request.vcpuCount(),
                request.quota());
    }

    public WorkspaceHandle prepare(
            UUID workspaceId, UUID ownerId, ImageRef image, QuotaBytes quota) {
        return prepareWorkspace(workspaceId, ownerId, image.value(), 0, 0, quota);
    }

    @Override
    public RuntimeHandle start(RuntimeRequest request) {
        Objects.requireNonNull(request, "request");
        requirePrepared(request.workspaceId());
        return new RuntimeHandle(request.workspaceId());
    }

    @Override
    public void stop(RuntimeHandle handle, boolean graceful) {
        Objects.requireNonNull(handle, "handle");
        requirePrepared(handle.value());
    }

    @Override
    public void destroy(WorkspaceHandle handle) {
        destroy(handle, false);
    }

    @Override
    public void destroy(WorkspaceHandle handle, boolean keepSnapshots) {
        requirePrepared(handle);
        prepared.remove(handle.workspaceId());
        if (!keepSnapshots) {
            snapshots.remove(handle.workspaceId());
        }
    }

    public SnapshotHandle snapshot(WorkspaceHandle handle, SnapshotTag tag) {
        return createSnapshot(handle, tag, "fake");
    }

    public void deleteSnapshot(SnapshotHandle snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        for (List<SnapshotSummary> list : snapshots.values()) {
            Iterator<SnapshotSummary> iterator = list.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().snapshotId().equals(snapshot.snapshotId())) {
                    iterator.remove();
                }
            }
        }
    }

    public void restore(WorkspaceHandle handle, SnapshotHandle snapshot) {
        restoreSnapshot(handle, snapshot);
    }

    public StorageUsage usage(WorkspaceHandle handle) {
        requirePrepared(handle);
        return new StorageUsage(0, 0, 0, 0);
    }

    public WorkspaceHandle prepareWorkspace(
            UUID workspaceId,
            UUID ownerId,
            String imageRef,
            long memoryMib,
            int vcpuCount,
            QuotaBytes quota) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        WorkspaceHandle handle =
                new WorkspaceHandle(
                        workspaceId,
                        Path.of("/workspaces/" + workspaceId + "/home"),
                        Path.of("/jail/" + workspaceId));
        prepared.put(workspaceId, handle);
        return handle;
    }

    public void startRuntime(WorkspaceHandle handle, RuntimeRequest config) {
        requirePrepared(handle);
    }

    public void stopRuntime(WorkspaceHandle handle, boolean graceful, int timeoutSeconds) {
        requirePrepared(handle);
    }

    public void destroyWorkspace(WorkspaceHandle handle, boolean keepSnapshots) {
        requirePrepared(handle);
        prepared.remove(handle.workspaceId());
        if (!keepSnapshots) {
            snapshots.remove(handle.workspaceId());
        }
    }

    public SnapshotHandle createSnapshot(
            WorkspaceHandle handle, SnapshotTag tag, String createdBy) {
        requirePrepared(handle);
        SnapshotHandle snapshot =
                new SnapshotHandle(
                        UUID.randomUUID(),
                        Path.of("/workspaces/" + handle.workspaceId() + "/snaps/" + tag.value()),
                        Instant.now());
        snapshots
                .computeIfAbsent(
                        handle.workspaceId(),
                        new Function<UUID, List<SnapshotSummary>>() {
                            @Override
                            public List<SnapshotSummary> apply(UUID key) {
                                return new ArrayList<>();
                            }
                        })
                .add(new SnapshotSummary(snapshot, tag.value(), snapshot.createdAt()));
        return snapshot;
    }

    public void restoreSnapshot(WorkspaceHandle handle, SnapshotHandle snapshot) {
        requirePrepared(handle);
    }

    public List<SnapshotSummary> listSnapshots(WorkspaceHandle handle) {
        requirePrepared(handle);
        return List.copyOf(snapshots.getOrDefault(handle.workspaceId(), List.of()));
    }

    public String attachPort(WorkspaceHandle handle, int port, String protocol) {
        requirePrepared(handle);
        String vsock = "vsock:" + handle.workspaceId() + ":" + port;
        ports.put(handle.workspaceId() + ":" + port, vsock);
        return vsock;
    }

    public void detachPort(WorkspaceHandle handle, int port) {
        requirePrepared(handle);
        ports.remove(handle.workspaceId() + ":" + port);
    }

    /** Returns whether a workspace is prepared. */
    public boolean isPrepared(UUID workspaceId) {
        return prepared.containsKey(workspaceId);
    }

    private void requirePrepared(WorkspaceHandle handle) {
        Objects.requireNonNull(handle, "handle");
        requirePrepared(handle.workspaceId());
    }

    private void requirePrepared(UUID workspaceId) {
        if (!prepared.containsKey(workspaceId)) {
            throw new IllegalArgumentException("workspace not prepared: " + workspaceId);
        }
    }
}
