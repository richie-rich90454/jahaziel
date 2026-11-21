/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.jahaziel.api.WorkspaceDtoFactory;
import dev.jahaziel.api.dto.WorkspaceDto;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.domain.Workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
        try (java.util.stream.Stream<Path> entries = Files.list(templatesDir)) {
            List<String> names = new ArrayList<String>();
            Iterator<Path> paths = entries.iterator();
            while (paths.hasNext()) {
                Path dir = paths.next();
                if (!Files.isDirectory(dir)
                        || Files.isSymbolicLink(dir)
                        || !Files.isRegularFile(dir.resolve("template.json"))) {
                    continue;
                }
                names.add(dir.getFileName().toString());
            }
            Collections.sort(names);
            return names;
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
        if (request == null
                || request.workspaceName() == null
                || request.workspaceName().isBlank()) {
            throw new IllegalArgumentException("workspaceName must not be blank");
        }
        TemplateManifest template = read(name);
        if (template.starterFiles() != null && template.starterFiles().size() > 256) {
            throw new IllegalArgumentException(
                    "template starterFiles must contain at most 256 files");
        }
        if (template.name() != null && !template.name().equals(name)) {
            throw new IllegalArgumentException("template name does not match its directory");
        }
        if (template.description() != null && template.description().length() > 2048) {
            throw new IllegalArgumentException(
                    "template description must contain at most 2048 characters");
        }
        int vcpu = template.vcpuCount() == null ? 2 : template.vcpuCount();
        long memory = template.memoryMib() == null ? 2048 : template.memoryMib();
        if (vcpu < 1 || vcpu > 64 || memory < 256 || memory > 262144) {
            throw new IllegalArgumentException("template resource limits are invalid");
        }
        if (template.ports() != null) {
            for (int index = 0; index < template.ports().size(); index++) {
                Integer port = template.ports().get(index);
                if (port == null || port < 1 || port > 65535) {
                    throw new IllegalArgumentException(
                            "template ports must be between 1 and 65535");
                }
            }
            Set<Integer> uniquePorts = new HashSet<Integer>(template.ports());
            if (uniquePorts.size() != template.ports().size()) {
                throw new IllegalArgumentException("template ports must be unique");
            }
        }
        if (template.ports() != null
                && template.ports().size() > PortService.MAX_PORTS_PER_WORKSPACE) {
            throw new IllegalArgumentException(
                    "template ports must contain at most "
                            + PortService.MAX_PORTS_PER_WORKSPACE
                            + " entries");
        }
        if (template.languagePacks() != null) {
            List<String> allowed = List.of("cpp", "java", "go", "rust", "typescript", "python");
            if (template.languagePacks().size() > 6) {
                throw new IllegalArgumentException(
                        "template contains an unsupported language pack");
            }
            for (int index = 0; index < template.languagePacks().size(); index++) {
                if (!allowed.contains(template.languagePacks().get(index))) {
                    throw new IllegalArgumentException(
                            "template contains an unsupported language pack");
                }
            }
            Set<String> uniquePacks = new HashSet<String>(template.languagePacks());
            if (uniquePacks.size() != template.languagePacks().size()) {
                throw new IllegalArgumentException("template language packs must be unique");
            }
        }
        validateSetupSteps(template.setupSteps());
        validateRunTasks(template.runTasks(), template.ports());
        Map<String, String> starterFiles =
                template.starterFiles() == null ? Map.of() : template.starterFiles();
        long starterBytes = 0;
        for (Map.Entry<String, String> starter : starterFiles.entrySet()) {
            validateStarter(starter.getKey(), starter.getValue());
            starterBytes += starter.getValue().getBytes(StandardCharsets.UTF_8).length;
            if (starterBytes > MAX_STARTER_BYTES) {
                throw new IllegalArgumentException(
                        "template starter files exceed the 2 MiB total limit");
            }
        }
        UserId owner = userId(principal);
        if (template.ports() != null) {
            for (int port : template.ports()) {
                if (port == 22 || port == 25) {
                    throw new IllegalArgumentException(
                            "port " + port + " is reserved and never exposed");
                }
                if (ports.lookup(owner.value(), port).isPresent()) {
                    throw new IllegalArgumentException(
                            "port " + port + " is already registered for this user");
                }
            }
        }
        validateStarterPaths(starterFiles);
        Workspace workspace =
                workspaces.create(
                        owner, request.workspaceName(), vcpu, memory, new NodeId(defaultNode));
        tasks.initialize(workspace.id(), name);
        for (Map.Entry<String, String> starter : starterFiles.entrySet()) {
            String relativePath = starter.getKey();
            String content = starter.getValue();
            int separator = relativePath.lastIndexOf('/');
            if (separator > 0) {
                files.forWorkspace(workspace.id())
                        .createDirectories(relativePath.substring(0, separator));
            }
            files.forWorkspace(workspace.id()).save(relativePath, content, null, true);
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
            throw new IllegalArgumentException(
                    "template starter path must be a relative workspace path");
        }
        for (String segment : relativePath.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(
                        "template starter path contains an invalid segment");
            }
        }
        if (content == null || content.getBytes(StandardCharsets.UTF_8).length > 256 * 1024) {
            throw new IllegalArgumentException(
                    "template starter file is null or too large: " + relativePath);
        }
    }

    /** Rejects paths that normalize to the same file or try to use a file as a directory. */
    private static void validateStarterPaths(Map<String, String> starterFiles) {
        List<String> normalized = new ArrayList<String>();
        for (String path : starterFiles.keySet()) {
            String candidate = path.replace('\\', '/').toLowerCase(Locale.ROOT);
            for (int index = 0; index < normalized.size(); index++) {
                String existing = normalized.get(index);
                if (candidate.equals(existing)
                        || candidate.startsWith(existing + "/")
                        || existing.startsWith(candidate + "/")) {
                    throw new IllegalArgumentException("template starter paths conflict: " + path);
                }
            }
            normalized.add(candidate);
        }
    }

    private static void validateSetupSteps(List<SetupStep> steps) {
        if (steps == null) return;
        if (steps.size() > 32)
            throw new IllegalArgumentException("template setupSteps must contain at most 32 steps");
        Set<String> ids = new HashSet<String>();
        for (SetupStep step : steps) {
            if (step == null
                    || step.id() == null
                    || !step.id().matches("[a-z0-9][a-z0-9_-]{0,63}")
                    || !ids.add(step.id())) {
                throw new IllegalArgumentException(
                        "template setup step ids must be valid and unique");
            }
            validateCommand(step.command(), "setup step " + step.id());
        }
    }

    private static void validateRunTasks(List<RunTask> tasks, List<Integer> templatePorts) {
        if (tasks == null) return;
        if (tasks.size() > 32)
            throw new IllegalArgumentException("template runTasks must contain at most 32 tasks");
        Set<String> ids = new HashSet<String>();
        for (RunTask task : tasks) {
            if (task == null
                    || task.id() == null
                    || !task.id().matches("[a-z0-9][a-z0-9_-]{0,63}")
                    || !ids.add(task.id())) {
                throw new IllegalArgumentException(
                        "template run task ids must be valid and unique");
            }
            if (task.label() == null || task.label().isBlank() || task.label().length() > 80) {
                throw new IllegalArgumentException(
                        "template run task label must contain 1-80 characters");
            }
            validateCommand(task.command(), "run task " + task.id());
            if (task.port() != null
                    && (task.port() < 1
                            || task.port() > 65535
                            || templatePorts == null
                            || !templatePorts.contains(task.port()))) {
                throw new IllegalArgumentException(
                        "run task port must be declared by the template");
            }
        }
    }

    private static void validateCommand(List<String> command, String source) {
        if (command == null || command.isEmpty() || command.size() > 64) {
            throw new IllegalArgumentException(
                    source + " command must be a non-empty argument list");
        }
        for (int index = 0; index < command.size(); index++) {
            String argument = command.get(index);
            if (argument == null
                    || argument.isBlank()
                    || argument.length() > 1024
                    || argument.indexOf('\0') >= 0) {
                throw new IllegalArgumentException(
                        source + " command contains an invalid argument");
            }
        }
    }

    private TemplateManifest read(String name) {
        try {
            Path manifest = checked(name).resolve("template.json");
            if (!Files.isRegularFile(manifest)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
            }
            return json.treeToValue(
                    json.readTree(Files.readString(manifest)), TemplateManifest.class);
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

    /** The parsed template.json shape. */
    private static final class TemplateManifest {
        private final String name;
        private final String description;
        private final Integer vcpuCount;
        private final Long memoryMib;
        private final List<Integer> ports;
        private final List<String> languagePacks;
        private final Map<String, String> starterFiles;
        private final List<SetupStep> setupSteps;
        private final List<RunTask> runTasks;

        /** Creates a manifest from its JSON representation. */
        @JsonCreator
        TemplateManifest(
                @JsonProperty("name") String name,
                @JsonProperty("description") String description,
                @JsonProperty("vcpuCount") Integer vcpuCount,
                @JsonProperty("memoryMib") Long memoryMib,
                @JsonProperty("ports") List<Integer> ports,
                @JsonProperty("languagePacks") List<String> languagePacks,
                @JsonProperty("starterFiles") Map<String, String> starterFiles,
                @JsonProperty("setupSteps") List<SetupStep> setupSteps,
                @JsonProperty("runTasks") List<RunTask> runTasks) {
            this.name = name;
            this.description = description;
            this.vcpuCount = vcpuCount;
            this.memoryMib = memoryMib;
            this.ports = ports;
            this.languagePacks = languagePacks;
            this.starterFiles = starterFiles;
            this.setupSteps = setupSteps;
            this.runTasks = runTasks;
        }

        /** Returns the template name. */
        public String name() {
            return name;
        }

        /** Returns the description. */
        public String description() {
            return description;
        }

        /** Returns the vCPU count. */
        public Integer vcpuCount() {
            return vcpuCount;
        }

        /** Returns the memory size in MiB. */
        public Long memoryMib() {
            return memoryMib;
        }

        /** Returns the port list. */
        public List<Integer> ports() {
            return ports;
        }

        /** Returns the language pack list. */
        public List<String> languagePacks() {
            return languagePacks;
        }

        /** Returns the starter files. */
        public Map<String, String> starterFiles() {
            return starterFiles;
        }

        /** Returns the setup steps. */
        public List<SetupStep> setupSteps() {
            return setupSteps;
        }

        /** Returns the run tasks. */
        public List<RunTask> runTasks() {
            return runTasks;
        }
    }

    /** A setup step in a template. */
    private static final class SetupStep {
        private final String id;
        private final List<String> command;

        /** Creates a step from its JSON representation. */
        @JsonCreator
        SetupStep(@JsonProperty("id") String id, @JsonProperty("command") List<String> command) {
            this.id = id;
            this.command = command;
        }

        /** Returns the step id. */
        public String id() {
            return id;
        }

        /** Returns the command. */
        public List<String> command() {
            return command;
        }
    }

    /** A run task in a template. */
    private static final class RunTask {
        private final String id;
        private final String label;
        private final List<String> command;
        private final Integer port;

        /** Creates a task from its JSON representation. */
        @JsonCreator
        RunTask(
                @JsonProperty("id") String id,
                @JsonProperty("label") String label,
                @JsonProperty("command") List<String> command,
                @JsonProperty("port") Integer port) {
            this.id = id;
            this.label = label;
            this.command = command;
            this.port = port;
        }

        /** Returns the task id. */
        public String id() {
            return id;
        }

        /** Returns the label. */
        public String label() {
            return label;
        }

        /** Returns the command. */
        public List<String> command() {
            return command;
        }

        /** Returns the port. */
        public Integer port() {
            return port;
        }
    }

    private static UserId userId(Principal principal) {
        try {
            return new UserId(UUID.fromString(principal.getName()));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("principal is not a user id");
        }
    }

    /** Apply request. */
    public static final class ApplyRequest {
        @NotBlank
        @Size(max = 64)
        private String workspaceName;

        /** Creates an empty request for JSON binding. */
        public ApplyRequest() {}

        /** Creates a request. */
        public ApplyRequest(String workspaceName) {
            this.workspaceName = workspaceName;
        }

        /** Returns the workspace name. */
        public String workspaceName() {
            return workspaceName;
        }

        /** Sets the workspace name. */
        public void setWorkspaceName(String workspaceName) {
            this.workspaceName = workspaceName;
        }
    }
}
