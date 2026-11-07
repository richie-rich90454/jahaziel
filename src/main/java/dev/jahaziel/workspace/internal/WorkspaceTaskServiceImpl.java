/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import dev.jahaziel.platform.Platform;
import dev.jahaziel.platform.Platforms;
import dev.jahaziel.security.api.SecretRedactor;
import dev.jahaziel.workspace.api.WorkspaceDirectoryService;
import dev.jahaziel.workspace.api.WorkspaceNotFoundException;
import dev.jahaziel.workspace.api.WorkspaceProcessRegistry;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceState;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Runs only command vectors declared in server-managed template manifests. */
@Service
public final class WorkspaceTaskServiceImpl implements WorkspaceTaskService, ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(WorkspaceTaskServiceImpl.class);
  private static final int MAX_OUTPUT_CHARS = 64 * 1024;
  private static final int MAX_TASKS = 4;
  private static final Duration MAX_RUNTIME = Duration.ofMinutes(30);
  private static final Set<String> LANGUAGE_PACK_IDS =
      Set.of("cpp", "java", "go", "rust", "typescript", "python");

  private final Path storageRoot;
  private final Path templatesRoot;
  private final ObjectMapper json;
  private final WorkspaceService workspaces;
  private final WorkspaceDirectoryService directories;
  private final WorkspaceProcessRegistry processRegistry;
  private final ConcurrentMap<UUID, Process> processes = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, WorkspaceProcessRegistry.Registration> processRegistrations =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Process> setupProcesses = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, WorkspaceProcessRegistry.Registration> setupRegistrations =
      new ConcurrentHashMap<>();
  private final Set<UUID> activeSetups = ConcurrentHashMap.newKeySet();
  private final Set<UUID> cancelledSetups = ConcurrentHashMap.newKeySet();
  private final Set<UUID> stoppingWorkspaces = ConcurrentHashMap.newKeySet();
  private final java.util.Set<UUID> cancelledRuns = ConcurrentHashMap.newKeySet();
  private final ConcurrentMap<UUID, Object> locks = new ConcurrentHashMap<>();

  public WorkspaceTaskServiceImpl(
      @Value("${jahaziel.storage.root:data}") String storageRoot,
      @Value("${jahaziel.templates.dir:data/templates}") String templatesRoot,
      ObjectMapper json,
      WorkspaceService workspaces,
      WorkspaceDirectoryService directories) {
    this(storageRoot, templatesRoot, json, workspaces, directories, null);
  }

  @Autowired
  public WorkspaceTaskServiceImpl(
      @Value("${jahaziel.storage.root:data}") String storageRoot,
      @Value("${jahaziel.templates.dir:data/templates}") String templatesRoot,
      ObjectMapper json,
      WorkspaceService workspaces,
      WorkspaceDirectoryService directories,
      WorkspaceProcessRegistry processRegistry) {
    this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    this.templatesRoot = Path.of(templatesRoot).toAbsolutePath().normalize();
    this.json = Objects.requireNonNull(json, "json");
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    this.directories = Objects.requireNonNull(directories, "directories");
    this.processRegistry = processRegistry;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    Path root = metadataRoot();
    try (var files = Files.list(root)) {
      for (Path file :
          files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
        WorkspaceMetadata metadata = read(file);
        if ("RUNNING".equals(metadata.setupStatus())) {
          UUID correlationId =
              metadata.setupCorrelationId() == null
                  ? UUID.randomUUID()
                  : metadata.setupCorrelationId();
          write(
              file,
              metadata.withSetup(
                  "FAILED",
                  metadata.setupOutput(),
                  "interrupted by server restart",
                  correlationId,
                  Instant.now()));
          log.warn(
              "workspace setup interrupted correlationId={} workspaceId={} reason=server-restarted",
              correlationId,
              metadata.workspaceId());
        }
        if ("RUNNING".equals(metadata.taskStatus())) {
          String output = interruptedTaskOutput(metadata.taskOutput());
          write(
              file,
              metadata.withTask("FAILED", output, null, metadata.taskStartedAt(), Instant.now()));
          log.warn(
              "workspace task interrupted correlationId={} workspaceId={} taskId={} reason=server-restarted",
              metadata.taskRunId(),
              metadata.workspaceId(),
              metadata.taskId());
        }
      }
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot reconcile workspace task state", failure);
    }
  }

  @Override
  public void initialize(WorkspaceId workspaceId, String templateName) {
    JsonNode definition = checkedManifest(templateName);
    readLanguagePacks(definition);
    Path metadataPath = metadataPath(workspaceId);
    synchronized (lock(workspaceId.value())) {
      if (Files.exists(metadataPath, LinkOption.NOFOLLOW_LINKS)) {
        WorkspaceMetadata current = read(metadataPath);
        if (!current.templateName().equals(templateName)) {
          throw new IllegalStateException("workspace is already associated with another template");
        }
        return;
      }
      boolean hasSetup =
          definition.path("setupSteps").isArray() && !definition.path("setupSteps").isEmpty();
      write(
          metadataPath,
          WorkspaceMetadata.initial(
              workspaceId.value(), templateName, hasSetup ? "PENDING" : "READY"));
    }
  }

  @Override
  public SetupStatus setupStatus(WorkspaceId workspaceId) {
    WorkspaceMetadata state = metadataOrNull(workspaceId);
    if (state == null) return new SetupStatus("UNTRACKED", "", "", Instant.EPOCH, null);
    return new SetupStatus(
        state.setupStatus(),
        SecretRedactor.redact(state.setupOutput()),
        SecretRedactor.redact(state.setupError()),
        state.updatedAt(),
        state.setupCorrelationId());
  }

  @Override
  public SetupStatus retrySetup(WorkspaceId workspaceId) {
    List<List<String>> commands = new ArrayList<>();
    UUID correlationId = UUID.randomUUID();
    synchronized (lock(workspaceId.value())) {
      WorkspaceMetadata state = metadataOrNull(workspaceId);
      if (state == null) return new SetupStatus("UNTRACKED", "", "", Instant.EPOCH, null);
      if (activeSetups.contains(workspaceId.value())) return setupStatus(workspaceId);
      if (!"PENDING".equals(state.setupStatus()) && !"FAILED".equals(state.setupStatus())) {
        return setupStatus(workspaceId);
      }
      if (stoppingWorkspaces.contains(workspaceId.value())) {
        return setupRetryBlocked(state);
      }
      if (!isStarted(workspaces.get(workspaceId).state())) {
        return setupRetryBlocked(state);
      }
      JsonNode steps = manifest(state.templateName()).path("setupSteps");
      if (!steps.isArray()) {
        return setupStatus(workspaceId);
      }
      for (JsonNode step : steps) commands.add(command(step.path("command")));
      if (commands.isEmpty()) {
        return setupStatus(workspaceId);
      }
      activeSetups.add(workspaceId.value());
      cancelledSetups.remove(workspaceId.value());
      write(
          metadataPath(workspaceId),
          state.withSetup("RUNNING", "", "", correlationId, Instant.now()));
    }
    log.info("workspace setup started correlationId={} workspaceId={}", correlationId, workspaceId);
    Thread.ofVirtual()
        .name("workspace-setup-" + workspaceId.value())
        .start(
            () -> {
              StringBuilder output = new StringBuilder();
              try {
                for (List<String> command : commands) {
                  if (cancelledSetups.contains(workspaceId.value())) {
                    throw new IllegalStateException("setup stopped because workspace stopped");
                  }
                  runAndCapture(workspaceId, command, output);
                }
                if (cancelledSetups.contains(workspaceId.value())) {
                  throw new IllegalStateException("setup stopped because workspace stopped");
                }
                update(
                    workspaceId,
                    current ->
                        current.withSetup(
                            "READY", SecretRedactor.redact(outputText(output)), "", Instant.now()));
                log.info(
                    "workspace setup completed correlationId={} workspaceId={}",
                    correlationId,
                    workspaceId);
              } catch (RuntimeException failure) {
                String error =
                    cancelledSetups.contains(workspaceId.value())
                        ? "setup stopped because workspace stopped"
                        : safeMessage(failure);
                update(
                    workspaceId,
                    current ->
                        current.withSetup(
                            "FAILED",
                            SecretRedactor.redact(outputText(output)),
                            error,
                            Instant.now()));
                log.warn(
                    "workspace setup failed correlationId={} workspaceId={} reason={}",
                    correlationId,
                    workspaceId,
                    error);
              } finally {
                activeSetups.remove(workspaceId.value());
                cancelledSetups.remove(workspaceId.value());
              }
            });
    return setupStatus(workspaceId);
  }

  private static SetupStatus setupRetryBlocked(WorkspaceMetadata state) {
    return new SetupStatus(
        state.setupStatus(),
        SecretRedactor.redact(state.setupOutput()),
        "workspace must be started before setup can be retried",
        state.updatedAt(),
        state.setupCorrelationId());
  }

  @Override
  public List<Task> tasks(WorkspaceId workspaceId) {
    WorkspaceMetadata state = metadataOrNull(workspaceId);
    if (state == null) return List.of();
    JsonNode definitions = manifest(state.templateName()).path("runTasks");
    if (!definitions.isArray()) return List.of();
    List<Task> result = new ArrayList<>();
    for (JsonNode definition : definitions) {
      result.add(
          new Task(
              requiredId(definition.path("id")),
              requiredLabel(definition.path("label")),
              definition.path("port").isIntegralNumber()
                  ? definition.path("port").intValue()
                  : null));
    }
    return List.copyOf(result);
  }

  @Override
  public List<String> supportedLanguagePacks(WorkspaceId workspaceId) {
    WorkspaceMetadata state = metadataOrNull(workspaceId);
    if (state == null) return List.of();
    return readLanguagePacks(checkedManifest(state.templateName()));
  }

  @Override
  public TaskRun startTask(WorkspaceId workspaceId, String taskId) {
    WorkspaceMetadata state = metadata(workspaceId);
    if (!isStarted(workspaces.get(workspaceId).state())) {
      throw new IllegalStateException("workspace must be started before running a task");
    }
    if (!"READY".equals(state.setupStatus())) {
      throw new IllegalStateException("template setup must finish before running a task");
    }
    JsonNode selected = null;
    for (JsonNode definition : manifest(state.templateName()).path("runTasks")) {
      if (definition.path("id").asString("").equals(taskId)) selected = definition;
    }
    if (selected == null) throw new IllegalArgumentException("unknown workspace task");
    List<String> command = command(selected.path("command"));
    String label = requiredLabel(selected.path("label"));
    synchronized (lock(workspaceId.value())) {
      if (stoppingWorkspaces.contains(workspaceId.value())) {
        throw new IllegalStateException("workspace is stopping");
      }
      WorkspaceMetadata current = metadata(workspaceId);
      if ("RUNNING".equals(current.taskStatus())) {
        throw new IllegalStateException("a workspace task is already running");
      }
      synchronized (processes) {
        if (processes.size() >= MAX_TASKS) throw new IllegalStateException("task capacity is full");
      }
      UUID runId = UUID.randomUUID();
      Instant started = Instant.now();
      write(
          metadataPath(workspaceId),
          current.withRun(runId, taskId, label, "RUNNING", "", null, started, null));
      log.info(
          "workspace task started correlationId={} workspaceId={} taskId={}",
          runId,
          workspaceId,
          taskId);
      Thread.ofVirtual()
          .name("workspace-task-" + runId)
          .start(() -> executeTask(workspaceId, runId, command));
      return taskRunFor(workspaceId, metadata(workspaceId));
    }
  }

  @Override
  public TaskRun taskRun(UUID runId) {
    Path file = metadataPathFromRun(runId);
    if (file == null) throw new WorkspaceNotFoundException(new WorkspaceId(runId));
    WorkspaceMetadata state = read(file);
    if (!runId.equals(state.taskRunId())) throw new IllegalArgumentException("task run not found");
    return taskRunFor(new WorkspaceId(state.workspaceId()), state);
  }

  @Override
  public TaskRun latestTaskRun(WorkspaceId workspaceId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    return taskRunFor(workspaceId, metadataOrNull(workspaceId));
  }

  @Override
  public TaskRun stopTask(WorkspaceId workspaceId, UUID runId) {
    Path file = metadataPathFromRun(runId);
    if (file == null) throw new IllegalArgumentException("task run not found");
    WorkspaceMetadata state = read(file);
    if (!workspaceId.value().equals(state.workspaceId()) || !runId.equals(state.taskRunId())) {
      throw new IllegalArgumentException("task run not found");
    }
    if ("RUNNING".equals(state.taskStatus())) closeTask(runId, "stopped by user");
    return taskRun(runId);
  }

  @Override
  public void closeByWorkspace(WorkspaceId workspaceId) {
    synchronized (lock(workspaceId.value())) {
      stoppingWorkspaces.add(workspaceId.value());
      closeSetup(workspaceId.value(), "workspace stopped");
      for (Path file : metadataFiles()) {
        WorkspaceMetadata state = read(file);
        if (workspaceId.value().equals(state.workspaceId())
            && "RUNNING".equals(state.taskStatus())
            && state.taskRunId() != null) {
          closeTask(state.taskRunId(), "workspace stopped");
        }
      }
    }
  }

  @Override
  public void allowByWorkspace(WorkspaceId workspaceId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    synchronized (lock(workspaceId.value())) {
      stoppingWorkspaces.remove(workspaceId.value());
    }
  }

  @Override
  @PreDestroy
  public void closeAll() {
    for (UUID workspaceId : List.copyOf(activeSetups)) {
      closeSetup(workspaceId, "server shutting down");
    }
    for (Path file : metadataFiles()) {
      WorkspaceMetadata state = read(file);
      if ("RUNNING".equals(state.taskStatus()) && state.taskRunId() != null) {
        closeTask(state.taskRunId(), "server shutting down");
      }
    }
  }

  private void closeTask(UUID runId, String reason) {
    cancelledRuns.add(runId);
    Process process = processes.remove(runId);
    WorkspaceProcessRegistry.Registration registration = processRegistrations.remove(runId);
    if (process != null) {
      process.descendants().forEach(ProcessHandle::destroy);
      process.destroy();
      try {
        if (!process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) {
          process.descendants().forEach(ProcessHandle::destroyForcibly);
          process.destroyForcibly();
        }
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        process.destroyForcibly();
      }
    }
    if (registration != null) registration.close();
    for (Path file : metadataFiles()) {
      WorkspaceMetadata state = read(file);
      if (runId.equals(state.taskRunId()) && "RUNNING".equals(state.taskStatus())) {
        write(
            file,
            state.withTask(
                "STOPPED",
                state.taskOutput() + "\n[task] " + reason,
                safeExitOrNull(process),
                state.taskStartedAt(),
                Instant.now()));
        log.info(
            "workspace task stopped correlationId={} workspaceId={} reason={}",
            runId,
            state.workspaceId(),
            SecretRedactor.redact(reason));
        return;
      }
    }
  }

  private void closeSetup(UUID workspaceId, String reason) {
    if (!activeSetups.contains(workspaceId)) return;
    cancelledSetups.add(workspaceId);
    Process process = setupProcesses.get(workspaceId);
    if (process != null) stopProcessTree(process);
    WorkspaceProcessRegistry.Registration registration = setupRegistrations.get(workspaceId);
    if (registration != null) registration.close();
    synchronized (lock(workspaceId)) {
      WorkspaceMetadata state = metadataOrNull(new WorkspaceId(workspaceId));
      if (state != null && "RUNNING".equals(state.setupStatus())) {
        write(
            metadataPath(new WorkspaceId(workspaceId)),
            state.withSetup(
                "FAILED",
                SecretRedactor.redact(state.setupOutput()),
                "setup interrupted: " + reason,
                Instant.now()));
      }
    }
  }

  private static void stopProcessTree(Process process) {
    List<ProcessHandle> descendants = process.descendants().toList();
    descendants.forEach(ProcessHandle::destroy);
    process.destroy();
    try {
      process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
    descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    if (process.isAlive()) process.destroyForcibly();
  }

  private void executeTask(WorkspaceId workspaceId, UUID runId, List<String> command) {
    Process process = null;
    WorkspaceProcessRegistry.Registration registration = null;
    StringBuilder output = new StringBuilder();
    try {
      process = start(workspaceId, command);
      if (processRegistry != null) {
        registration =
            processRegistry.track(workspaceId, WorkspaceProcessRegistry.Kind.TASK, process);
      }
      Process running = process;
      synchronized (processes) {
        if (cancelledRuns.contains(runId)) {
          process.destroyForcibly();
          if (registration != null) registration.close();
          return;
        }
        if (processes.size() >= MAX_TASKS) {
          process.destroyForcibly();
          if (registration != null) registration.close();
          throw new IllegalStateException("task capacity is full");
        }
        processes.put(runId, process);
        if (registration != null) processRegistrations.put(runId, registration);
      }
      Thread reader =
          Thread.ofVirtual()
              .name("workspace-task-output-" + runId)
              .start(() -> readOutput(running, output, workspaceId, runId));
      boolean ended =
          process.waitFor(MAX_RUNTIME.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
      if (!ended) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroyForcibly();
        throw new IllegalStateException("task exceeded the 30 minute runtime limit");
      }
      reader.join(1000);
      if (cancelledRuns.contains(runId)) return;
      int exitCode = process.exitValue();
      String finalOutput;
      synchronized (output) {
        finalOutput = output.toString();
      }
      update(
          workspaceId,
          current ->
              current.withTask(
                  exitCode == 0 ? "SUCCEEDED" : "FAILED",
                  SecretRedactor.redact(finalOutput),
                  exitCode,
                  current.taskStartedAt(),
                  Instant.now()));
      log.info(
          "workspace task completed correlationId={} workspaceId={} exitCode={}",
          runId,
          workspaceId,
          exitCode);
    } catch (Exception failure) {
      if (cancelledRuns.contains(runId)) return;
      Integer exitCode = process == null ? null : safeExit(process);
      String captured;
      synchronized (output) {
        captured = output.toString();
      }
      update(
          workspaceId,
          current ->
              current.withTask(
                  "FAILED",
                  SecretRedactor.redact(captured),
                  exitCode,
                  current.taskStartedAt(),
                  Instant.now()));
      log.warn(
          "workspace task failed correlationId={} workspaceId={} reason={}",
          runId,
          workspaceId,
          safeMessage(failure));
    } finally {
      processes.remove(runId);
      processRegistrations.remove(runId);
      cancelledRuns.remove(runId);
      if (process != null && process.isAlive()) process.destroyForcibly();
      if (registration != null) registration.close();
    }
  }

  private void runAndCapture(WorkspaceId workspaceId, List<String> command, StringBuilder output) {
    UUID workspaceKey = workspaceId.value();
    if (cancelledSetups.contains(workspaceKey)) {
      throw new IllegalStateException("setup stopped because workspace stopped");
    }
    Process process = start(workspaceId, command);
    setupProcesses.put(workspaceKey, process);
    WorkspaceProcessRegistry.Registration registration = null;
    try {
      if (processRegistry != null) {
        registration =
            processRegistry.track(workspaceId, WorkspaceProcessRegistry.Kind.SETUP, process);
        setupRegistrations.put(workspaceKey, registration);
      }
      if (cancelledSetups.contains(workspaceKey)) {
        stopProcessTree(process);
        throw new IllegalStateException("setup stopped because workspace stopped");
      }
      Thread reader =
          Thread.ofVirtual()
              .name("workspace-setup-output-" + process.pid())
              .start(() -> readInto(process, output, workspaceId));
      if (!process.waitFor(MAX_RUNTIME.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
        stopProcessTree(process);
        throw new IllegalStateException("setup step exceeded the 30 minute runtime limit");
      }
      reader.join(1000);
      if (cancelledSetups.contains(workspaceKey)) {
        throw new IllegalStateException("setup stopped because workspace stopped");
      }
      if (process.exitValue() != 0)
        throw new IllegalStateException("setup step exited with status " + process.exitValue());
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      stopProcessTree(process);
      throw new IllegalStateException("setup was interrupted", failure);
    } finally {
      setupProcesses.remove(workspaceKey, process);
      if (registration != null) setupRegistrations.remove(workspaceKey, registration);
      if (process.isAlive()) stopProcessTree(process);
      if (registration != null) registration.close();
    }
  }

  private Process start(WorkspaceId workspaceId, List<String> command) {
    if (Platforms.detect(null).os() != Platform.Os.LINUX) {
      throw new IllegalStateException("workspace tasks require a Linux server");
    }
    Path home = directories.home(workspaceId);
    List<String> managedCommand =
        processRegistry == null ? command : processRegistry.dedicatedSessionCommand(command);
    ProcessBuilder builder =
        new ProcessBuilder(managedCommand).directory(home.toFile()).redirectErrorStream(true);
    Map<String, String> environment = builder.environment();
    environment.clear();
    environment.put("HOME", home.toString());
    environment.put("PATH", "/usr/local/bin:/usr/bin:/bin");
    environment.put("TERM", "xterm-256color");
    environment.put("LANG", "C.UTF-8");
    try {
      return builder.start();
    } catch (IOException failure) {
      throw new IllegalStateException(
          "task executable is unavailable on this Linux server", failure);
    }
  }

  private void readOutput(
      Process process, StringBuilder output, WorkspaceId workspaceId, UUID runId) {
    Instant lastWrite = Instant.EPOCH;
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      char[] chunk = new char[2048];
      int count;
      while ((count = reader.read(chunk)) >= 0) {
        if (count == 0) continue;
        synchronized (output) {
          output.append(chunk, 0, count);
          if (output.length() > MAX_OUTPUT_CHARS)
            output.delete(0, output.length() - MAX_OUTPUT_CHARS);
        }
        if (Duration.between(lastWrite, Instant.now()).toMillis() >= 250) {
          persistTaskOutput(workspaceId, output);
          lastWrite = Instant.now();
        }
      }
    } catch (IOException ignored) {
      // Process termination may close its stream between reads.
    }
    persistTaskOutput(workspaceId, output);
  }

  private void persistTaskOutput(WorkspaceId workspaceId, StringBuilder output) {
    String captured;
    synchronized (output) {
      captured = output.toString();
    }
    update(
        workspaceId,
        current ->
            current.withTask(
                current.taskStatus(),
                SecretRedactor.redact(captured),
                current.taskExitCode(),
                current.taskStartedAt(),
                Instant.now()));
  }

  private void readInto(Process process, StringBuilder output, WorkspaceId workspaceId) {
    Instant lastWrite = Instant.EPOCH;
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      char[] chunk = new char[2048];
      int count;
      while ((count = reader.read(chunk)) >= 0) {
        if (count == 0) continue;
        synchronized (output) {
          output.append(chunk, 0, count);
          if (output.length() > MAX_OUTPUT_CHARS)
            output.delete(0, output.length() - MAX_OUTPUT_CHARS);
        }
        if (Duration.between(lastWrite, Instant.now()).toMillis() >= 250) {
          persistSetupOutput(workspaceId, output);
          lastWrite = Instant.now();
        }
      }
    } catch (IOException ignored) {
      // Process termination may close its stream between reads.
    }
    persistSetupOutput(workspaceId, output);
  }

  private void persistSetupOutput(WorkspaceId workspaceId, StringBuilder output) {
    String captured = outputText(output);
    update(
        workspaceId,
        current ->
            current.withSetup(
                current.setupStatus(),
                SecretRedactor.redact(captured),
                current.setupError(),
                Instant.now()));
  }

  private static String outputText(StringBuilder output) {
    synchronized (output) {
      return output.toString();
    }
  }

  private Path metadataRoot() {
    try {
      Files.createDirectories(storageRoot);
      Path realRoot = storageRoot.toRealPath();
      Path state = realRoot.resolve("workspace-state");
      ensurePlainDirectory(state);
      return state;
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot create workspace state directory", failure);
    }
  }

  private Path metadataPath(WorkspaceId id) {
    return metadataRoot().resolve(id.value() + ".json");
  }

  private Path metadataPathFromRun(UUID runId) {
    for (Path file : metadataFiles()) {
      if (runId.equals(read(file).taskRunId())) return file;
    }
    return null;
  }

  private List<Path> metadataFiles() {
    try (var files = Files.list(metadataRoot())) {
      return files.filter(path -> path.toString().endsWith(".json")).toList();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private WorkspaceMetadata metadata(WorkspaceId id) {
    Path file = metadataPath(id);
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
      throw new IllegalArgumentException("workspace has no template metadata");
    WorkspaceMetadata state = read(file);
    return new WorkspaceMetadata(
        id.value(),
        state.templateName(),
        state.setupStatus(),
        state.setupOutput(),
        state.setupError(),
        state.setupCorrelationId(),
        state.taskRunId(),
        state.taskId(),
        state.taskLabel(),
        state.taskStatus(),
        SecretRedactor.redact(state.taskOutput()),
        state.taskExitCode(),
        state.taskStartedAt(),
        state.taskFinishedAt(),
        state.updatedAt());
  }

  private WorkspaceMetadata metadataOrNull(WorkspaceId id) {
    Path file = metadataPath(id);
    return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? metadata(id) : null;
  }

  private void update(WorkspaceId id, java.util.function.UnaryOperator<WorkspaceMetadata> change) {
    synchronized (lock(id.value())) {
      Path path = metadataPath(id);
      write(path, change.apply(metadata(id)));
    }
  }

  private JsonNode checkedManifest(String name) {
    if (name == null || !name.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}"))
      throw new IllegalArgumentException("invalid template name");
    Path dir = templatesRoot.resolve(name).normalize();
    try {
      if (!dir.startsWith(templatesRoot)
          || Files.isSymbolicLink(dir)
          || !dir.toRealPath().startsWith(templatesRoot.toRealPath())) {
        throw new IllegalArgumentException("invalid template location");
      }
      return manifest(name);
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot read server-managed template", failure);
    }
  }

  private JsonNode manifest(String name) {
    try {
      return json.readTree(Files.readString(templatesRoot.resolve(name).resolve("template.json")));
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot read server-managed template", failure);
    }
  }

  private static List<String> readLanguagePacks(JsonNode definition) {
    JsonNode packs = definition.get("languagePacks");
    if (packs == null) return List.of();
    if (!packs.isArray() || packs.size() > LANGUAGE_PACK_IDS.size()) {
      throw new IllegalArgumentException("template languagePacks must be a supported list");
    }
    List<String> result = new ArrayList<>();
    Set<String> unique = new java.util.HashSet<>();
    for (JsonNode pack : packs) {
      if (!pack.isTextual()
          || !LANGUAGE_PACK_IDS.contains(pack.asString())
          || !unique.add(pack.asString())) {
        throw new IllegalArgumentException("template contains an invalid language pack");
      }
      result.add(pack.asString());
    }
    return List.copyOf(result);
  }

  private static List<String> command(JsonNode node) {
    if (!node.isArray() || node.isEmpty() || node.size() > 64)
      throw new IllegalArgumentException("template command must be a non-empty argument list");
    List<String> args = new ArrayList<>();
    for (JsonNode item : node) {
      if (!item.isTextual()
          || item.asString().isBlank()
          || item.asString().length() > 1024
          || item.asString().indexOf('\0') >= 0) {
        throw new IllegalArgumentException("template command contains an invalid argument");
      }
      args.add(item.asString());
    }
    return List.copyOf(args);
  }

  private static String requiredId(JsonNode node) {
    String value = node.asString("");
    if (!value.matches("[a-z0-9][a-z0-9_-]{0,63}"))
      throw new IllegalArgumentException("template task has an invalid id");
    return value;
  }

  private static String requiredLabel(JsonNode node) {
    String value = node.asString("");
    if (value.isBlank() || value.length() > 80)
      throw new IllegalArgumentException("template task has an invalid label");
    return value;
  }

  private static void ensurePlainDirectory(Path path) throws IOException {
    if (Files.isSymbolicLink(path))
      throw new IllegalArgumentException("workspace storage path contains a symbolic link");
    Files.createDirectories(path);
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      throw new IllegalArgumentException("workspace storage path is not a plain directory");
  }

  private WorkspaceMetadata read(Path path) {
    try {
      return json.readValue(Files.readString(path), WorkspaceMetadata.class);
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot read workspace task state", failure);
    }
  }

  private void write(Path path, WorkspaceMetadata state) {
    Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
    try {
      Files.writeString(temporary, json.writeValueAsString(state), StandardCharsets.UTF_8);
      try {
        Files.move(
            temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot persist workspace task state", failure);
    }
  }

  private Object lock(UUID id) {
    return locks.computeIfAbsent(id, ignored -> new Object());
  }

  private TaskRun taskRunFor(WorkspaceId workspaceId, WorkspaceMetadata state) {
    if (state == null || state.taskRunId() == null) return null;
    return new TaskRun(
        state.taskRunId(),
        workspaceId.value(),
        state.taskId(),
        state.taskLabel(),
        state.taskStatus(),
        SecretRedactor.redact(state.taskOutput()),
        state.taskExitCode(),
        state.taskStartedAt(),
        state.taskFinishedAt());
  }

  private static String interruptedTaskOutput(String existing) {
    String reason = "Task was interrupted by a server restart.";
    String output = existing == null || existing.isBlank() ? reason : existing + "\n" + reason;
    if (output.length() > MAX_OUTPUT_CHARS) {
      output = output.substring(output.length() - MAX_OUTPUT_CHARS);
    }
    return SecretRedactor.redact(output);
  }

  private static String safeMessage(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank() ? "setup failed" : SecretRedactor.redact(message);
  }

  private static Integer safeExit(Process process) {
    return process.isAlive() ? null : process.exitValue();
  }

  private static Integer safeExitOrNull(Process process) {
    return process == null ? null : safeExit(process);
  }

  private static boolean isStarted(WorkspaceState state) {
    return state instanceof WorkspaceState.Ready
        || state instanceof WorkspaceState.Active
        || state instanceof WorkspaceState.Idle;
  }

  private record WorkspaceMetadata(
      UUID workspaceId,
      String templateName,
      String setupStatus,
      String setupOutput,
      String setupError,
      UUID setupCorrelationId,
      UUID taskRunId,
      String taskId,
      String taskLabel,
      String taskStatus,
      String taskOutput,
      Integer taskExitCode,
      Instant taskStartedAt,
      Instant taskFinishedAt,
      Instant updatedAt) {
    static WorkspaceMetadata initial(UUID workspaceId, String template, String setupStatus) {
      Instant now = Instant.now();
      return new WorkspaceMetadata(
          workspaceId,
          template,
          setupStatus,
          "",
          "",
          null,
          null,
          null,
          null,
          "IDLE",
          "",
          null,
          null,
          null,
          now);
    }

    WorkspaceMetadata withSetup(String status, String output, String error, Instant updated) {
      return withSetup(status, output, error, setupCorrelationId, updated);
    }

    WorkspaceMetadata withSetup(
        String status, String output, String error, UUID correlationId, Instant updated) {
      return new WorkspaceMetadata(
          workspaceId,
          templateName,
          status,
          output,
          error,
          correlationId,
          taskRunId,
          taskId,
          taskLabel,
          taskStatus,
          taskOutput,
          taskExitCode,
          taskStartedAt,
          taskFinishedAt,
          updated);
    }

    WorkspaceMetadata withRun(
        UUID run,
        String id,
        String label,
        String status,
        String output,
        Integer exit,
        Instant started,
        Instant finished) {
      return new WorkspaceMetadata(
          workspaceId,
          templateName,
          setupStatus,
          setupOutput,
          setupError,
          setupCorrelationId,
          run,
          id,
          label,
          status,
          output,
          exit,
          started,
          finished,
          Instant.now());
    }

    WorkspaceMetadata withTask(
        String status, String output, Integer exit, Instant started, Instant finished) {
      return new WorkspaceMetadata(
          workspaceId,
          templateName,
          setupStatus,
          setupOutput,
          setupError,
          setupCorrelationId,
          taskRunId,
          taskId,
          taskLabel,
          status,
          output,
          exit,
          started,
          finished,
          Instant.now());
    }
  }
}
