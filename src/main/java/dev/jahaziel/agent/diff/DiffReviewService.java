/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.diff;

import dev.jahaziel.agent.AgentAudit;
import dev.jahaziel.agent.AgentException;
import dev.jahaziel.agent.AgentStore;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Stages agent-produced patches and records per-hunk decisions. This service never writes into a
 * workspace: applying a patch is always a separate, explicit operation.
 */
public final class DiffReviewService {
    private final AgentStore store;
    private final StrictPatchParser parser;
    private final AgentAudit audit;

    public DiffReviewService(AgentStore store, StrictPatchParser parser, AgentAudit audit) {
        this.store = Objects.requireNonNull(store, "store");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    /**
     * Parses and stages a patch. Paths that escape the workspace are rejected before any write.
     *
     * @param user the requesting user
     * @param workspace the target workspace
     * @param patchText the unified diff text
     * @return the staged patch with every hunk pending
     */
    public UnifiedPatch stage(UserId user, WorkspaceId workspace, String patchText) {
        requireOwner(user, workspace);
        UnifiedPatch patch = parser.parse(patchText, workspace.value());
        patch.setId(UUID.randomUUID().toString());
        patch.setUserId(user.value().toString());
        patch.setWorkspaceId(workspace.value().toString());
        store.write(jsonPath(user, workspace, patch.getId()), patch);
        store.write(rawPath(user, workspace, patch.getId()), patch.getRaw());
        return patch;
    }

    /** Lists staged patches for one user and workspace. */
    public List<UnifiedPatch> list(UserId user, WorkspaceId workspace) {
        requireOwner(user, workspace);
        List<UnifiedPatch> result = new ArrayList<UnifiedPatch>();
        for (Path file : store.listFiles(scopePath(user, workspace))) {
            if (file.getFileName().toString().endsWith(".json")) {
                UnifiedPatch patch = store.read(file, UnifiedPatch.class);
                if (patch != null) {
                    result.add(patch);
                }
            }
        }
        return result;
    }

    /** Returns one staged patch. */
    public UnifiedPatch get(UserId user, WorkspaceId workspace, String patchId) {
        requireOwner(user, workspace);
        UnifiedPatch patch = store.read(jsonPath(user, workspace, patchId), UnifiedPatch.class);
        if (patch == null) {
            throw new AgentException("unknown staged patch");
        }
        return patch;
    }

    /** Accepts one hunk, recording the decision in the audit log. */
    public UnifiedPatch accept(UserId user, WorkspaceId workspace, String patchId, int hunkIndex) {
        return decide(user, workspace, patchId, hunkIndex, UnifiedPatch.ACCEPTED);
    }

    /** Rejects one hunk, recording the decision in the audit log. */
    public UnifiedPatch reject(UserId user, WorkspaceId workspace, String patchId, int hunkIndex) {
        return decide(user, workspace, patchId, hunkIndex, UnifiedPatch.REJECTED);
    }

    private UnifiedPatch decide(
            UserId user,
            WorkspaceId workspace,
            String patchId,
            int hunkIndex,
            String decision) {
        requireOwner(user, workspace);
        UnifiedPatch patch = get(user, workspace, patchId);
        if (hunkIndex < 0 || hunkIndex >= patch.getHunks().size()) {
            throw new AgentException("hunk index is out of range");
        }
        patch.getDecisions().set(hunkIndex, decision);
        store.write(jsonPath(user, workspace, patchId), patch);
        String action =
            UnifiedPatch.ACCEPTED.equals(decision) ? "PATCH_HUNK_ACCEPTED" : "PATCH_HUNK_REJECTED";
        audit.record(
            user,
            action,
            patchId + "#" + hunkIndex,
            "PATCH_HUNK",
            "filePath",
            patch.getFilePath());
        return patch;
    }

    private Path scopePath(UserId user, WorkspaceId workspace) {
        return store.containedChild(
            store.patchRoot(), user.value().toString(), workspace.value().toString());
    }

    private Path jsonPath(UserId user, WorkspaceId workspace, String patchId) {
        if (patchId == null || !AgentStore.isSafeSegment(patchId)) {
            throw new AgentException("invalid patch id");
        }
        return store.containedChild(scopePath(user, workspace), patchId + ".json");
    }

    private Path rawPath(UserId user, WorkspaceId workspace, String patchId) {
        return store.containedChild(scopePath(user, workspace), patchId + ".patch");
    }

    private static void requireOwner(UserId user, WorkspaceId workspace) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(workspace, "workspace");
    }
}
