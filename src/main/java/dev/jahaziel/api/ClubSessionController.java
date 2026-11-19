/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.club.domain.ClubSession;
import dev.jahaziel.club.session.ClubSessionService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.security.Principal;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Club session creation, joining, isolated forks and shared demo control. */
@RestController
@RequestMapping("/api/v1/club/sessions")
public final class ClubSessionController extends ApiControllerBase {
    private final ClubSessionService sessions;
    private final ClubRegistry registry;

    public ClubSessionController(
            ClubSessionService sessions, ClubRegistry registry, AuthorizationService auth) {
        super(auth);
        this.sessions = sessions;
        this.registry = registry;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClubDtos.SessionDto create(
            @RequestBody(required = false) ClubDtos.CreateRequest body, Principal principal) {
        UserId host = requireUser(principal);
        ClubDtos.CreateRequest request = body == null ? new ClubDtos.CreateRequest() : body;
        Instant startsAt = parseInstant(request.getStartsAt());
        Instant endsAt = parseInstant(request.getEndsAt());
        return view(registry.createSession(
            host, request.getTemplate(), startsAt, endsAt, request.getCapacity()));
    }

    @GetMapping("/{sessionId}")
    public ClubDtos.SessionDto get(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        requireMember(session, user);
        return view(session);
    }

    @PostMapping("/{sessionId}/join")
    public ClubDtos.SessionDto join(
            @PathVariable String sessionId,
            @RequestBody(required = false) ClubDtos.JoinRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        ClubDtos.JoinRequest request = body == null ? new ClubDtos.JoinRequest() : body;
        if (request.getCode() == null || !request.getCode().equals(session.joinCode())) {
            throw ApiFailureException.forbidden();
        }
        session.join(user, request.getRole());
        return view(session);
    }

    @PostMapping("/{sessionId}/fork")
    @ResponseStatus(HttpStatus.CREATED)
    public ClubDtos.WorkspaceRefDto fork(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        WorkspaceId workspace = sessions.forkFor(session, user).id();
        requireWorkspaceFor(user, workspace, WRITE);
        ClubDtos.WorkspaceRefDto result = new ClubDtos.WorkspaceRefDto();
        result.setWorkspaceId(workspace.value().toString());
        return result;
    }

    @PostMapping("/{sessionId}/demo")
    @ResponseStatus(HttpStatus.CREATED)
    public ClubDtos.WorkspaceRefDto createDemo(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        requireHost(session, user);
        WorkspaceId workspace = sessions.createDemo(session).id();
        ClubDtos.WorkspaceRefDto result = new ClubDtos.WorkspaceRefDto();
        result.setWorkspaceId(workspace.value().toString());
        return result;
    }

    @GetMapping("/{sessionId}/demo")
    public ClubDtos.WorkspaceRefDto followDemo(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        WorkspaceId workspace = sessions.followDemo(session, user);
        ClubDtos.WorkspaceRefDto result = new ClubDtos.WorkspaceRefDto();
        result.setWorkspaceId(workspace.value().toString());
        return result;
    }

    @PostMapping("/{sessionId}/demo/control")
    public ClubDtos.StatusDto controlDemo(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        sessions.controlDemo(session, user);
        return new ClubDtos.StatusDto("controlling");
    }

    @PostMapping("/{sessionId}/demo/transfer")
    public ClubDtos.StatusDto transferDemo(
            @PathVariable String sessionId,
            @RequestBody(required = false) ClubDtos.TransferRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        ClubDtos.TransferRequest request = body == null ? new ClubDtos.TransferRequest() : body;
        sessions.transferDemoControl(session, user, parseUser(request.getNext()));
        return new ClubDtos.StatusDto("transferred");
    }

    @PostMapping("/{sessionId}/leaderboard")
    public ClubDtos.StatusDto leaderboard(
            @PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        requireHost(session, user);
        session.setLeaderboardEnabled(true);
        return new ClubDtos.StatusDto("enabled");
    }

    @PostMapping("/{sessionId}/join-code/rotate")
    public ClubDtos.SessionDto rotateCode(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        requireHost(session, user);
        session.rotateJoinCode();
        return view(session);
    }

    private UserId parseUser(String value) {
        if (value == null || value.isEmpty()) {
            throw ApiFailureException.badRequest("A user id is required");
        }
        try {
            return new UserId(java.util.UUID.fromString(value));
        } catch (IllegalArgumentException invalid) {
            throw ApiFailureException.badRequest("A user id is invalid");
        }
    }

    private static void requireMember(ClubSession session, UserId user) {
        if (session.roleOf(user) == null) {
            throw ApiFailureException.forbidden();
        }
    }

    private static void requireHost(ClubSession session, UserId user) {
        if (!session.host().equals(user)) {
            throw ApiFailureException.forbidden();
        }
    }

    private static ClubDtos.SessionDto view(ClubSession session) {
        ClubDtos.SessionDto result = new ClubDtos.SessionDto();
        result.setId(session.id());
        result.setJoinCode(session.joinCode());
        result.setTemplate(session.template());
        result.setCapacity(session.capacity());
        result.setParticipants(session.size());
        result.setLeaderboard(session.isLeaderboardEnabled());
        result.setLocked(session.isLocked());
        return result;
    }
}
