/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.language.api.LanguagePackService;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authorized catalog, workspace selection, and administrator cache operations. */
@RestController
public final class LanguagePackController {
  private final LanguagePackService packs;
  private final AuthorizationService authorization;
  private final WorkspaceTaskService tasks;

  public LanguagePackController(
      LanguagePackService packs, AuthorizationService authorization, WorkspaceTaskService tasks) {
    this.packs = packs;
    this.authorization = authorization;
    this.tasks = tasks;
  }

  @GetMapping("/api/v1/language-packs")
  public List<LanguagePackService.Pack> catalog(Principal principal) {
    userId(principal);
    return packs.catalog();
  }

  @GetMapping("/api/v1/workspaces/{id}/language-packs")
  public List<LanguagePackService.Pack> forWorkspace(@PathVariable UUID id, Principal principal) {
    WorkspaceId workspace = require(principal, id, "read");
    List<String> supported = tasks.supportedLanguagePacks(workspace);
    return packs.forWorkspace(workspace).stream()
        .map(pack -> pack.withTemplateSupport(supported.isEmpty() || supported.contains(pack.id())))
        .toList();
  }

  @PutMapping("/api/v1/workspaces/{id}/language-packs/{languageId}")
  public LanguagePackService.Pack select(
      @PathVariable UUID id, @PathVariable String languageId, Principal principal) {
    WorkspaceId workspace = require(principal, id, "write");
    List<String> supported = tasks.supportedLanguagePacks(workspace);
    if (!supported.isEmpty() && !supported.contains(languageId)) {
      throw new IllegalArgumentException(
          "language pack is not supported by this workspace template");
    }
    return packs.select(workspace, languageId);
  }

  @DeleteMapping("/api/v1/workspaces/{id}/language-packs/{languageId}")
  public void deselect(
      @PathVariable UUID id, @PathVariable String languageId, Principal principal) {
    WorkspaceId workspace = require(principal, id, "write");
    packs.deselect(workspace, languageId);
  }

  @GetMapping("/api/v1/admin/language-packs/cache")
  public LanguagePackService.CacheReport cache(Principal principal) {
    requireAdmin(principal);
    return packs.report();
  }

  @PostMapping("/api/v1/admin/language-packs/cache/prune")
  public LanguagePackService.PruneReport prune(
      @RequestParam(defaultValue = "true") boolean dryRun, Principal principal) {
    requireAdmin(principal);
    return packs.prune(dryRun);
  }

  private WorkspaceId require(Principal principal, UUID id, String permission) {
    UserId actor = userId(principal);
    WorkspaceId workspace = new WorkspaceId(id);
    if (!authorization.isAllowed(actor, permission, workspace)) {
      throw new AccessDeniedException(actor, permission);
    }
    return workspace;
  }

  private void requireAdmin(Principal principal) {
    UserId actor = userId(principal);
    if (!authorization.isAdmin(actor)) {
      throw new AccessDeniedException(actor, "admin");
    }
  }

  private static UserId userId(Principal principal) {
    if (principal == null) {
      throw new IllegalArgumentException("authentication is required");
    }
    try {
      return new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
  }
}
