/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.agent.model.ModelCatalog;
import dev.jahaziel.agent.usage.UsageMeters;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Model allowlist and usage counters. Model ids are configuration-only, never browser-supplied. */
@RestController
public final class AgentMetaController extends ApiControllerBase {
    private final ModelCatalog models;
    private final UsageMeters usage;

    public AgentMetaController(
            ModelCatalog models, UsageMeters usage, AuthorizationService auth) {
        super(auth);
        this.models = models;
        this.usage = usage;
    }

    @GetMapping("/api/v1/agent/models")
    public List<AgentDtos.ModelDto> catalog(Principal principal) {
        requireUser(principal);
        List<ModelCatalog.ModelInfo> found = models.catalog();
        List<AgentDtos.ModelDto> result = new ArrayList<AgentDtos.ModelDto>();
        for (int index = 0; index < found.size(); index++) {
            AgentDtos.ModelDto dto = new AgentDtos.ModelDto();
            dto.setId(found.get(index).getId());
            dto.setCapability(found.get(index).getCapability());
            result.add(dto);
        }
        return result;
    }

    @GetMapping("/api/v1/agent/usage/me")
    public AgentDtos.UsageDto mine(Principal principal) {
        UserId user = requireUser(principal);
        return view(usage.getByUser(user));
    }

    @GetMapping("/api/v1/agent/workspaces/{id}/usage")
    public AgentDtos.UsageDto scoped(@PathVariable UUID id, Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, READ);
        return view(usage.get(user, workspace));
    }

    @PostMapping("/api/v1/agent/workspaces/{id}/usage")
    public AgentDtos.UsageDto record(
            @PathVariable UUID id,
            @RequestBody(required = false) AgentDtos.AgentRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        AgentDtos.AgentRequest request = body == null ? new AgentDtos.AgentRequest() : body;
        return view(usage.record(user, workspace, request.getTokensIn(), request.getTokensOut()));
    }

    private static AgentDtos.UsageDto view(UsageMeters.UsageSnapshot snapshot) {
        AgentDtos.UsageDto result = new AgentDtos.UsageDto();
        result.setUserId(snapshot.getUserId());
        result.setWorkspaceId(snapshot.getWorkspaceId());
        result.setTokensIn(snapshot.getTokensIn());
        result.setTokensOut(snapshot.getTokensOut());
        result.setMessages(snapshot.getMessages());
        return result;
    }
}
