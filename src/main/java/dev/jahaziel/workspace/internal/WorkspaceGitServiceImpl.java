/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import dev.jahaziel.platform.Platform;
import dev.jahaziel.platform.Platforms;
import dev.jahaziel.security.api.SecretRedactor;
import dev.jahaziel.workspace.api.WorkspaceGitService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Runs a small fixed set of Git commands against the workspace root. */
@Service
public final class WorkspaceGitServiceImpl implements WorkspaceGitService {
  private static final int MAX_STATUS_BYTES = 1024 * 1024;
  private static final int MAX_DIFF_BYTES = 128 * 1024;
  private static final int MAX_COMMIT_MESSAGE = 512;
  private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(15);

  private final Path storageRoot;

  public WorkspaceGitServiceImpl(@Value("${jahaziel.storage.root:data}") String storageRoot) {
    this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
  }

  @Override
  public Status status(WorkspaceId workspaceId) {
    Path root = workspaceHome(workspaceId);
    if (!isRepository(root)) return new Status(false, "", List.of());

    String branch = command(root, List.of("branch", "--show-current"), 1024).strip();
    if (branch.isEmpty()) branch = "HEAD";
    byte[] porcelain =
        commandBytes(
            root,
            List.of("status", "--porcelain=v1", "-z", "--no-renames", "--untracked-files=all"),
            MAX_STATUS_BYTES);
    String output = new String(porcelain, StandardCharsets.UTF_8);
    List<Change> changes = new ArrayList<>();
    for (String record : output.split("\\u0000", -1)) {
      if (record.length() < 4 || record.charAt(2) != ' ') continue;
      changes.add(new Change(record.substring(3), record.substring(0, 1), record.substring(1, 2)));
    }
    return new Status(true, branch, List.copyOf(changes));
  }

  @Override
  public Diff diff(WorkspaceId workspaceId, String path) {
    Path root = requireRepository(workspaceId);
    String checkedPath = checkedPath(root, path);
    Status current = status(workspaceId);
    boolean untracked =
        current.changes().stream()
            .anyMatch(
                change -> change.path().equals(checkedPath) && change.indexStatus().equals("?"));
    if (untracked) {
      String nullDevice = Platforms.detect(null).os() == Platform.Os.WINDOWS ? "NUL" : "/dev/null";
      CommandResult result =
          commandResult(
              root,
              List.of(
                  "diff",
                  "--no-index",
                  "--no-ext-diff",
                  "--no-textconv",
                  "--no-color",
                  "--",
                  nullDevice,
                  checkedPath),
              MAX_DIFF_BYTES);
      if (result.exitCode() != 0 && result.exitCode() != 1) {
        throw new IllegalStateException(result.output().strip());
      }
      return new Diff(checkedPath, result.output());
    }
    String literalPathspec = ":(top,literal)" + checkedPath;
    if (!hasHead(root)) {
      String staged =
          command(
              root,
              List.of(
                  "diff",
                  "--cached",
                  "--no-ext-diff",
                  "--no-textconv",
                  "--no-color",
                  "--",
                  literalPathspec),
              MAX_DIFF_BYTES);
      String unstaged =
          command(
              root,
              List.of(
                  "diff", "--no-ext-diff", "--no-textconv", "--no-color", "--", literalPathspec),
              MAX_DIFF_BYTES);
      String content =
          (staged.isEmpty() ? "" : "# Staged changes\n" + staged)
              + (unstaged.isEmpty() ? "" : "# Unstaged changes\n" + unstaged);
      return new Diff(checkedPath, content);
    }
    String content =
        command(
            root,
            List.of(
                "diff",
                "HEAD",
                "--no-ext-diff",
                "--no-textconv",
                "--no-color",
                "--no-renames",
                "--",
                literalPathspec),
            MAX_DIFF_BYTES);
    return new Diff(checkedPath, content);
  }

  @Override
  public Status stage(WorkspaceId workspaceId, String path) {
    Path root = requireRepository(workspaceId);
    String checkedPath = checkedPath(root, path);
    command(root, List.of("add", "-A", "--", ":(top,literal)" + checkedPath), MAX_STATUS_BYTES);
    return status(workspaceId);
  }

