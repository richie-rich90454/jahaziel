/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.api.controller.AdminController;
import dev.jahaziel.api.controller.ApiExceptionHandler;
import dev.jahaziel.api.controller.DevConnectController;
import dev.jahaziel.api.controller.PortController;
import dev.jahaziel.api.controller.RuntimeController;
import dev.jahaziel.api.controller.SnapshotController;
import dev.jahaziel.api.controller.TerminalController;
import dev.jahaziel.api.controller.WorkspaceController;
import dev.jahaziel.api.dto.AdminHealthDto;
import dev.jahaziel.api.dto.ApiError;
import dev.jahaziel.api.dto.AuditRecordDto;
import dev.jahaziel.api.dto.CreatePolicyRequest;
import dev.jahaziel.api.dto.CreateSnapshotRequest;
import dev.jahaziel.api.dto.CreateWorkspaceRequest;
import dev.jahaziel.api.dto.MetricsDto;
import dev.jahaziel.api.dto.NodeDto;
import dev.jahaziel.api.dto.PortDto;
import dev.jahaziel.api.dto.RegisterPortRequest;
import dev.jahaziel.api.dto.SnapshotDto;
import dev.jahaziel.api.dto.TerminalSessionDto;
import dev.jahaziel.api.dto.UserDto;
import dev.jahaziel.api.dto.WorkspaceDto;
import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.networking.api.GatewayEndpoint;
import dev.jahaziel.networking.api.PortBinding;
import dev.jahaziel.runtime.internal.BootProgressService;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ApiExtraCoverageTest {
  private Principal principal(UUID id) {
    return () -> id.toString();
  }

  @Test
  void portDtoAndMetricsDto() {
    PortBinding b =
        new PortBinding(UUID.randomUUID(), 8080, UUID.randomUUID(), "http", Instant.now());
    assertTrue(
        PortDto.from(b, "slug", new GatewayEndpoint("https", "code.test", 443), null).url()
            == null);
    assertTrue(
        PortDto.from(b, "slug", new GatewayEndpoint("https", "code.test", 443), "tok")
            .url()
            .contains("tok"));
    assertEquals(0, MetricsDto.zero().cpuUsagePercent());
  }

  @Test
  void workspaceDtoFactoryBothPaths() {
    var ports = mock(dev.jahaziel.networking.api.PortService.class);
    var users = mock(UserService.class);
    var tokens = mock(JwtService.class);
    var progress = new BootProgressService();
    WorkspaceDtoFactory f =
        new WorkspaceDtoFactory(
            ports, users, tokens, new GatewayEndpoint("https", "code.test", 443), progress, 900);
    UserId owner = new UserId(UUID.randomUUID());
    var ws = mock(dev.jahaziel.workspace.domain.Workspace.class);
    WorkspaceDtoFactoryTestHelper.setup(ws, owner);
    var view = mock(dev.jahaziel.workspace.api.WorkspaceView.class);
    when(view.workspace()).thenReturn(ws);
    when(users.slug(owner)).thenReturn("slug");
    PortBinding b = new PortBinding(owner.value(), 8080, ws.id().value(), "http", Instant.now());
    when(ports.bindingsByWorkspace(ws.id())).thenReturn(List.of(b));
    when(tokens.issue(any(), any(), any(), any())).thenReturn("tok");
    WorkspaceDto dto = f.forUser(owner, view);
    assertEquals(1, dto.ports().size());
    WorkspaceDto admin = f.withoutTokens(view);
    assertTrue(admin.ports().get(0).url() == null);
    assertTrue(dto.bootPhase() == null);
    assertTrue(dto.bootPhaseAt() == null);
    progress.report(ws.id(), dev.jahaziel.ide.internal.IdeServerService.BootPhase.SPAWNING);
    WorkspaceDto live = f.forUser(owner, view);
    assertEquals("spawning", live.bootPhase());
    assertTrue(live.bootPhaseAt() != null);
    WorkspaceDto liveAdmin = f.withoutTokens(view);
    assertEquals("spawning", liveAdmin.bootPhase());
    assertThrows(
        NullPointerException.class,
        () ->
            new WorkspaceDtoFactory(
                null,
                users,
                tokens,
                new GatewayEndpoint("http", "d", 8081),
                new BootProgressService(),
                1));
  }

  @Test
  void workspaceDtoCarriesFailureReason() {
    var ports = mock(dev.jahaziel.networking.api.PortService.class);
    var users = mock(UserService.class);
    var tokens = mock(JwtService.class);
    WorkspaceDtoFactory f =
        new WorkspaceDtoFactory(
            ports,
            users,
            tokens,
            new GatewayEndpoint("https", "code.test", 443),
            new BootProgressService(),
            900);
    UserId owner = new UserId(UUID.randomUUID());
    var ws = mock(dev.jahaziel.workspace.domain.Workspace.class);
    WorkspaceDtoFactoryTestHelper.setup(ws, owner);
    when(ws.state())
        .thenReturn(
            new dev.jahaziel.workspace.domain.WorkspaceState.Failed(Instant.now(), "disk gone"));
    var view = mock(dev.jahaziel.workspace.api.WorkspaceView.class);
    when(view.workspace()).thenReturn(ws);
    when(ports.bindingsByWorkspace(ws.id())).thenReturn(List.of());
    WorkspaceDto failed = f.forUser(owner, view);
    assertEquals("failed", failed.state());
    assertEquals("disk gone", failed.stateReason());
    when(ws.state())
        .thenReturn(new dev.jahaziel.workspace.domain.WorkspaceState.Ready(Instant.now()));
    WorkspaceDto ready = f.withoutTokens(view);
    assertEquals("ready", ready.state());
    assertTrue(ready.stateReason() == null);
  }

  @Test
  void exceptionHandlerCoversAll() {
    ApiExceptionHandler h = new ApiExceptionHandler();
    UserId u = new UserId(UUID.randomUUID());
    assertEquals(
        "NOT_FOUND",
        h.workspaceNotFound(
                new dev.jahaziel.workspace.api.WorkspaceNotFoundException(
                    new dev.jahaziel.workspace.domain.WorkspaceId(UUID.randomUUID())))
            .code());
    assertEquals(
        "NOT_FOUND", h.userNotFound(new dev.jahaziel.identity.api.UserNotFoundException(u)).code());
    assertEquals(
        "NOT_FOUND",
        h.resourceNotFound(
                new dev.jahaziel.snapshot.api.SnapshotNotFoundException(
                    new dev.jahaziel.snapshot.domain.SnapshotId(UUID.randomUUID())))
            .code());
    ApiError denied = h.accessDenied(new AccessDeniedException(u, "write"));
    assertEquals("FORBIDDEN", denied.code());
    assertTrue(denied.details().containsKey("user"));
    assertEquals(
        "LOGIN_REJECTED",
        h.loginRejected(new dev.jahaziel.identity.api.LoginRejectedException("no")).code());
    assertEquals(
        "DEVCONNECT_FAILED",
        h.devConnectFailed(new dev.jahaziel.identity.api.DevConnectException("x")).code());
    assertEquals(
        "NODE_AGENT_UNREACHABLE",
        h.nodeAgentUnreachable(new dev.jahaziel.runtime.api.NodeAgentUnreachableException("x"))
            .code());
    assertEquals(
        "STATE_CONFLICT",
        h.illegalState(
                new dev.jahaziel.workspace.domain.IllegalWorkspaceStateException(
                    new dev.jahaziel.workspace.domain.WorkspaceId(UUID.randomUUID()),
                    new dev.jahaziel.workspace.domain.WorkspaceState.Requested(Instant.now()),
                    "x"))
            .code());
    assertEquals("VALIDATION_FAILED", h.badInput(new IllegalArgumentException("bad")).code());
    assertEquals(
        "NOT_FOUND",
        h.noResource(new NoResourceFoundException(HttpMethod.GET, "/missing", "gone"), null)
            .code());
    jakarta.servlet.http.HttpServletRequest request =
        mock(jakarta.servlet.http.HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(request.getHeader("Host")).thenReturn("localhost:11451");
    when(request.getHeader("User-Agent")).thenReturn("test-agent");
    assertEquals(
        "gone",
        h.noResource(new NoResourceFoundException(HttpMethod.GET, "/missing", "gone"), request)
            .details()
            .get("path"));
    when(request.getQueryString()).thenReturn("a=1");
    assertEquals(
        "gone",
        h.noResource(new NoResourceFoundException(HttpMethod.GET, "/missing", "gone"), request)
            .details()
            .get("path"));
    assertEquals(
        "VALIDATION_FAILED",
        h.invalidBody(mock(org.springframework.web.bind.MethodArgumentNotValidException.class))
            .code());
    assertEquals("INTERNAL_ERROR", h.unexpected(new RuntimeException("boom")).code());
  }

  @Test
  void devConnectControllerBranches() {
    DevConnectService dev = mock(DevConnectService.class);
    UserService users = mock(UserService.class);
    DevConnectController c = new DevConnectController(dev, users);
    when(dev.startLogin()).thenReturn("https://idp/authorize");
    assertEquals(302, c.login().getStatusCode().value());
    assertEquals(
        "/?login=failed",
        c.callback("s", "code", "access_denied").getHeaders().getLocation().toString());
    DevConnectService.LoginResult r =
        new DevConnectService.LoginResult(UUID.randomUUID(), "sess", "tok");
    when(dev.completeLogin("s", "code")).thenReturn(r);
    when(dev.sessionTtl()).thenReturn(Duration.ofDays(1));
    when(dev.tokenTtl()).thenReturn(Duration.ofHours(1));
    var ok = c.callback("s", "code", null);
    assertEquals(302, ok.getStatusCode().value());
    assertTrue(ok.getHeaders().get("Set-Cookie").size() == 2);
    UserId u = new UserId(UUID.randomUUID());
    var appUser = new dev.jahaziel.spi.UserIdentity("s", "e", "N", List.of("ROLE_USER"), List.of());
    when(users.get(u)).thenReturn(appUser);
    when(users.isMfaEnabled(u)).thenReturn(true);
    UserDto me = c.me(principal(u.value()));
    assertEquals("e", me.email());
    assertThrows(IllegalArgumentException.class, () -> c.me(() -> "nope"));
    var logout = c.logout("sess");
    assertEquals(204, logout.getStatusCode().value());
  }

  @Test
  void workspaceControllerBranches() {
    var workspaces = mock(dev.jahaziel.workspace.api.WorkspaceService.class);
    var runtime = mock(dev.jahaziel.runtime.api.RuntimeService.class);
    var auth = mock(dev.jahaziel.authorization.api.AuthorizationService.class);
    var users = mock(UserService.class);
    var ports = mock(dev.jahaziel.networking.api.PortService.class);
    var tokens = mock(JwtService.class);
    when(ports.bindingsByWorkspace(any())).thenReturn(List.of());
    var dtos =
        new WorkspaceDtoFactory(
            ports,
            users,
            tokens,
            new GatewayEndpoint("https", "code.test", 443),
            new BootProgressService(),
            900);
    WorkspaceController c =
        new WorkspaceController(workspaces, runtime, auth, users, dtos, "node-a");
    UserId u = new UserId(UUID.randomUUID());
    var identity = mock(dev.jahaziel.spi.UserIdentity.class);
    when(users.get(u)).thenReturn(identity);
    when(identity.email()).thenReturn("e");
    when(identity.displayName()).thenReturn("N");
    when(identity.roles()).thenReturn(List.of());
    when(users.isMfaEnabled(u)).thenReturn(false);
    assertEquals("e", c.me(principal(u.value())).email());
    assertThrows(IllegalArgumentException.class, () -> c.me(() -> "nope"));
    var ws = mock(dev.jahaziel.workspace.domain.Workspace.class);
    WorkspaceDtoFactoryTestHelper.setup(ws, u);
    var view = mock(dev.jahaziel.workspace.api.WorkspaceView.class);
    when(view.workspace()).thenReturn(ws);
    when(workspaces.describeByOwner(u)).thenReturn(List.of(view));
    when(users.slug(u)).thenReturn("slug");
    assertEquals(1, c.listMine(principal(u.value())).size());
    when(workspaces.create(any(), any(), any(Integer.class), any(Long.class), any()))
        .thenReturn(ws);
    when(workspaces.describe(ws.id())).thenReturn(view);
    assertTrue(c.create(principal(u.value()), new CreateWorkspaceRequest("n", null, null)) != null);
    assertTrue(c.create(principal(u.value()), new CreateWorkspaceRequest("n", 4, 4096)) != null);
    when(auth.isAllowed(any(), any(), any())).thenReturn(true);
    when(workspaces.describe(any())).thenReturn(view);
    assertTrue(c.get(UUID.randomUUID(), principal(u.value())) != null);
    when(auth.isAllowed(any(), any(), any())).thenReturn(false);
    assertThrows(AccessDeniedException.class, () -> c.get(UUID.randomUUID(), principal(u.value())));
    assertThrows(IllegalArgumentException.class, () -> c.me(() -> "bad"));
    when(auth.isAllowed(any(), any(), any())).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () -> c.delete(UUID.randomUUID(), false, principal(u.value())));
    var wid = new dev.jahaziel.workspace.domain.WorkspaceId(UUID.randomUUID());
    var ws2 = mock(dev.jahaziel.workspace.domain.Workspace.class);
    when(ws2.id()).thenReturn(wid);
    when(auth.isAllowed(any(), any(), any())).thenReturn(true);
    org.mockito.Mockito.doThrow(new dev.jahaziel.runtime.api.NodeAgentUnreachableException("down"))
        .when(runtime)
        .stop(any());
    c.delete(wid.value(), true, principal(u.value()));
  }

  @Test
  void runtimeControllerMetricsBranches() {
    var runtime = mock(dev.jahaziel.runtime.api.RuntimeService.class);
    var workspaces = mock(dev.jahaziel.workspace.api.WorkspaceService.class);
    var metrics = mock(dev.jahaziel.monitoring.api.MetricsService.class);
    var auth = mock(dev.jahaziel.authorization.api.AuthorizationService.class);
    var users = mock(UserService.class);
    var ports = mock(dev.jahaziel.networking.api.PortService.class);
    var tokens = mock(JwtService.class);
    when(ports.bindingsByWorkspace(any())).thenReturn(List.of());
    var dtos =
        new WorkspaceDtoFactory(
            ports,
            users,
            tokens,
            new GatewayEndpoint("https", "code.test", 443),
            new BootProgressService(),
            900);
    RuntimeController c = new RuntimeController(runtime, workspaces, metrics, auth, dtos);
    UserId u = new UserId(UUID.randomUUID());
    UUID wid = UUID.randomUUID();
    when(auth.isAllowed(any(), any(), any())).thenReturn(true);
    var ws = mock(dev.jahaziel.workspace.domain.Workspace.class);
    WorkspaceDtoFactoryTestHelper.setup(ws, u);
    var view = mock(dev.jahaziel.workspace.api.WorkspaceView.class);
    when(view.workspace()).thenReturn(ws);
    when(workspaces.describe(any())).thenReturn(view);
    when(users.slug(any())).thenReturn("slug");
    assertTrue(c.start(wid, principal(u.value())) != null);
    assertTrue(c.stop(wid, principal(u.value())) != null);
    assertTrue(c.restart(wid, principal(u.value())) != null);
    when(metrics.latest(any())).thenReturn(Optional.empty());
    assertEquals(MetricsDto.zero(), c.metrics(wid, principal(u.value())));
    when(metrics.latest(any()))
        .thenReturn(
            Optional.of(
                new dev.jahaziel.monitoring.api.HourlyRollup(
                    UUID.randomUUID(), Instant.now(), 1, 1, 1, 1, 1, 1, 1, 1, 1)));
    assertTrue(c.metrics(wid, principal(u.value())).cpuUsagePercent() == 1);
    when(auth.isAllowed(any(), any(), any())).thenReturn(false);
    assertThrows(AccessDeniedException.class, () -> c.start(wid, principal(u.value())));
    assertThrows(IllegalArgumentException.class, () -> c.start(wid, () -> "bad"));
  }

  @Test
  void snapshotAndTerminalControllerBranches() {
    var snapshots = mock(dev.jahaziel.snapshot.api.SnapshotService.class);
    var workspaces = mock(dev.jahaziel.workspace.api.WorkspaceService.class);
    var auth = mock(dev.jahaziel.authorization.api.AuthorizationService.class);
    var users = mock(UserService.class);
    var ports = mock(dev.jahaziel.networking.api.PortService.class);
    var tokens = mock(JwtService.class);
    when(ports.bindingsByWorkspace(any())).thenReturn(List.of());
    var dtos =
        new WorkspaceDtoFactory(
            ports,
            users,
            tokens,
            new GatewayEndpoint("https", "code.test", 443),
            new BootProgressService(),
            900);
    SnapshotController sc = new SnapshotController(snapshots, workspaces, auth, dtos);
    UserId u = new UserId(UUID.randomUUID());
    UUID wid = UUID.randomUUID();
    when(auth.isAllowed(any(), any(), any())).thenReturn(true);
    when(snapshots.list(any())).thenReturn(List.of());
    assertTrue(sc.list(wid, principal(u.value())).isEmpty());
    var meta =
        new dev.jahaziel.snapshot.domain.SnapshotMetadata(
            new dev.jahaziel.snapshot.domain.SnapshotId(UUID.randomUUID()),
            new dev.jahaziel.workspace.domain.WorkspaceId(wid),
            "t",
            dev.jahaziel.snapshot.domain.SnapshotKind.MANUAL,
            Instant.now(),
            "u",
            java.nio.file.Path.of("/s"),
            0,
            0,
            Optional.empty(),
            "");
    when(snapshots.create(any(), any(), any(), any())).thenReturn(meta);
    SnapshotDto dto = sc.create(wid, new CreateSnapshotRequest("t"), principal(u.value()));
    assertEquals("t", dto.tag());
    var ws = mock(dev.jahaziel.workspace.domain.Workspace.class);
    WorkspaceDtoFactoryTestHelper.setup(ws, u);
    var view = mock(dev.jahaziel.workspace.api.WorkspaceView.class);
    when(view.workspace()).thenReturn(ws);
    when(workspaces.describe(any())).thenReturn(view);
    when(users.slug(any())).thenReturn("slug");
    assertTrue(sc.restore(wid, UUID.randomUUID(), principal(u.value())) != null);
    when(auth.isAllowed(any(), any(), any())).thenReturn(false);
    assertThrows(AccessDeniedException.class, () -> sc.list(wid, principal(u.value())));
    var terminals = mock(dev.jahaziel.terminal.api.TerminalService.class);
    TerminalController tc = new TerminalController(terminals, auth);
    when(auth.isAllowed(any(), any(), any())).thenReturn(true);
    var sess =
        new dev.jahaziel.terminal.domain.TerminalSession(
            new dev.jahaziel.terminal.domain.TerminalSessionId(UUID.randomUUID()),
            new dev.jahaziel.workspace.domain.WorkspaceId(wid),
            u,
            24,
            80);
    when(terminals.open(any(), any(), any(Integer.class), any(Integer.class))).thenReturn(sess);
    TerminalSessionDto opened = tc.open(wid, 24, 80, principal(u.value()));
    assertTrue(opened != null);
    when(terminals.listByWorkspace(any())).thenReturn(List.of(sess));
    assertEquals(1, tc.list(wid, principal(u.value())).size());
    when(terminals.get(any())).thenReturn(sess);
    tc.close(sess.id().value(), principal(u.value()));
  }

  @Test
  void portControllerBranches() {
    var ports = mock(dev.jahaziel.networking.api.PortService.class);
    var users = mock(UserService.class);
    var auth = mock(dev.jahaziel.authorization.api.AuthorizationService.class);
    var routes = mock(dev.jahaziel.networking.api.GatewayRouteService.class);
    var tokens = mock(JwtService.class);
    PortController c =
        new PortController(
            ports,
            users,
            auth,
            routes,
            tokens,
            new GatewayEndpoint("https", "code.test", 443),
            900);
    UserId u = new UserId(UUID.randomUUID());
    UUID wid = UUID.randomUUID();
    when(auth.isAllowed(any(), any(), any())).thenReturn(true);
    PortBinding b = new PortBinding(u.value(), 8080, wid, "http", Instant.now());
    when(ports.bindingsByWorkspace(any())).thenReturn(List.of(b));
    when(users.slug(any())).thenReturn("slug");
    when(tokens.issue(any(), any(), any(), any())).thenReturn("tok");
    assertEquals(1, c.list(wid, principal(u.value())).size());
    when(ports.registerPort(any(), any(Integer.class), any(), any())).thenReturn(b);
    assertTrue(
        c.register(wid, new RegisterPortRequest(8080, "http"), principal(u.value())) != null);
    c.unregister(wid, 8080, principal(u.value()));
    when(auth.isAllowed(any(), any(), any())).thenReturn(false);
    assertThrows(AccessDeniedException.class, () -> c.list(wid, principal(u.value())));
    assertThrows(IllegalArgumentException.class, () -> c.list(wid, () -> "bad"));
  }

  @Test
  void adminControllerBranches() {
    var admin = mock(dev.jahaziel.administration.api.AdminService.class);
    var auth = mock(dev.jahaziel.authorization.api.AuthorizationService.class);
    var users = mock(UserService.class);
    var workspaces = mock(dev.jahaziel.workspace.api.WorkspaceService.class);
    var nodes = mock(dev.jahaziel.node.api.NodeService.class);
    var audit = mock(dev.jahaziel.audit.api.AuditService.class);
    var policies = mock(dev.jahaziel.policy.api.PolicyService.class);
    var metrics = mock(dev.jahaziel.monitoring.api.MetricsService.class);
    var ports = mock(dev.jahaziel.networking.api.PortService.class);
    var tokens = mock(JwtService.class);
    when(ports.bindingsByWorkspace(any())).thenReturn(List.of());
    var dtos =
        new WorkspaceDtoFactory(
            ports,
            users,
            tokens,
            new GatewayEndpoint("https", "code.test", 443),
            new BootProgressService(),
            900);
    AdminController c =
        new AdminController(admin, auth, users, workspaces, nodes, audit, policies, metrics, dtos);
    UserId adminId = new UserId(UUID.randomUUID());
    when(auth.isAdmin(adminId)).thenReturn(true);
    when(users.isMfaEnabled(adminId)).thenReturn(true);
    assertThrows(IllegalArgumentException.class, () -> c.audit(0, principal(adminId.value())));
    assertThrows(IllegalArgumentException.class, () -> c.audit(-1, principal(adminId.value())));
    when(audit.latest(5)).thenReturn(List.of());
    assertTrue(c.audit(5, principal(adminId.value())).isEmpty());
    when(audit.latest(1000)).thenReturn(List.of());
    assertTrue(c.audit(5000, principal(adminId.value())).isEmpty());
    when(policies.listByScope("global", null)).thenReturn(List.of());
    assertTrue(c.policy("global", "", principal(adminId.value())).isEmpty());
    assertTrue(c.policy("user", "u1", principal(adminId.value())).isEmpty());
    var doc =
        new dev.jahaziel.policy.api.PolicyDoc(
            UUID.randomUUID(), "global", null, "{}", Instant.now(), "op");
    when(policies.create(any(), any(), any(), any())).thenReturn(doc);
    assertEquals(
        doc,
        c.putPolicy(new CreatePolicyRequest("global", null, "{}"), principal(adminId.value())));
    when(metrics.latest(any())).thenReturn(Optional.empty());
    assertTrue(c.metrics(UUID.randomUUID(), principal(adminId.value())).isEmpty());
    when(nodes.list()).thenReturn(List.of());
    when(admin.listWorkspaces()).thenReturn(List.of());
    AdminHealthDto health = c.health(principal(adminId.value()));
    assertEquals("ok", health.status());
    assertThrows(IllegalArgumentException.class, () -> c.health(() -> "bad"));
    when(auth.isAdmin(adminId)).thenReturn(false);
    assertThrows(AccessDeniedException.class, () -> c.health(principal(adminId.value())));
    when(auth.isAdmin(adminId)).thenReturn(true);
    c.deletePolicy(UUID.randomUUID(), principal(adminId.value()));
    assertThrows(IllegalArgumentException.class, () -> c.health(() -> "bad"));
  }

  @Test
  void dtoMappers() {
    var node =
        new dev.jahaziel.node.domain.Node(
            new dev.jahaziel.node.domain.NodeId("n1"),
            "n",
            "r",
            new dev.jahaziel.node.domain.NodeCapacity(4, 8192, 100),
            "fp");
    NodeDto nd = NodeDto.from(node);
    assertEquals("n1", nd.nodeId());
    AuditRecord rec =
        new AuditRecord(
            UUID.randomUUID(),
            Instant.now(),
            "actor",
            dev.jahaziel.spi.ActorType.USER,
            "act",
            Optional.of("t"),
            Optional.of("tt"),
            dev.jahaziel.spi.Outcome.SUCCESS,
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            Optional.empty(),
            "h");
    AuditRecordDto ad = AuditRecordDto.from(rec);
    assertEquals("USER", ad.actorType());
  }

  static final class WorkspaceDtoFactoryTestHelper {
    static void setup(dev.jahaziel.workspace.domain.Workspace ws, UserId owner) {
      when(ws.id()).thenReturn(new dev.jahaziel.workspace.domain.WorkspaceId(UUID.randomUUID()));
      when(ws.owner()).thenReturn(owner);
      when(ws.name()).thenReturn("n");
      when(ws.state())
          .thenReturn(new dev.jahaziel.workspace.domain.WorkspaceState.Ready(Instant.now()));
      when(ws.quota()).thenReturn(new dev.jahaziel.workspace.domain.WorkspaceQuota(100L, 10L));
    }
  }
}
