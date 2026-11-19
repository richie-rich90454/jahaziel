/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.agent.prompt.PromptLibrary;
import dev.jahaziel.agent.prompt.PromptTemplate;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/** Per-user snippets and administrator-managed global templates. */
@RestController
public final class AgentPromptController extends ApiControllerBase {
    private final PromptLibrary prompts;

    public AgentPromptController(PromptLibrary prompts, AuthorizationService auth) {
        super(auth);
        this.prompts = prompts;
    }

    @GetMapping("/api/v1/agent/prompts")
    public List<AgentDtos.PromptDto> list(Principal principal) {
        UserId user = requireUser(principal);
        List<PromptTemplate> found = prompts.list(user);
        List<AgentDtos.PromptDto> result = new ArrayList<AgentDtos.PromptDto>();
        for (int index = 0; index < found.size(); index++) {
            result.add(view(found.get(index)));
        }
        return result;
    }

    @PostMapping("/api/v1/agent/prompts")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentDtos.PromptDto create(
            @RequestBody(required = false) AgentDtos.AgentRequest body, Principal principal) {
        UserId user = requireUser(principal);
        AgentDtos.AgentRequest request = body == null ? new AgentDtos.AgentRequest() : body;
        return view(prompts.createUser(user, request.getName(), request.getText()));
    }

    @GetMapping("/api/v1/agent/prompts/{id}")
    public AgentDtos.PromptDto get(@PathVariable String id, Principal principal) {
        UserId user = requireUser(principal);
        return view(prompts.getUser(user, id));
    }

    @PutMapping("/api/v1/agent/prompts/{id}")
    public AgentDtos.PromptDto update(
            @PathVariable String id,
            @RequestBody(required = false) AgentDtos.AgentRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        AgentDtos.AgentRequest request = body == null ? new AgentDtos.AgentRequest() : body;
        return view(prompts.updateUser(user, id, request.getName(), request.getText()));
    }

    @DeleteMapping("/api/v1/agent/prompts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, Principal principal) {
        UserId user = requireUser(principal);
        prompts.deleteUser(user, id);
    }

    @PostMapping("/api/v1/agent/prompts/{id}/render")
    public AgentDtos.RenderedDto render(
            @PathVariable String id,
            @RequestBody(required = false) AgentDtos.AgentRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        AgentDtos.AgentRequest request = body == null ? new AgentDtos.AgentRequest() : body;
        AgentDtos.RenderedDto result = new AgentDtos.RenderedDto();
        result.setRendered(prompts.render(user, id, request.getFile(), request.getSelection()));
        return result;
    }

    @PostMapping("/api/v1/agent/admin/prompts")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentDtos.PromptDto createGlobal(
            @RequestBody(required = false) AgentDtos.AgentRequest body, Principal principal) {
        requireAdmin(principal);
        AgentDtos.AgentRequest request = body == null ? new AgentDtos.AgentRequest() : body;
        return view(prompts.createGlobal(request.getName(), request.getText()));
    }

    @GetMapping("/api/v1/agent/admin/prompts/{id}")
    public AgentDtos.PromptDto getGlobal(@PathVariable String id, Principal principal) {
        requireAdmin(principal);
        return view(prompts.getGlobal(id));
    }

    @PutMapping("/api/v1/agent/admin/prompts/{id}")
    public AgentDtos.PromptDto updateGlobal(
            @PathVariable String id,
            @RequestBody(required = false) AgentDtos.AgentRequest body,
            Principal principal) {
        requireAdmin(principal);
        AgentDtos.AgentRequest request = body == null ? new AgentDtos.AgentRequest() : body;
        return view(prompts.updateGlobal(id, request.getName(), request.getText()));
    }

    @DeleteMapping("/api/v1/agent/admin/prompts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGlobal(@PathVariable String id, Principal principal) {
        requireAdmin(principal);
        prompts.deleteGlobal(id);
    }

    private static AgentDtos.PromptDto view(PromptTemplate template) {
        AgentDtos.PromptDto result = new AgentDtos.PromptDto();
        result.setId(template.getId());
        result.setName(template.getName());
        result.setBody(template.getBody());
        result.setOwner(template.getOwner());
        return result;
    }
}
