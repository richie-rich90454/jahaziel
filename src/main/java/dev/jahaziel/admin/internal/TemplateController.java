/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.internal;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.jahaziel.api.WorkspaceDtoFactory;
import dev.jahaziel.api.dto.WorkspaceDto;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.workspace.api.WorkspaceService;
import java.io.IOException;
import java.io.UncheckedIOException;
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

/** Workspace templates from {@code data/templates/<name>/template.json}. */
@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {
  private final WorkspaceService workspaces;
  private final WorkspaceDtoFactory dtos;
  private final ObjectMapper json;
  private final Path templatesDir;
  private final String defaultNode;

  public TemplateController(
      WorkspaceService workspaces,
      WorkspaceDtoFactory dtos,
      ObjectMapper json,
      @Value("${jahaziel.templates.dir:data/templates}") String templatesDir,
      @Value("${jahaziel.default-node:node-a}") String defaultNode) {
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    this.dtos = Objects.requireNonNull(dtos, "dtos");
    this.json = Objects.requireNonNull(json, "json");
    this.templatesDir = Path.of(templatesDir);
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
    JsonNode template = read(name);
    int vcpu = template.path("vcpuCount").asInt(2);
    long memory = template.path("memoryMib").asLong(2048);
    UserId owner = userId(principal);
    var workspace =
        workspaces.create(owner, request.workspaceName(), vcpu, memory, new NodeId(defaultNode));
    return dtos.forUser(owner, workspaces.describe(workspace.id()));
  }

  private JsonNode read(String name) {
    try {
      return json.readTree(Files.readString(checked(name).resolve("template.json")));
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private Path checked(String name) {
    Path dir = templatesDir.resolve(name).normalize();
    if (!dir.startsWith(templatesDir.normalize()) || name.contains("..")) {
      throw new IllegalArgumentException("invalid template name");
    }
    return dir;
  }

  private static UserId userId(Principal principal) {
    try {
      return new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
  }

  /** Apply request. */
  public record ApplyRequest(String workspaceName) {}
}