  @Override
  public Status unstage(WorkspaceId workspaceId, String path) {
    Path root = requireRepository(workspaceId);
    String checkedPath = checkedPath(root, path);
    if (hasHead(root)) {
      command(
          root,
          List.of("restore", "--staged", "--", ":(top,literal)" + checkedPath),
          MAX_STATUS_BYTES);
    } else {
      command(
          root,
          List.of("rm", "--cached", "-r", "--ignore-unmatch", "--", ":(top,literal)" + checkedPath),
          MAX_STATUS_BYTES);
    }
    return status(workspaceId);
  }

  private static boolean hasHead(Path root) {
    return commandResult(root, List.of("rev-parse", "--verify", "HEAD"), 1024).exitCode() == 0;
  }

  @Override
  public CommitResult commit(WorkspaceId workspaceId, String message) {
    if (message == null || message.isBlank() || message.length() > MAX_COMMIT_MESSAGE) {
      throw new IllegalArgumentException("commit message must contain 1 to 512 characters");
    }
    if (message.codePoints().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("commit message cannot contain control characters");
    }
    Path root = requireRepository(workspaceId);
    String summary = command(root, List.of("commit", "-m", message.strip()), MAX_STATUS_BYTES);
    String commit = command(root, List.of("rev-parse", "--short", "HEAD"), 1024).strip();
    return new CommitResult(commit, summary.strip());
  }

  private Path requireRepository(WorkspaceId workspaceId) {
    Path root = workspaceHome(workspaceId);
    if (!isRepository(root))
      throw new IllegalArgumentException("workspace root is not a Git repository");
    return root;
  }

  private boolean isRepository(Path root) {
    CommandResult result = commandResult(root, List.of("rev-parse", "--show-toplevel"), 4096);
    if (result.exitCode() != 0) return false;
    try {
      return Path.of(result.output().strip()).toRealPath().equals(root);
    } catch (IOException | InvalidPathException failure) {
      return false;
    }
  }

  private Path workspaceHome(WorkspaceId workspaceId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    try {
      Path data = storageRoot.toRealPath();
      Path expected =
          data.resolve("workspaces")
              .resolve(workspaceId.value().toString())
              .resolve("home")
              .normalize();
      Path actual = expected.toRealPath();
      if (!actual.equals(expected) || !Files.isDirectory(actual, LinkOption.NOFOLLOW_LINKS)) {
        throw new IllegalArgumentException("workspace storage path is not a plain directory");
      }
      return actual;
    } catch (IOException failure) {
      throw new IllegalArgumentException("workspace home is unavailable", failure);
    }
  }

  private static String checkedPath(Path root, String path) {
    if (path == null
        || path.isBlank()
        || path.length() > 1024
        || path.indexOf('\\') >= 0
        || path.matches("^[A-Za-z]:.*")) {
      throw new IllegalArgumentException("Git path must be a workspace-relative path");
    }
    try {
      Path relative = Path.of(path);
      if (relative.isAbsolute() || relative.getNameCount() == 0) {
        throw new IllegalArgumentException("Git path must be workspace-relative");
      }
      for (Path part : relative) {
        String value = part.toString();
        if (value.isBlank() || value.equals(".") || value.equals("..")) {
          throw new IllegalArgumentException("Git path contains an invalid component");
        }
      }
      Path target = root.resolve(relative).normalize();
      if (!target.startsWith(root))
        throw new IllegalArgumentException("Git path escapes the workspace");
      Path probe = target;
      while (probe != null && probe.startsWith(root)) {
        if (Files.exists(probe, LinkOption.NOFOLLOW_LINKS)) {
          if (!probe.toRealPath().startsWith(root)) {
            throw new IllegalArgumentException(
                "Git path escapes the workspace through a symbolic link");
          }
          break;
        }
        probe = probe.getParent();
      }
      return relative.toString().replace('\\', '/');
    } catch (InvalidPathException | IOException failure) {
      throw new IllegalArgumentException("Git path is invalid", failure);
    }
  }

