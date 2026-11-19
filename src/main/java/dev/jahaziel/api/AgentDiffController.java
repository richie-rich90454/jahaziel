/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.agent.diff.DiffReviewService;
import dev.jahaziel.agent.diff.UnifiedPatch;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staging and per-hunk review of agent patches. Patches are never applied by these endpoints. */
@RestController
@RequestMapping("/api/v1/agent/workspaces/{id}/diffs")
public final class AgentDiffController extends ApiControllerBase {
    private final DiffReviewService diffs;

    public AgentDiffController(DiffReviewService diffs, AuthorizationService auth) {
        super(auth);
        this.diffs = diffs;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentDtos.PatchDto stage(
            @PathVariable UUID id,
            @RequestBody(required = false) AgentDtos.AgentRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        String patchText = body == null ? null : body.getPatch();
        return view(diffs.stage(user, workspace, patchText));
    }

    @GetMapping
    public List<AgentDtos.PatchDto> list(@PathVariable UUID id, Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, READ);
        List<UnifiedPatch> found = diffs.list(user, workspace);
        List<AgentDtos.PatchDto> result = new ArrayList<AgentDtos.PatchDto>();
        for (int index = 0; index < found.size(); index++) {
            result.add(view(found.get(index)));
        }
        return result;
    }

    @GetMapping("/{patchId}")
    public AgentDtos.PatchDto get(
            @PathVariable UUID id, @PathVariable String patchId, Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, READ);
        return view(diffs.get(user, workspace, patchId));
    }

    @PostMapping("/{patchId}/hunks/{hunkIndex}/accept")
    public AgentDtos.PatchDto accept(
            @PathVariable UUID id,
            @PathVariable String patchId,
            @PathVariable int hunkIndex,
            Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        return view(diffs.accept(user, workspace, patchId, hunkIndex));
    }

    @PostMapping("/{patchId}/hunks/{hunkIndex}/reject")
    public AgentDtos.PatchDto reject(
            @PathVariable UUID id,
            @PathVariable String patchId,
            @PathVariable int hunkIndex,
            Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        return view(diffs.reject(user, workspace, patchId, hunkIndex));
    }

    private static AgentDtos.PatchDto view(UnifiedPatch patch) {
        AgentDtos.PatchDto result = new AgentDtos.PatchDto();
        result.setId(patch.getId());
        result.setFilePath(patch.getFilePath());
        result.setStatus(status(patch));
        return result;
    }

    private static String status(UnifiedPatch patch) {
        List<String> decisions = patch.getDecisions();
        boolean allAccepted = true;
        boolean anyRejected = false;
        for (int index = 0; index < decisions.size(); index++) {
            String decision = decisions.get(index);
            if (UnifiedPatch.REJECTED.equals(decision)) {
                anyRejected = true;
            }
            if (!UnifiedPatch.ACCEPTED.equals(decision)) {
                allAccepted = false;
            }
        }
        if (anyRejected) {
            return "PARTIAL";
        }
        if (allAccepted && !decisions.isEmpty()) {
            return UnifiedPatch.ACCEPTED;
        }
        return UnifiedPatch.PENDING;
    }
}
