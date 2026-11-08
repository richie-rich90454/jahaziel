/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.debug.api;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Workspace-scoped Debug Adapter Protocol sessions. */
public interface DebugService {
  Map<String, String> DAP_ADAPTER_IDS =
      Map.of(
          "node", "pwa-node",
          "python", "debugpy",
          "java", "java",
          "cpp", "lldb",
          "go", "go",
          "rust", "lldb");
  Set<String> LANGUAGES = DAP_ADAPTER_IDS.keySet();

  /** Starts a server-managed adapter for one launch inside the workspace. */
  Session start(WorkspaceId workspaceId, StartRequest request);

  /** Returns the session only when it belongs to the specified workspace. */
  Session get(WorkspaceId workspaceId, UUID sessionId);

  /** Returns currently active sessions belonging to the specified workspace. */
  List<Session> list(WorkspaceId workspaceId);

  /** Stops a session only when it belongs to the specified workspace. */
  Session stop(WorkspaceId workspaceId, UUID sessionId);

  /** Attaches a reconnecting WebSocket to the DAP message stream. */
  AutoCloseable attach(WorkspaceId workspaceId, UUID sessionId, Consumer<String> output);

  /** Sends one JSON DAP message after validating its workspace-bound launch fields. */
  void send(WorkspaceId workspaceId, UUID sessionId, String message);

  /** Launch-only configuration accepted from the browser. It has no command or executable field. */
  record StartRequest(
      String languageId,
      String program,
      String mainClass,
      List<String> arguments,
      String workingDirectory,
      boolean stopOnEntry) {
    public StartRequest {
      if (languageId == null || !LANGUAGES.contains(languageId)) {
        throw new IllegalArgumentException("unsupported debug language");
      }
      arguments = arguments == null ? List.of() : List.copyOf(arguments);
      if (arguments.size() > 128
          || arguments.stream().anyMatch(value -> value == null || value.length() > 4096)) {
        throw new IllegalArgumentException("debug arguments exceed the supported limit");
      }
      if ("java".equals(languageId)) {
        if (!validJavaMainClass(mainClass) || program != null && !program.isBlank()) {
          throw new IllegalArgumentException("Java debug requires a main class, not an executable");
        }
      } else {
        program = relativePath(program, "program");
        if (mainClass != null && !mainClass.isBlank()) {
          throw new IllegalArgumentException("mainClass is supported only by Java debugging");
        }
      }
      workingDirectory =
          workingDirectory == null || workingDirectory.isBlank()
              ? "."
              : relativePath(workingDirectory, "workingDirectory");
    }

    private static boolean validJavaMainClass(String value) {
      return value != null
          && value.length() <= 512
          && value.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    }

    private static String relativePath(String value, String name) {
      if (value == null || value.isBlank() || value.length() > 4096 || value.indexOf('\0') >= 0) {
        throw new IllegalArgumentException(name + " must be a relative workspace path");
      }
      Path path = Path.of(value.replace('\\', '/'));
      if (path.isAbsolute() || path.normalize().startsWith("..")) {
        throw new IllegalArgumentException(name + " must stay inside the workspace");
      }
      return path.normalize().toString().replace('\\', '/');
    }
  }

  /** Public status that does not expose adapter command paths or process identifiers. */
  record Session(
      UUID id,
      UUID workspaceId,
      String languageId,
      String adapterId,
      String state,
      String adapterVersion,
      Instant startedAt,
      String error) {}
}
