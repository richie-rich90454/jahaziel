/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.internal;

import dev.jahaziel.administration.api.AdminService;
import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.ide.internal.IdeService;
import dev.jahaziel.node.api.NodeService;
import dev.jahaziel.platform.Platforms;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Admin dashboard at {@code /_admin}: overview, health, workspaces, users, logs, config, and a
 * diagnostics download. Live logs stream server-sent events from the control-plane log file.
 */
@RestController
public class AdminUiController {
  private final AdminService admin;
  private final NodeService nodes;
  private final AuthorizationService auth;
  private final UserService users;
  private final IdeService ide;
  private final Path logFile;
  private final String backend;
  private final boolean demo;
  private final ExecutorService sender = Executors.newCachedThreadPool();

  public AdminUiController(
      AdminService admin,
      NodeService nodes,
      AuthorizationService auth,
      UserService users,
      IdeService ide,
      @Value("${logging.file.name:/var/log/jahaziel/control-plane.log}") String logFile,
      @Value("${jahaziel.workspace.backend:local}") String backend,
      @Value("${jahaziel.demo:false}") boolean demo) {
    this.admin = admin;
    this.nodes = nodes;
    this.auth = auth;
    this.users = users;
    this.ide = ide;
    this.logFile = Path.of(logFile);
    this.backend = backend;
    this.demo = demo;
  }

  /** Returns the dashboard page. */
  @GetMapping(value = "/_admin", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> overview(Principal principal) {
    UserId caller = requireAdmin(principal);
    int nodeCount = nodes.list().size();
    int workspaceCount = admin.listWorkspaces().size();
    String html =
        "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
            + "<title>Jahaziel admin</title></head><body>"
            + "<h1>Jahaziel admin</h1>"
            + "<p>operator: " + caller.value() + "</p>"
            + "<ul><li>health: ok, nodes " + nodeCount + ", workspaces " + workspaceCount + "</li>"
            + "<li>backend: " + backend + "</li>"
            + "<li>reh: " + ide.variant().slug() + "</li></ul>"
            + "<ul><li><a href=\"/admin/v1/health\">health</a></li>"
            + "<li><a href=\"/admin/v1/workspaces\">workspaces</a></li>"
            + "<li><a href=\"/admin/v1/users\">users</a></li>"
            + "<li><a href=\"/admin/v1/audit/query\">logs</a></li>"
            + "<li><a href=\"/admin/v1/logs/stream\">live logs</a></li>"
            + "<li><a href=\"/_admin/diagnostics\">diagnostics download</a></li></ul>"
            + "<pre id=\"logs\"></pre>"
            + "<script>var s=new EventSource('/admin/v1/logs/stream');"
            + "s.onmessage=function(e){document.getElementById('logs').textContent+=e.data+'\\n';};</script>"
            + "</body></html>";
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
  }

  /** Downloads a diagnostics bundle. */
  @GetMapping(value = "/_admin/diagnostics", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> diagnostics(Principal principal) {
    requireAdmin(principal);
    String body = "status=ok\nnodes=" + nodes.list().size() + "\nworkspaces="
        + admin.listWorkspaces().size() + "\nbackend=" + backend + "\nreh="
        + ide.variant().slug() + "\njava=" + System.getProperty("java.version") + "\nhost="
        + Platforms.hostRehVariant() + "\n";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("diagnostics.txt").build().toString())
        .contentType(MediaType.TEXT_PLAIN)
        .body(body);
  }

  /** Streams the last 200 log lines as server-sent events. */
  @GetMapping(value = "/admin/v1/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter logs(Principal principal) {
    requireAdmin(principal);
    SseEmitter emitter = new SseEmitter(60_000L);
    sender.execute(() -> {
      try {
        List<String> lines =
            Files.isRegularFile(logFile) ? Files.readAllLines(logFile) : List.of("no log file");
        lines.stream().skip(Math.max(0, lines.size() - 200)).forEach(line -> {
          try {
            emitter.send(SseEmitter.event().data(line));
          } catch (IOException ignored) {
            // Client went away; completing below ends the stream.
          }
        });
        emitter.complete();
      } catch (IOException failure) {
        emitter.completeWithError(failure);
      }
    });
    return emitter;
  }

  private UserId requireAdmin(Principal principal) {
    if (principal == null) {
      if (demo) {
        return DemoSeeder.DEMO_USER;
      }
      throw new IllegalArgumentException("missing principal");
    }
    UserId user;
    try {
      user = new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
    if (!auth.isAdmin(user)) {
      throw new AccessDeniedException(user, "admin");
    }
    if (!users.isMfaEnabled(user)) {
      throw new AccessDeniedException(user, "admin:mfa");
    }
    return user;
  }
}
