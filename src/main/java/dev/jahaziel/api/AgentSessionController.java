/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.agent.session.AgentSession;
import dev.jahaziel.agent.session.AgentSessionService;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Authenticated, workspace-scoped agent session endpoints. */
@RestController
@RequestMapping("/api/v1/agent/workspaces/{id}/sessions")
public final class AgentSessionController extends ApiControllerBase {
    private final AgentSessionService sessions;

    public AgentSessionController(AgentSessionService sessions, AuthorizationService auth) {
        super(auth);
        this.sessions = sessions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentDtos.SessionDto create(
            @PathVariable UUID id,
            @RequestBody(required = false) AgentDtos.AgentRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        String name = body == null ? null : body.getName();
        return view(sessions.create(user, workspace, name));
    }

    @GetMapping
    public List<AgentDtos.SessionDto> list(@PathVariable UUID id, Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, READ);
        List<AgentSession> found = sessions.list(user, workspace);
        List<AgentDtos.SessionDto> result = new ArrayList<AgentDtos.SessionDto>();
        for (int index = 0; index < found.size(); index++) {
            result.add(view(found.get(index)));
        }
        return result;
    }

    @GetMapping("/{sessionId}")
    public AgentDtos.SessionDto status(
            @PathVariable UUID id, @PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, READ);
        return view(sessions.status(user, workspace, sessionId));
    }

    @PostMapping("/{sessionId}/resume")
    public AgentDtos.SessionDto resume(
            @PathVariable UUID id, @PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        return view(sessions.resume(user, workspace, sessionId));
    }

    @PostMapping("/{sessionId}/restart")
    public AgentDtos.SessionDto restart(
            @PathVariable UUID id, @PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        return view(sessions.restart(user, workspace, sessionId));
    }

    @PostMapping("/{sessionId}/kill")
    public AgentDtos.SessionDto kill(
            @PathVariable UUID id, @PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        return view(sessions.kill(user, workspace, sessionId));
    }

    @GetMapping("/{sessionId}/transcript")
    public List<String> transcript(
            @PathVariable UUID id, @PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, READ);
        return sessions.transcript(user, workspace, sessionId);
    }

    @PostMapping("/{sessionId}/transcript")
    public AgentDtos.StatusDto append(
            @PathVariable UUID id,
            @PathVariable String sessionId,
            @RequestBody(required = false) AgentDtos.AgentRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        sessions.appendLine(user, workspace, sessionId, body == null ? null : body.getLine());
        return new AgentDtos.StatusDto("appended");
    }

    private static AgentDtos.SessionDto view(AgentSession session) {
        AgentDtos.SessionDto result = new AgentDtos.SessionDto();
        result.setId(session.getId());
        result.setName(session.getName());
        result.setStatus(session.getStatus());
        result.setWorkspaceId(session.getWorkspaceId());
        return result;
    }
}
