/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.administration.api.AdminService;
import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.dist.DemoSeeder;
import dev.jahaziel.ide.internal.IdeService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.api.NodeService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;

class AdminUiControllerTest {
  @TempDir private Path tmp;

  private Principal principal(UUID id) {
    return () -> id.toString();
  }

  private AdminUiController controller(
      boolean demo, Path logFile, AuthorizationService auth, UserService users) {
    var admin = mock(AdminService.class);
    var nodes = mock(NodeService.class);
    var ide = mock(IdeService.class);
    when(ide.variant()).thenReturn(IdeService.Variant.WIN32_X64);
    when(nodes.list()).thenReturn(List.of());
    when(admin.listWorkspaces()).thenReturn(List.of());
    return new AdminUiController(admin, nodes, auth, users, ide, logFile.toString(), "local", demo);
  }

  private AdminUiController openController(boolean demo, Path logFile) {
    UserId adminId = new UserId(UUID.randomUUID());
    var auth = mock(AuthorizationService.class);
    var users = mock(UserService.class);
    when(auth.isAdmin(any())).thenReturn(true);
    when(users.isMfaEnabled(any())).thenReturn(true);
    AdminUiController controller = controller(demo, logFile, auth, users);
    return controller;
  }

  @Test
  void overviewServesDashboard() {
    AdminUiController controller = openController(false, tmp.resolve("control-plane.log"));
    var response = controller.overview(principal(UUID.randomUUID()));
    assertEquals(200, response.getStatusCode().value());
    assertEquals(MediaType.TEXT_HTML, response.getHeaders().getContentType());
    assertTrue(response.getBody().contains("Jahaziel admin"));
  }

  @Test
  void diagnosticsDownloadsBundle() {
    AdminUiController controller = openController(false, tmp.resolve("control-plane.log"));
    var response = controller.diagnostics(principal(UUID.randomUUID()));
    assertEquals(200, response.getStatusCode().value());
    assertTrue(response.getBody().contains("status=ok"));
    assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("diagnostics.txt"));
  }

  @Test
  void logsStreamsTailAsEvents() throws Exception {
    Path log = tmp.resolve("control-plane.log");
    Files.writeString(log, "first\nsecond\n", StandardCharsets.UTF_8);
    AdminUiController controller = openController(false, log);
    var response = controller.logs(principal(UUID.randomUUID()));
    assertEquals(200, response.getStatusCode().value());
    assertEquals(MediaType.TEXT_EVENT_STREAM, response.getHeaders().getContentType());
    assertTrue(response.getBody().contains("data:first"));
    assertTrue(response.getBody().contains("data:second"));
  }

  @Test
  void logsFallsBackWhenMissing() {
    AdminUiController controller = openController(false, tmp.resolve("missing.log"));
    var response = controller.logs(principal(UUID.randomUUID()));
    assertEquals(200, response.getStatusCode().value());
    assertTrue(response.getBody().contains("data:no log file"));
  }

  @Test
  void logsFallsBackWhenUnreadable() throws Exception {
    Path dir = tmp.resolve("dir-log");
    Files.createDirectories(dir);
    AdminUiController controller = openController(false, dir);
    var response = controller.logs(principal(UUID.randomUUID()));
    assertEquals(200, response.getStatusCode().value());
    assertTrue(response.getBody().contains("data:cannot read log"));
  }

  @Test
  void overviewRejectsBadCallers() {
    AdminUiController controller = openController(false, tmp.resolve("control-plane.log"));
    assertThrows(IllegalArgumentException.class, () -> controller.overview(null));
    assertThrows(IllegalArgumentException.class, () -> controller.overview(() -> "nope"));
    UserId plain = new UserId(UUID.randomUUID());
    var auth = mock(AuthorizationService.class);
    var users = mock(UserService.class);
    when(auth.isAdmin(plain)).thenReturn(false);
    AdminUiController guarded = controller(false, tmp.resolve("control-plane.log"), auth, users);
    assertThrows(AccessDeniedException.class, () -> guarded.overview(principal(plain.value())));
    when(auth.isAdmin(plain)).thenReturn(true);
    when(users.isMfaEnabled(plain)).thenReturn(false);
    assertThrows(AccessDeniedException.class, () -> guarded.overview(principal(plain.value())));
    assertThrows(IllegalArgumentException.class, () -> guarded.diagnostics(null));
    assertThrows(IllegalArgumentException.class, () -> guarded.logs(() -> "bad"));
  }

  @Test
  void demoNullPrincipalFallsBackToDemoAdmin() {
    UserId adminId = DemoSeeder.DEMO_USER;
    var auth = mock(AuthorizationService.class);
    var users = mock(UserService.class);
    when(auth.isAdmin(adminId)).thenReturn(true);
    when(users.isMfaEnabled(adminId)).thenReturn(true);
    AdminUiController controller = controller(true, tmp.resolve("control-plane.log"), auth, users);
    assertEquals(200, controller.overview(null).getStatusCode().value());
    assertEquals(200, controller.diagnostics(null).getStatusCode().value());
    assertTrue(controller.logs(null).getBody().contains("data:"));
  }
}
