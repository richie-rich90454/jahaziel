/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.workspace.api.WorkspaceGitService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceGitServiceImplTest {
  @TempDir Path temporary;

  private Path data;
  private Path home;
  private WorkspaceId workspaceId;
  private WorkspaceGitService service;

  @BeforeEach
  void setUp() throws Exception {
    data = Files.createDirectories(temporary.resolve("data"));
    workspaceId = new WorkspaceId(UUID.randomUUID());
    home =
        Files.createDirectories(
            data.resolve("workspaces").resolve(workspaceId.value().toString()).resolve("home"));
    service = new WorkspaceGitServiceImpl(data.toString());
    git("init", "--quiet");
    git("config", "user.name", "Workspace Test");
    git("config", "user.email", "workspace@example.test");
  }

  @Test
  void reportsChangesStagesUnstagesDiffsAndCommitsWithinWorkspace() throws Exception {
    Path source = home.resolve("src/main.txt");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "before\n");
    service.stage(workspaceId, "src/main.txt");
    service.commit(workspaceId, "Add initial source");

    Files.writeString(source, "before\nafter\n");
    WorkspaceGitService.Status changed = service.status(workspaceId);
    assertTrue(changed.repository());
    assertEquals("src/main.txt", changed.changes().getFirst().path());
    assertEquals(" ", changed.changes().getFirst().indexStatus());
    assertEquals("M", changed.changes().getFirst().worktreeStatus());
    assertTrue(service.diff(workspaceId, "src/main.txt").content().contains("+after"));

    service.stage(workspaceId, "src/main.txt");
    assertEquals("M", service.status(workspaceId).changes().getFirst().indexStatus());
    service.unstage(workspaceId, "src/main.txt");
    assertEquals(" ", service.status(workspaceId).changes().getFirst().indexStatus());

    service.stage(workspaceId, "src/main.txt");
    WorkspaceGitService.CommitResult commit = service.commit(workspaceId, "Update source");
    assertFalse(commit.commit().isBlank());
    assertTrue(commit.summary().contains("Update source"));
    assertTrue(service.status(workspaceId).changes().isEmpty());
  }

  @Test
  void reportsUntrackedFilesAndRejectsUnsafeGitPaths() throws Exception {
    Files.writeString(home.resolve("notes.txt"), "draft\n");
    String diff = service.diff(workspaceId, "notes.txt").content();
    assertTrue(diff.contains("+++ b/notes.txt"));
    assertTrue(diff.contains("+draft"), diff);
    assertTrue(service.status(workspaceId).changes().getFirst().indexStatus().equals("?"));
    service.stage(workspaceId, "notes.txt");
    assertTrue(service.diff(workspaceId, "notes.txt").content().contains("+draft"));
    service.unstage(workspaceId, "notes.txt");
    assertEquals("?", service.status(workspaceId).changes().getFirst().indexStatus());
    assertThrows(IllegalArgumentException.class, () -> service.stage(workspaceId, "../outside"));
    assertThrows(IllegalArgumentException.class, () -> service.diff(workspaceId, "C:/outside"));
    assertThrows(IllegalArgumentException.class, () -> service.commit(workspaceId, "bad\nmessage"));
  }

  @Test
  void statusMarksAWorkspaceWithoutGitMetadataAsUnavailable() throws Exception {
    Path otherHome =
        Files.createDirectories(
            data.resolve("workspaces").resolve(UUID.randomUUID().toString()).resolve("home"));
    assertTrue(Files.isDirectory(otherHome));
    WorkspaceId otherId =
        new WorkspaceId(UUID.fromString(otherHome.getParent().getFileName().toString()));

    assertFalse(service.status(otherId).repository());
  }

  @Test
  void gitChildEnvironmentDoesNotInheritApplicationSecrets() {
    Map<String, String> environment = new HashMap<>();
    environment.put("PATH", "safe-path");
    environment.put("BASIS_CLIENT_SECRET", "must-not-reach-git");
    environment.put("AWS_SECRET_ACCESS_KEY", "must-not-reach-git");
    environment.put("HOME", "host-home");

    WorkspaceGitServiceImpl.configureEnvironment(environment);

    assertEquals("safe-path", environment.get("PATH"));
    assertEquals("0", environment.get("GIT_TERMINAL_PROMPT"));
    assertEquals("1", environment.get("GIT_CONFIG_NOSYSTEM"));
    if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
      assertFalse(environment.containsKey("GIT_CONFIG_GLOBAL"));
    } else {
      assertEquals("/dev/null", environment.get("GIT_CONFIG_GLOBAL"));
    }
    assertFalse(environment.containsKey("BASIS_CLIENT_SECRET"));
    assertFalse(environment.containsKey("AWS_SECRET_ACCESS_KEY"));
    assertFalse(environment.containsKey("HOME"));
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void panelCommitDoesNotRunRepositoryHooksOrExposeTheirOutput() throws Exception {
    Path hook = home.resolve(".git/hooks/pre-commit");
    Files.writeString(
        hook, "#!/bin/sh\necho token=hook-secret\nprintf x > \"$PWD/hook-ran\"\nexit 1\n");
    Files.setPosixFilePermissions(
        hook,
        EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE));
    Files.writeString(home.resolve("source.txt"), "workspace source\n");
    service.stage(workspaceId, "source.txt");

    WorkspaceGitService.CommitResult commit = service.commit(workspaceId, "Add source");

    assertFalse(Files.exists(home.resolve("hook-ran")));
    assertFalse(commit.summary().contains("hook-secret"));
    assertTrue(service.status(workspaceId).changes().isEmpty());
  }

  @Test
  void refusesToOperateOnARepositoryAboveTheWorkspaceRoot() throws Exception {
    Path nestedHome =
        Files.createDirectories(
            data.resolve("workspaces").resolve(UUID.randomUUID().toString()).resolve("home"));
    Path parentRepository = nestedHome.getParent().getParent();
    git("-C", parentRepository.toString(), "init", "--quiet");
    WorkspaceId nestedId =
        new WorkspaceId(UUID.fromString(nestedHome.getParent().getFileName().toString()));

    assertFalse(service.status(nestedId).repository());
    assertThrows(IllegalArgumentException.class, () -> service.stage(nestedId, "file.txt"));
  }

  private void git(String... arguments) throws Exception {
    java.util.List<String> command = new java.util.ArrayList<>();
    command.add("git");
    command.add("-C");
    command.add(home.toString());
    command.addAll(java.util.List.of(arguments));
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    byte[] output = process.getInputStream().readAllBytes();
    assertEquals(0, process.waitFor(), new String(output, java.nio.charset.StandardCharsets.UTF_8));
  }
}