  private static String command(Path root, List<String> arguments, int maxBytes) {
    CommandResult result = commandResult(root, arguments, maxBytes);
    if (result.exitCode() != 0) {
      String detail = result.output().strip();
      if (detail.length() > 512) detail = detail.substring(0, 512);
      throw new IllegalStateException(detail.isEmpty() ? "Git command failed" : detail);
    }
    return result.output();
  }

  private static byte[] commandBytes(Path root, List<String> arguments, int maxBytes) {
    return command(root, arguments, maxBytes).getBytes(StandardCharsets.UTF_8);
  }

  private static CommandResult commandResult(Path root, List<String> arguments, int maxBytes) {
    List<String> command = new ArrayList<>(arguments.size() + 3);
    command.add("git");
    command.add("-C");
    command.add(root.toString());
    command.add("-c");
    command.add("core.fsmonitor=false");
    command.add("-c");
    command.add("core.hooksPath=" + disabledHooksPath());
    command.addAll(arguments);
    Process process;
    try {
      ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
      configureEnvironment(builder.environment());
      process = builder.start();
    } catch (IOException failure) {
      throw new IllegalStateException("Git is unavailable on the server", failure);
    }

    AtomicReference<Capture> captured = new AtomicReference<>();
    AtomicReference<IOException> readFailure = new AtomicReference<>();
    Thread reader =
        Thread.ofPlatform()
            .daemon()
            .start(
                () -> {
                  try {
                    captured.set(capture(process.getInputStream(), maxBytes));
                  } catch (IOException failure) {
                    readFailure.set(failure);
                  }
                });
    try {
      if (!process.waitFor(
          COMMAND_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
        terminateProcessTree(process);
        reader.join(1000);
        throw new IllegalStateException("Git command timed out");
      }
      reader.join(1000);
      if (reader.isAlive()) {
        terminateProcessTree(process);
        reader.interrupt();
        throw new IllegalStateException("Git output could not be collected");
      }
      if (readFailure.get() != null)
        throw new IllegalStateException("Git output could not be read", readFailure.get());
      Capture output = captured.get();
      if (output == null) throw new IllegalStateException("Git returned no result");
      if (output.truncated())
        throw new IllegalArgumentException("Git output exceeded the workbench limit");
      return new CommandResult(
          process.exitValue(),
          SecretRedactor.redact(new String(output.bytes(), StandardCharsets.UTF_8)));
    } catch (InterruptedException failure) {
      terminateProcessTree(process);
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Git command was interrupted", failure);
    }
  }

  private static void terminateProcessTree(Process process) {
    List<ProcessHandle> descendants = process.descendants().toList();
    descendants.forEach(ProcessHandle::destroy);
    process.destroy();
    try {
      if (!process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) {
        descendants.forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
      }
    } catch (InterruptedException interrupted) {
      descendants.forEach(ProcessHandle::destroyForcibly);
      process.destroyForcibly();
      Thread.currentThread().interrupt();
    }
  }

  private static String disabledHooksPath() {
    return Platforms.detect(null).os() == Platform.Os.WINDOWS ? "NUL" : "/dev/null";
  }

  static void configureEnvironment(Map<String, String> environment) {
    String path = environment.get("PATH");
    environment.clear();
    if (path != null) environment.put("PATH", path);
    environment.put("GIT_TERMINAL_PROMPT", "0");
    environment.put("GIT_CONFIG_NOSYSTEM", "1");
    if (Platforms.detect(null).os() != Platform.Os.WINDOWS) {
      environment.put("GIT_CONFIG_GLOBAL", "/dev/null");
    }
  }

  private static Capture capture(InputStream input, int maxBytes) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
    byte[] buffer = new byte[8192];
    boolean truncated = false;
    int read;
    while ((read = input.read(buffer)) >= 0) {
      int remaining = maxBytes - output.size();
      if (remaining > 0) output.write(buffer, 0, Math.min(remaining, read));
      if (read > remaining) truncated = true;
    }
    return new Capture(output.toByteArray(), truncated);
  }

  private record Capture(byte[] bytes, boolean truncated) {}

  private record CommandResult(int exitCode, String output) {}
}
