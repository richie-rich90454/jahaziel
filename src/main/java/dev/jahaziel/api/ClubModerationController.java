/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.club.domain.ClubSession;
import dev.jahaziel.club.moderation.ModerationService;
import dev.jahaziel.identity.domain.UserId;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Club chat, staff moderation and the help queue. All staff actions are role-checked downstream. */
@RestController
@RequestMapping("/api/v1/club/sessions/{sessionId}")
public final class ClubModerationController extends ApiControllerBase {
    private final ModerationService moderation;
    private final ClubRegistry registry;

    public ClubModerationController(
            ModerationService moderation, ClubRegistry registry, AuthorizationService auth) {
        super(auth);
        this.moderation = moderation;
        this.registry = registry;
    }

    @PostMapping("/chat")
    public ClubDtos.ChatDto post(
            @PathVariable String sessionId,
            @RequestBody(required = false) ClubDtos.ChatRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        ClubDtos.ChatRequest request = body == null ? new ClubDtos.ChatRequest() : body;
        return view(moderation.post(session, user, request.getText()));
    }

    @GetMapping("/chat")
    public List<ClubDtos.ChatDto> history(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        return view(moderation.history(session, user));
    }

    @GetMapping("/chat/pinned")
    public List<ClubDtos.ChatDto> pinned(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        return view(moderation.pinned(session, user));
    }

    @PostMapping("/chat/{messageId}/pin")
    public ClubDtos.StatusDto pin(
            @PathVariable String sessionId, @PathVariable String messageId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        moderation.pin(session, user, messageId);
        return new ClubDtos.StatusDto("pinned");
    }

    @PostMapping("/moderation/mute")
    public ClubDtos.StatusDto mute(
            @PathVariable String sessionId,
            @RequestBody(required = false) ClubDtos.TargetRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        moderation.mute(session, user, parseUser(body));
        return new ClubDtos.StatusDto("muted");
    }

    @PostMapping("/moderation/unmute")
    public ClubDtos.StatusDto unmute(
            @PathVariable String sessionId,
            @RequestBody(required = false) ClubDtos.TargetRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        moderation.unmute(session, user, parseUser(body));
        return new ClubDtos.StatusDto("unmuted");
    }

    @PostMapping("/moderation/remove")
    public ClubDtos.StatusDto remove(
            @PathVariable String sessionId,
            @RequestBody(required = false) ClubDtos.TargetRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        moderation.remove(session, user, parseUser(body));
        return new ClubDtos.StatusDto("removed");
    }

    @PostMapping("/moderation/lock")
    public ClubDtos.StatusDto lock(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        moderation.lock(session, user);
        return new ClubDtos.StatusDto("locked");
    }

    @PostMapping("/moderation/unlock")
    public ClubDtos.StatusDto unlock(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        moderation.unlock(session, user);
        return new ClubDtos.StatusDto("unlocked");
    }

    @PostMapping("/help/join")
    public ClubDtos.StatusDto joinHelp(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        moderation.joinHelpQueue(session, user);
        return new ClubDtos.StatusDto("queued");
    }

    @PostMapping("/help/leave")
    public ClubDtos.StatusDto leaveHelp(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        moderation.leaveHelpQueue(session, user);
        return new ClubDtos.StatusDto("removed");
    }

    @GetMapping("/help/size")
    public DataDtos.CountDto helpSize(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        requireMember(session, user);
        return new DataDtos.CountDto(moderation.helpQueueSize(session));
    }

    @PostMapping("/help/next")
    public ClubDtos.StatusDto nextHelp(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        UserId next = moderation.nextHelp(session, user);
        return new ClubDtos.StatusDto(next.value().toString());
    }

    private static UserId parseUser(ClubDtos.TargetRequest body) {
        if (body == null || body.getTarget() == null || body.getTarget().isEmpty()) {
            throw ApiFailureException.badRequest("A target user id is required");
        }
        try {
            return new UserId(UUID.fromString(body.getTarget()));
        } catch (IllegalArgumentException invalid) {
            throw ApiFailureException.badRequest("A target user id is invalid");
        }
    }

    private static void requireMember(ClubSession session, UserId user) {
        if (session.roleOf(user) == null) {
            throw ApiFailureException.forbidden();
        }
    }

    private static List<ClubDtos.ChatDto> view(List<ModerationService.ChatMessage> messages) {
        List<ClubDtos.ChatDto> result = new ArrayList<ClubDtos.ChatDto>();
        for (int index = 0; index < messages.size(); index++) {
            ModerationService.ChatMessage message = messages.get(index);
            ClubDtos.ChatDto dto = new ClubDtos.ChatDto();
            dto.setId(message.id());
            dto.setAuthor(message.user().value().toString());
            dto.setText(message.text());
            dto.setPinned(message.isPinned());
            result.add(dto);
        }
        return result;
    }

    private static ClubDtos.ChatDto view(ModerationService.ChatMessage message) {
        ClubDtos.ChatDto result = new ClubDtos.ChatDto();
        result.setId(message.id());
        result.setAuthor(message.user().value().toString());
        result.setText(message.text());
        result.setPinned(message.isPinned());
        return result;
    }
}
