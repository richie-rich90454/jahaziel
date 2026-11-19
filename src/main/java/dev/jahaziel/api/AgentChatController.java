/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.agent.chat.AgentChatService;
import dev.jahaziel.agent.chat.ChatMessage;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Workspace-scoped chat history. No provider credential is accepted by these endpoints. */
@RestController
@RequestMapping("/api/v1/agent/workspaces/{id}/chat")
public final class AgentChatController extends ApiControllerBase {
    private final AgentChatService chat;

    public AgentChatController(AgentChatService chat, AuthorizationService auth) {
        super(auth);
        this.chat = chat;
    }

    @PostMapping
    public List<AgentDtos.MessageDto> send(
            @PathVariable UUID id,
            @RequestBody(required = false) AgentDtos.AgentRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        AgentDtos.AgentRequest request = body == null ? new AgentDtos.AgentRequest() : body;
        return view(
                chat.send(
                        user,
                        workspace,
                        request.getText(),
                        request.getFile(),
                        request.getSelection()));
    }

    @PostMapping("/agent")
    public List<AgentDtos.MessageDto> appendAgent(
            @PathVariable UUID id,
            @RequestBody(required = false) AgentDtos.AgentRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        String text = body == null ? null : body.getText();
        return view(chat.appendAgent(user, workspace, text));
    }

    @GetMapping
    public List<AgentDtos.MessageDto> history(@PathVariable UUID id, Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, READ);
        return view(chat.history(user, workspace));
    }

    @PostMapping("/cancel")
    public AgentDtos.StatusDto cancel(@PathVariable UUID id, Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        chat.cancel(user, workspace);
        return new AgentDtos.StatusDto("cancelled");
    }

    @GetMapping("/cancelled")
    public AgentDtos.StatusDto cancelled(@PathVariable UUID id, Principal principal) {
        UserId user = requireUser(principal);
        WorkspaceId workspace = requireWorkspace(principal, id, READ);
        return new AgentDtos.StatusDto(Boolean.toString(chat.isCancelled(user, workspace)));
    }

    private static List<AgentDtos.MessageDto> view(List<ChatMessage> messages) {
        List<AgentDtos.MessageDto> result = new ArrayList<AgentDtos.MessageDto>();
        for (int index = 0; index < messages.size(); index++) {
            ChatMessage message = messages.get(index);
            AgentDtos.MessageDto dto = new AgentDtos.MessageDto();
            dto.setId(message.getId());
            dto.setRole(message.getRole());
            dto.setText(message.getText());
            dto.setActiveFile(message.getActiveFile());
            dto.setCreatedAt(message.getCreatedAt());
            result.add(dto);
        }
        return result;
    }
}
