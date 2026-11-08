/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.language.api.LanguagePackService;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LanguagePackControllerTest {
  @Test
  void marksTemplatePacksAndRejectsEnablingAnUnlistedPack() {
    UUID user = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    UserId actor = new UserId(user);
    WorkspaceId workspace = new WorkspaceId(workspaceId);
    Principal principal = () -> user.toString();
    AuthorizationService authorization = mock(AuthorizationService.class);
    WorkspaceTaskService tasks = mock(WorkspaceTaskService.class);
    LanguagePackService packs = mock(LanguagePackService.class);
    when(authorization.isAllowed(actor, "read", workspace)).thenReturn(true);
    when(authorization.isAllowed(actor, "write", workspace)).thenReturn(true);
    when(tasks.supportedLanguagePacks(workspace)).thenReturn(List.of("python"));
    when(packs.forWorkspace(workspace))
        .thenReturn(List.of(pack("python", false), pack("java", true)));
    LanguagePackController controller = new LanguagePackController(packs, authorization, tasks);

    List<LanguagePackService.Pack> result = controller.forWorkspace(workspaceId, principal);

    assertTrue(result.getFirst().supportedByTemplate());
    assertFalse(result.get(1).supportedByTemplate());
    assertThrows(
        IllegalArgumentException.class, () -> controller.select(workspaceId, "java", principal));
  }

  private static LanguagePackService.Pack pack(String id, boolean supported) {
    return new LanguagePackService.Pack(
        id,
        id,
        "1.0",
        1,
        "sha256",
        List.of("sha256"),
        List.of("x86_64"),
        false,
        false,
        "available",
        supported,
        "");
  }
}
