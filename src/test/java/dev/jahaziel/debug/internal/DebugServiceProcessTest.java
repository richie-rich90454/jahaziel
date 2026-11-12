/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.debug.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.debug.api.DebugService;
import dev.jahaziel.workspace.api.WorkspaceDirectoryService;
import dev.jahaziel.workspace.api.WorkspaceProcessRegistry;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.api.WorkspaceTaskService.DebugTarget;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class DebugServiceProcessTest {
  @TempDir Path temporaryDirectory;

  @Test
  @Timeout(30)
  void launchesOnlyServerBoundProgramAndReturnsWorkspaceRelativeSourcePaths() throws Exception {
    WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
    Path workspaceHome = temporaryDirectory.resolve("workspace");
    Files.createDirectories(workspaceHome);
    Files.writeString(workspaceHome.resolve("main.js"), "console.log('safe');");
    Workspace workspace = readyWorkspace(workspaceId);
    WorkspaceDirectoryService directories = mock(WorkspaceDirectoryService.class);
    when(directories.home(workspaceId)).thenReturn(workspaceHome);
    WorkspaceService workspaces = mock(WorkspaceService.class);
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    WorkspaceProcessRegistry registry = mock(WorkspaceProcessRegistry.class);
    when(registry.dedicatedSessionCommand(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(registry.track(any(), any(), any()))
        .thenReturn(mock(WorkspaceProcessRegistry.Registration.class));
    Path java =
        Path.of(
            System.getProperty("java.home"),
            "bin",
            System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
    DebugAdapterProperties properties =
        new DebugAdapterProperties(
            Map.of(
                "node",
                new DebugAdapterProperties.Adapter(
                    "fixture",
                    List.of(
                        java.toString(),
                        "-cp",
                        Path.of("target", "test-classes").toAbsolutePath().toString(),
                        FakeDapAdapter.class.getName()),
                    DebugAdapterProperties.Transport.STDIO)));
    DebugServiceImpl service =
        new DebugServiceImpl(
            properties,
            directories,
            workspaces,
            registry,
            mock(WorkspaceTaskService.class),
            new ObjectMapper(),
            temporaryDirectory.resolve("data").toString());
    BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    try {
      DebugService.Session session =
          service.start(
              workspaceId,
              new DebugService.StartRequest("node", "main.js", null, List.of(), ".", false));
      assertEquals("RUNNING", session.state());
      assertEquals("pwa-node", session.adapterId());
      service.attach(workspaceId, session.id(), messages::add);
      service.send(workspaceId, session.id(), request(1, "initialize", Map.of()));
      service.send(
          workspaceId,
          session.id(),
          request(
              2,
              "launch",
              Map.of("program", "C:/outside/secret.js", "args", List.of("untrusted"))));

      JsonNode launchEcho = awaitEvent(messages, "output");
      String program = launchEcho.path("body").path("output").asString("");
      assertTrue(program.startsWith(workspaceHome.toRealPath().toString()));
      assertTrue(!program.contains("outside") && !program.contains("untrusted"));

      service.send(workspaceId, session.id(), request(3, "stackTrace", Map.of("threadId", 1)));
      JsonNode stack = awaitResponse(messages, 3);
      JsonNode source = stack.path("body").path("stackFrames").get(0).path("source");
      assertEquals("main.js", source.path("path").asString());
      assertNotNull(service.get(workspaceId, session.id()));
      for (String unsupported :
          List.of(
              "attach", "runInTerminal", "startDebugging", "restart", "writeMemory", "terminate")) {
        assertThrows(
            IllegalArgumentException.class,
            () -> service.send(workspaceId, session.id(), request(4, unsupported, Map.of())));
      }
    } finally {
      service.closeAll();
    }
  }

  @Test
  @Timeout(30)
  void attachesOnlyToAnOpaqueWorkspaceTargetAndReplacesClientProcessFields() throws Exception {
    WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
    Path workspaceHome = temporaryDirectory.resolve("attach-workspace");
    Files.createDirectories(workspaceHome);
    Workspace workspace = readyWorkspace(workspaceId);
    WorkspaceDirectoryService directories = mock(WorkspaceDirectoryService.class);
    when(directories.home(workspaceId)).thenReturn(workspaceHome);
    WorkspaceService workspaces = mock(WorkspaceService.class);
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    WorkspaceProcessRegistry registry = mock(WorkspaceProcessRegistry.class);
    when(registry.dedicatedSessionCommand(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(registry.track(any(), any(), any()))
        .thenReturn(mock(WorkspaceProcessRegistry.Registration.class));
    UUID targetId = UUID.randomUUID();
    WorkspaceTaskService tasks = mock(WorkspaceTaskService.class);
    when(tasks.debugTarget(eq(workspaceId), eq(targetId)))
        .thenReturn(
            new DebugTarget(
                targetId,
                workspaceId.value(),
                UUID.randomUUID(),
                "node",
                "Start preview server",
                ProcessHandle.current().pid(),
                null,
                null));
    Path java =
        Path.of(
            System.getProperty("java.home"),
            "bin",
            System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
    DebugAdapterProperties properties =
        new DebugAdapterProperties(
            Map.of(
                "node",
                new DebugAdapterProperties.Adapter(
                    "fixture",
                    List.of(
                        java.toString(),
                        "-cp",
                        Path.of("target", "test-classes").toAbsolutePath().toString(),
                        FakeDapAdapter.class.getName()),
                    DebugAdapterProperties.Transport.STDIO)));
    DebugServiceImpl service =
        new DebugServiceImpl(
            properties,
            directories,
            workspaces,
            registry,
            tasks,
            new ObjectMapper(),
            temporaryDirectory.resolve("attach-data").toString());
    BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    try {
      DebugService.Session session =
          service.attachToTarget(workspaceId, new DebugService.AttachRequest(targetId));
      service.attach(workspaceId, session.id(), messages::add);
      service.send(workspaceId, session.id(), request(1, "initialize", Map.of()));
      awaitResponse(messages, 1);
      service.send(
          workspaceId,
          session.id(),
          request(
              2,
              "attach",
              Map.of("processId", 999999, "hostName", "attacker.invalid", "port", 7000)));

      JsonNode attached = awaitEvent(messages, "output");
      String echo = attached.path("body").path("output").asString("");
      assertTrue(echo.contains("processId=" + ProcessHandle.current().pid()));
      assertTrue(
          !echo.contains("999999") && !echo.contains("attacker.invalid") && !echo.contains("7000"));
      assertEquals("RUNNING", service.get(workspaceId, session.id()).state());
      assertEquals("STOPPED", service.stop(workspaceId, session.id()).state());
      assertTrue(ProcessHandle.current().isAlive());
      JsonNode disconnected = awaitEvent(messages, "output");
      assertTrue(
          disconnected
              .path("body")
              .path("output")
              .asString("")
              .contains("terminateDebuggee=false"));
    } finally {
      service.closeAll();
    }
  }

  @Test
  @Timeout(20)
  void retainsUnconfirmedDetachForRetryAndCleansTheAdapterWhenTheServerStops() throws Exception {
    WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
    Path workspaceHome = temporaryDirectory.resolve("unconfirmed-detach-workspace");
    Files.createDirectories(workspaceHome);
    Workspace workspace = readyWorkspace(workspaceId);
    WorkspaceDirectoryService directories = mock(WorkspaceDirectoryService.class);
    when(directories.home(workspaceId)).thenReturn(workspaceHome);
    WorkspaceService workspaces = mock(WorkspaceService.class);
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    WorkspaceProcessRegistry registry = mock(WorkspaceProcessRegistry.class);
    when(registry.dedicatedSessionCommand(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    AtomicReference<Process> adapterProcess = new AtomicReference<>();
    when(registry.track(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              adapterProcess.set(invocation.getArgument(2));
              return mock(WorkspaceProcessRegistry.Registration.class);
            });
    UUID targetId = UUID.randomUUID();
    WorkspaceTaskService tasks = mock(WorkspaceTaskService.class);
    when(tasks.debugTarget(eq(workspaceId), eq(targetId)))
        .thenReturn(
            new DebugTarget(
                targetId,
                workspaceId.value(),
                UUID.randomUUID(),
                "node",
                "Start preview server",
                ProcessHandle.current().pid(),
                null,
                null));
    Path java =
        Path.of(
            System.getProperty("java.home"),
            "bin",
            System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
    DebugAdapterProperties properties =
        new DebugAdapterProperties(
            Map.of(
                "node",
                new DebugAdapterProperties.Adapter(
                    "fixture",
                    List.of(
                        java.toString(),
                        "-cp",
                        Path.of("target", "test-classes").toAbsolutePath().toString(),
                        FakeDapAdapter.class.getName(),
                        "ignore-disconnect"),
                    DebugAdapterProperties.Transport.STDIO)));
    DebugServiceImpl service =
        new DebugServiceImpl(
            properties,
            directories,
            workspaces,
            registry,
            tasks,
            new ObjectMapper(),
            temporaryDirectory.resolve("unconfirmed-detach-data").toString());
    BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    try {
      DebugService.Session session =
          service.attachToTarget(workspaceId, new DebugService.AttachRequest(targetId));
      service.attach(workspaceId, session.id(), messages::add);
      service.send(workspaceId, session.id(), request(1, "initialize", Map.of()));
      awaitResponse(messages, 1);
      service.send(workspaceId, session.id(), request(2, "attach", Map.of()));
      awaitEvent(messages, "output");

      DebugService.Session failedStop = service.stop(workspaceId, session.id());

      assertEquals("FAILED", failedStop.state());
      assertEquals(failedStop.id(), service.get(workspaceId, session.id()).id());
      assertTrue(adapterProcess.get().isAlive());
      assertTrue(ProcessHandle.current().isAlive());
    } finally {
      service.closeAll();
    }
    assertFalse(adapterProcess.get().isAlive());
  }

  @Test
  @Timeout(45)
  void selectsTheFixedAttachShapeForEverySupportedLanguage() throws Exception {
    WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
    Path workspaceHome = temporaryDirectory.resolve("language-attach-workspace");
    Files.createDirectories(workspaceHome);
    Path executable = workspaceHome.resolve("debug-app");
    Files.writeString(executable, "server-managed program fixture");
    Workspace workspace = readyWorkspace(workspaceId);
    WorkspaceDirectoryService directories = mock(WorkspaceDirectoryService.class);
    when(directories.home(workspaceId)).thenReturn(workspaceHome);
    WorkspaceService workspaces = mock(WorkspaceService.class);
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    WorkspaceProcessRegistry registry = mock(WorkspaceProcessRegistry.class);
    when(registry.dedicatedSessionCommand(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(registry.track(any(), any(), any()))
        .thenReturn(mock(WorkspaceProcessRegistry.Registration.class));
    WorkspaceTaskService tasks = mock(WorkspaceTaskService.class);
    List<String> languages = List.of("node", "python", "java", "cpp", "go", "rust");
    Map<String, DebugTarget> targets = new HashMap<>();
    for (int index = 0; index < languages.size(); index++) {
      String language = languages.get(index);
      UUID id = UUID.randomUUID();
      Integer debugPort = language.equals("java") ? Integer.valueOf(43000) : null;
      if (language.equals("python")) debugPort = 43001;
      DebugTarget target =
          new DebugTarget(
              id,
              workspaceId.value(),
              UUID.randomUUID(),
              language,
              language + " preview task",
              5000L + index,
              debugPort,
              language.equals("cpp") || language.equals("rust") ? executable.toString() : null);
      targets.put(language, target);
      when(tasks.debugTarget(eq(workspaceId), eq(id))).thenReturn(target);
    }

    Path java =
        Path.of(
            System.getProperty("java.home"),
            "bin",
            System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
    DebugAdapterProperties.Adapter fixture =
        new DebugAdapterProperties.Adapter(
            "fixture",
            List.of(
                java.toString(),
                "-cp",
                Path.of("target", "test-classes").toAbsolutePath().toString(),
                FakeDapAdapter.class.getName()),
            DebugAdapterProperties.Transport.STDIO);
    Map<String, DebugAdapterProperties.Adapter> configuredAdapters = new HashMap<>();
    languages.forEach(language -> configuredAdapters.put(language, fixture));
    DebugServiceImpl service =
        new DebugServiceImpl(
            new DebugAdapterProperties(configuredAdapters),
            directories,
            workspaces,
            registry,
            tasks,
            new ObjectMapper(),
            temporaryDirectory.resolve("language-attach-data").toString());
    try {
      for (String language : languages) {
        DebugTarget target = targets.get(language);
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        DebugService.Session session =
            service.attachToTarget(workspaceId, new DebugService.AttachRequest(target.id()));
        service.attach(workspaceId, session.id(), messages::add);
        service.send(workspaceId, session.id(), request(1, "initialize", Map.of()));
        awaitResponse(messages, 1);
        service.send(
            workspaceId,
            session.id(),
            request(
                2,
                "attach",
                Map.of(
                    "processId",
                    999999,
                    "pid",
                    999999,
                    "hostName",
                    "attacker.invalid",
                    "port",
                    7000,
                    "program",
                    "/outside/attacker")));

        String echo = awaitEvent(messages, "output").path("body").path("output").asString("");
        if (List.of("node", "go").contains(language)) {
          assertTrue(echo.contains("processId=" + target.processId()), language);
        }
        if (List.of("cpp", "rust").contains(language)) {
          assertTrue(echo.contains("pid=" + target.processId()), language);
          assertTrue(echo.contains("program=" + executable.toRealPath()), language);
        }
        if (language.equals("java")) {
          assertTrue(echo.contains("hostName=127.0.0.1"), language);
          assertTrue(echo.contains("port=" + target.debugPort()), language);
        }
        if (language.equals("python")) {
          assertTrue(echo.contains("host=127.0.0.1"), language);
          assertTrue(echo.contains("port=" + target.debugPort()), language);
        }
        if (language.equals("go")) assertTrue(echo.contains("mode=local"));
        assertTrue(
            !echo.contains("999999")
                && !echo.contains("attacker.invalid")
                && !echo.contains("7000")
                && !echo.contains("/outside/attacker"),
            language);
        service.stop(workspaceId, session.id());
      }
    } finally {
      service.closeAll();
    }
  }

  private static String request(int sequence, String command, Map<String, ?> arguments) {
    ObjectMapper json = new ObjectMapper();
    ObjectNode message = json.createObjectNode();
    message.put("seq", sequence);
    message.put("type", "request");
    message.put("command", command);
    message.set("arguments", json.valueToTree(arguments));
    return json.writeValueAsString(message);
  }

  private static JsonNode awaitEvent(BlockingQueue<String> messages, String event)
      throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline) {
      String payload = messages.poll(200, TimeUnit.MILLISECONDS);
      if (payload == null) continue;
      JsonNode message = new ObjectMapper().readTree(payload);
      if (message.path("type").asString().equals("event")
          && message.path("event").asString().equals(event)) return message;
    }
    throw new AssertionError("debug adapter did not produce event " + event);
  }

  private static JsonNode awaitResponse(BlockingQueue<String> messages, int requestSequence)
      throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline) {
      String payload = messages.poll(200, TimeUnit.MILLISECONDS);
      if (payload == null) continue;
      JsonNode message = new ObjectMapper().readTree(payload);
      if (message.path("type").asString().equals("response")
          && message.path("request_seq").asInt(-1) == requestSequence) return message;
    }
    throw new AssertionError("debug adapter did not respond to request " + requestSequence);
  }

  private static Workspace readyWorkspace(WorkspaceId workspaceId) {
    Workspace workspace =
        new Workspace(
            workspaceId,
            new dev.jahaziel.identity.domain.UserId(UUID.randomUUID()),
            "project",
            WorkspaceQuota.defaults(),
            new VirtualMachine(
                new VirtualMachineId(UUID.randomUUID()),
                workspaceId,
                new VirtualMachineConfiguration(2, 2048)));
    workspace.prepare();
    workspace.boot();
    workspace.markReady();
    return workspace;
  }

  /** Minimal local DAP adapter used to assert framing, launch confinement, and path rewriting. */
  public static final class FakeDapAdapter {
    private static String launchProgram = "";

    public static void main(String[] args) throws Exception {
      int sequence = 1;
      boolean ignoreDisconnect = args.length > 0 && args[0].equals("ignore-disconnect");
      while (true) {
        String payload = readFrame(System.in);
        if (payload == null) return;
        String command = value(payload, "command");
        int requestSequence = Integer.parseInt(value(payload, "seq"));
        if (command.equals("initialize")) {
          writeFrame(
              System.out,
              "{\"seq\":"
                  + sequence++
                  + ",\"type\":\"response\",\"request_seq\":"
                  + requestSequence
                  + ",\"command\":\"initialize\",\"success\":true,\"body\":{\"supportsConfigurationDoneRequest\":true}}");
        } else if (command.equals("launch")) {
          launchProgram = value(payload, "program");
          writeFrame(
              System.out,
              "{\"seq\":" + sequence++ + ",\"type\":\"event\",\"event\":\"initialized\"}");
          writeFrame(
              System.out,
              "{\"seq\":"
                  + sequence++
                  + ",\"type\":\"response\",\"request_seq\":"
                  + requestSequence
                  + ",\"command\":\"launch\",\"success\":true}");
          writeFrame(
              System.out,
              "{\"seq\":"
                  + sequence++
                  + ",\"type\":\"event\",\"event\":\"output\",\"body\":{\"output\":\""
                  + escapeJson(launchProgram)
                  + "\"}}");
        } else if (command.equals("attach")) {
          String processId = value(payload, "processId");
          String pid = value(payload, "pid");
          String host = value(payload, "host");
          String hostName = value(payload, "hostName");
          String port = value(payload, "port");
          String program = value(payload, "program");
          String mode = value(payload, "mode");
          writeFrame(
              System.out,
              "{\"seq\":" + sequence++ + ",\"type\":\"event\",\"event\":\"initialized\"}");
          writeFrame(
              System.out,
              "{\"seq\":"
                  + sequence++
                  + ",\"type\":\"response\",\"request_seq\":"
                  + requestSequence
                  + ",\"command\":\"attach\",\"success\":true}");
          writeFrame(
              System.out,
              "{\"seq\":"
                  + sequence++
                  + ",\"type\":\"event\",\"event\":\"output\",\"body\":{\"output\":\"processId="
                  + processId
                  + ";pid="
                  + pid
                  + ";host="
                  + host
                  + ";hostName="
                  + hostName
                  + ";port="
                  + port
                  + ";program="
                  + escapeJson(program)
                  + ";mode="
                  + mode
                  + "\"}}");
        } else if (command.equals("disconnect")) {
          if (ignoreDisconnect) continue;
          writeFrame(
              System.out,
              "{\"seq\":"
                  + sequence++
                  + ",\"type\":\"event\",\"event\":\"output\",\"body\":{\"output\":\"terminateDebuggee="
                  + value(payload, "terminateDebuggee")
                  + "\"}}");
          writeFrame(
              System.out,
              "{\"seq\":"
                  + sequence++
                  + ",\"type\":\"response\",\"request_seq\":"
                  + requestSequence
                  + ",\"command\":\"disconnect\",\"success\":true}");
        } else if (command.equals("stackTrace")) {
          writeFrame(
              System.out,
              "{\"seq\":"
                  + sequence++
                  + ",\"type\":\"response\",\"request_seq\":"
                  + requestSequence
                  + ",\"command\":\"stackTrace\",\"success\":true,\"body\":{\"stackFrames\":[{\"id\":7,\"name\":\"main\",\"line\":1,\"source\":{\"name\":\"main.js\",\"path\":\""
                  + escapeJson(launchProgram)
                  + "\"}}]}}");
        } else {
          writeFrame(
              System.out,
              "{\"seq\":"
                  + sequence++
                  + ",\"type\":\"response\",\"request_seq\":"
                  + requestSequence
                  + ",\"command\":\""
                  + escapeJson(command)
                  + "\",\"success\":true}");
        }
      }
    }

    private static String value(String json, String key) {
      String marker = "\"" + key + "\":";
      int start = json.indexOf(marker);
      if (start < 0) return "";
      start += marker.length();
      if (json.charAt(start) == '"') {
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int index = start + 1; index < json.length(); index++) {
          char current = json.charAt(index);
          if (escaped) {
            result.append(current);
            escaped = false;
          } else if (current == '\\') {
            escaped = true;
          } else if (current == '"') {
            return result.toString();
          } else {
            result.append(current);
          }
        }
        return result.toString();
      }
      if (json.startsWith("true", start)) return "true";
      if (json.startsWith("false", start)) return "false";
      int end = start;
      while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
      return json.substring(start, end);
    }

    private static String escapeJson(String value) {
      return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String readFrame(InputStream input) throws IOException {
      int length = -1;
      while (true) {
        String line = readLine(input);
        if (line == null) return null;
        if (line.isEmpty()) break;
        if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
          length = Integer.parseInt(line.substring(15).strip());
        }
      }
      if (length < 0) throw new IOException("missing content length");
      byte[] body = input.readNBytes(length);
      if (body.length != length) throw new IOException("short DAP body");
      return new String(body, StandardCharsets.UTF_8);
    }

    private static String readLine(InputStream input) throws IOException {
      ByteArrayOutputStream line = new ByteArrayOutputStream();
      int value;
      while ((value = input.read()) >= 0) {
        if (value == '\n') {
          byte[] bytes = line.toByteArray();
          int length =
              bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
          return new String(bytes, 0, length, StandardCharsets.US_ASCII);
        }
        line.write(value);
      }
      return null;
    }

    private static void writeFrame(OutputStream output, String message) throws IOException {
      byte[] body = message.getBytes(StandardCharsets.UTF_8);
      output.write(
          ("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
      output.write(body);
      output.flush();
    }
  }
}
