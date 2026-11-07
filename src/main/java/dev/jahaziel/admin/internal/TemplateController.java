/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.internal;

import dev.jahaziel.api.WorkspaceDtoFactory;
import dev.jahaziel.api.dto.WorkspaceDto;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Workspace templates from {@code data/templates/<name>/template.json}. */
@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {
  private static final long MAX_STARTER_BYTES = 2L * 1024 * 1024;
  private final WorkspaceService workspaces;
  private final WorkspaceFiles files;
  private final WorkspaceTaskService tasks;
  private final PortService ports;
  private final WorkspaceDtoFactory dtos;
  private final ObjectMapper json;
  private final Path templatesDir;
  private final String defaultNode;

  public TemplateController(
      WorkspaceService workspaces,
      WorkspaceFiles files,
      WorkspaceTaskService tasks,
      PortService ports,
      WorkspaceDtoFactory dtos,
      ObjectMapper json,
      @Value("${jahaziel.templates.dir:data/templates}") String templatesDir,
      @Value("${jahaziel.default-node:node-a}") String defaultNode) {
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    this.files = Objects.requireNonNull(files, "files");
    this.tasks = Objects.requireNonNull(tasks, "tasks");
    this.ports = Objects.requireNonNull(ports, "ports");
    this.dtos = Objects.requireNonNull(dtos, "dtos");
    this.json = Objects.requireNonNull(json, "json");
    this.templatesDir = Path.of(templatesDir).toAbsolutePath().normalize();
    this.defaultNode = defaultNode;
  }

  /** Lists template names. */
  @GetMapping
  public List<String> list() {
    if (!Files.isDirectory(templatesDir)) {
      return List.of();
    }
    try (Stream<Path> entries = Files.list(templatesDir)) {
      return entries
          .filter(Files::isDirectory)
          .filter(dir -> !Files.isSymbolicLink(dir))
          .filter(dir -> Files.isRegularFile(dir.resolve("template.json")))
          .map(dir -> dir.getFileName().toString())
          .sorted()
          .toList();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  /** Returns one {@code template.json}. */
  @GetMapping("/{name}")
  public ResponseEntity<byte[]> get(@PathVariable String name) {
    Path file = checked(name).resolve("template.json");
    if (!Files.isRegularFile(file)) {
      return ResponseEntity.notFound().build();
    }
    try {
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_JSON)
          .body(Files.readAllBytes(file));
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  /** Creates a workspace from a template. */
  @PostMapping("/{name}/apply")
  @ResponseStatus(HttpStatus.CREATED)
  public WorkspaceDto apply(
      @PathVariable String name, @RequestBody ApplyRequest request, Principal principal) {
    if (request == null || request.workspaceName() == null || request.workspaceName().isBlank()) {
      throw new IllegalArgumentException("workspaceName must not be blank");
    }
    TemplateManifest template = read(name);
    if (template.starterFiles() != null && template.starterFiles().size() > 256) {
      throw new IllegalArgumentException("template starterFiles must contain at most 256 files");
    }
    if (template.name() != null && !template.name().equals(name)) {
      throw new IllegalArgumentException("template name does not match its directory");
    }
    int vcpu = template.vcpuCount() == null ? 2 : template.vcpuCount();
    long memory = template.memoryMib() == null ? 2048 : template.memoryMib();
    if (vcpu < 1 || vcpu > 64 || memory < 256 || memory > 262144) {
      throw new IllegalArgumentException("template resource limits are invalid");
    }
    if (template.ports() != null
        && template.ports().stream().anyMatch(port -> port == null || port < 1 || port > 65535)) {
      throw new IllegalArgumentException("template ports must be between 1 and 65535");
    }
    if (template.ports() != null
        && template.ports().stream().distinct().count() != template.ports().size()) {
      throw new IllegalArgumentException("template ports must be unique");
    }
    if (template.ports() != null && template.ports().size() > PortService.MAX_PORTS_PER_WORKSPACE) {
      throw new IllegalArgumentException(
          "template ports must contain at most "
              + PortService.MAX_PORTS_PER_WORKSPACE
              + " entries");
    }
    if (template.languagePacks() != null) {
      if (template.languagePacks().size() > 6
          || template.languagePacks().stream()
              .anyMatch(
                  pack ->
                      !List.of("cpp", "java", "go", "rust", "typescript", "python")
                          .contains(pack))) {
        throw new IllegalArgumentException("template contains an unsupported language pack");
      }
      if (template.languagePacks().stream().distinct().count() != template.languagePacks().size()) {
        throw new IllegalArgumentException("template language packs must be unique");
      }
    }
    validateSetupSteps(template.setupSteps());
    validateRunTasks(template.runTasks(), template.ports());
    java.util.Map<String, String> starterFiles =
        template.starterFiles() == null ? java.util.Map.of() : template.starterFiles();
    long starterBytes = 0;
    for (var starter : starterFiles.entrySet()) {
      validateStarter(starter.getKey(), starter.getValue());
      starterBytes += starter.getValue().getBytes(StandardCharsets.UTF_8).length;
      if (starterBytes > MAX_STARTER_BYTES) {
        throw new IllegalArgumentException("template starter files exceed the 2 MiB total limit");
      }
    }
    UserId owner = userId(principal);
    var workspace =
        workspaces.create(owner, request.workspaceName(), vcpu, memory, new NodeId(defaultNode));
    tasks.initialize(workspace.id(), name);
    WorkspaceFiles.Operations workspaceFiles = files.forWorkspace(workspace.id());
    for (var starter : starterFiles.entrySet()) {
      String relativePath = starter.getKey();
      String content = starter.getValue();
      int separator = relativePath.lastIndexOf('/');
      if (separator > 0) {
        workspaceFiles.createDirectories(relativePath.substring(0, separator));
      }
      workspaceFiles.save(relativePath, content, null, true);
    }
    if (template.ports() != null) {
      for (int port : template.ports()) {
        ports.registerPort(owner, port, workspace.id(), "http");
      }
    }
    tasks.retrySetup(workspace.id());
    return dtos.forUser(owner, workspaces.describe(workspace.id()));
  }

  private static void validateStarter(String relativePath, String content) {
    if (relativePath == null
        || relativePath.isBlank()
        || relativePath.indexOf('\\') >= 0
        || relativePath.indexOf('\0') >= 0
        || relativePath.startsWith("/")) {
      throw new IllegalArgumentException("template starter path must be a relative workspace path");
    }
    for (String segment : relativePath.split("/", -1)) {
      if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
        throw new IllegalArgumentException("template starter path contains an invalid segment");
      }
    }
    if (content == null || content.getBytes(StandardCharsets.UTF_8).length > 256 * 1024) {
      throw new IllegalArgumentException(
          "template starter file is null or too large: " + relativePath);
    }
  }

  private static void validateSetupSteps(List<SetupStep> steps) {
    if (steps == null) return;
    if (steps.size() > 32)
      throw new IllegalArgumentException("template setupSteps must contain at most 32 steps");
    java.util.HashSet<String> ids = new java.util.HashSet<>();
    for (SetupStep step : steps) {
      if (step == null
          || step.id() == null
          || !step.id().matches("[a-z0-9][a-z0-9_-]{0,63}")
          || !ids.add(step.id())) {
        throw new IllegalArgumentException("template setup step ids must be valid and unique");
      }
      validateCommand(step.command(), "setup step " + step.id());
    }
  }

  private static void validateRunTasks(List<RunTask> tasks, List<Integer> templatePorts) {
    if (tasks == null) return;
    if (tasks.size() > 32)
      throw new IllegalArgumentException("template runTasks must contain at most 32 tasks");
    java.util.HashSet<String> ids = new java.util.HashSet<>();
    for (RunTask task : tasks) {
      if (task == null
          || task.id() == null
          || !task.id().matches("[a-z0-9][a-z0-9_-]{0,63}")
          || !ids.add(task.id())) {
        throw new IllegalArgumentException("template run task ids must be valid and unique");
      }
      if (task.label() == null || task.label().isBlank() || task.label().length() > 80) {
        throw new IllegalArgumentException("template run task label must contain 1-80 characters");
      }
      validateCommand(task.command(), "run task " + task.id());
      if (task.port() != null
          && (task.port() < 1
              || task.port() > 65535
              || templatePorts == null
              || !templatePorts.contains(task.port()))) {
        throw new IllegalArgumentException("run task port must be declared by the template");
      }
    }
  }

  private static void validateCommand(List<String> command, String source) {
    if (command == null || command.isEmpty() || command.size() > 64) {
      throw new IllegalArgumentException(source + " command must be a non-empty argument list");
    }
    if (command.stream()
        .anyMatch(
            argument ->
                argument == null
                    || argument.isBlank()
                    || argument.length() > 1024
                    || argument.indexOf('\0') >= 0)) {
      throw new IllegalArgumentException(source + " command contains an invalid argument");
    }
  }

  private TemplateManifest read(String name) {
    try {
      Path manifest = checked(name).resolve("template.json");
      if (!Files.isRegularFile(manifest)) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
      }
      return json.treeToValue(json.readTree(Files.readString(manifest)), TemplateManifest.class);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private Path checked(String name) {
    if (name == null || !name.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")) {
      throw new IllegalArgumentException("invalid template name");
    }
    Path dir = templatesDir.resolve(name).normalize();
    try {
      if (!dir.startsWith(templatesDir)
          || !dir.toRealPath().startsWith(templatesDir.toRealPath())) {
        throw new IllegalArgumentException("invalid template location");
      }
    } catch (IOException failure) {
      if (!Files.exists(dir)) {
        return dir;
      }
      throw new UncheckedIOException(failure);
    }
    return dir;
  }

  private record TemplateManifest(
      String name,
      String description,
      Integer vcpuCount,
      Long memoryMib,
      List<Integer> ports,
      List<String> languagePacks,
      java.util.Map<String, String> starterFiles,
      List<SetupStep> setupSteps,
      List<RunTask> runTasks) {}

  private record SetupStep(String id, List<String> command) {}

  private record RunTask(String id, String label, List<String> command, Integer port) {}

  private static UserId userId(Principal principal) {
    try {
      return new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
  }

  /** Apply request. */
  public record ApplyRequest(@NotBlank @Size(max = 64) String workspaceName) {}
}
