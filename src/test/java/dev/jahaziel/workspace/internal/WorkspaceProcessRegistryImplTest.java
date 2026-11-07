/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.workspace.api.WorkspaceProcessRegistry;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationArguments;

class WorkspaceProcessRegistryImplTest {
  @TempDir Path temp;

  @Test
  void restartRecoveryStopsOnlyTheProcessWithTheRecordedIdentity() throws Exception {
    WorkspaceProcessRegistryImpl previousServer = new WorkspaceProcessRegistryImpl(temp.toString());
    Path java = javaExecutable();
    Path testClasses =
        Path.of(
            WorkspaceProcessRegistryImplTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    Process child =
        new ProcessBuilder(
                previousServer.dedicatedSessionCommand(
                    List.of(
                        java.toString(),
                        "-cp",
                        testClasses.toString(),
                        ChildProcess.class.getName())))
            .start();
    WorkspaceProcessRegistry.Registration registration = null;
    try {
      awaitProcessIdentity(child);
      registration =
          previousServer.track(
              new WorkspaceId(UUID.randomUUID()), WorkspaceProcessRegistry.Kind.TASK, child);
      Path marker = temp.resolve("managed-processes").resolve(registration.id() + ".process");
      assertTrue(Files.isRegularFile(marker));

      new WorkspaceProcessRegistryImpl(temp.toString()).run((ApplicationArguments) null);

      assertTrue(
          child.waitFor(2, TimeUnit.SECONDS), "restart recovery should stop the matching process");
      assertFalse(Files.exists(marker), "recovered marker should be removed");
    } finally {
      if (registration != null) registration.close();
      if (child.isAlive()) child.destroyForcibly();
    }
  }

  @Test
  void mismatchedStartTimeRemovesMarkerWithoutStoppingRecycledPid() throws Exception {
    WorkspaceProcessRegistryImpl previousServer = new WorkspaceProcessRegistryImpl(temp.toString());
    Path java = javaExecutable();
    Path testClasses =
        Path.of(
            WorkspaceProcessRegistryImplTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    Process child =
        new ProcessBuilder(
                previousServer.dedicatedSessionCommand(
                    List.of(
                        java.toString(),
                        "-cp",
                        testClasses.toString(),
                        ChildProcess.class.getName())))
            .start();
    WorkspaceProcessRegistry.Registration registration = null;
    try {
      awaitProcessIdentity(child);
      registration =
          previousServer.track(
              new WorkspaceId(UUID.randomUUID()), WorkspaceProcessRegistry.Kind.TERMINAL, child);
      Path marker = temp.resolve("managed-processes").resolve(registration.id() + ".process");
      String contents = Files.readString(marker);
      Instant started = child.toHandle().info().startInstant().orElseThrow();
      String wrongStart = started.plusSeconds(10).toString();
      contents = contents.replaceFirst("(?m)^started=.*$", "started=" + wrongStart);
      assertTrue(
          contents.contains("started=" + wrongStart), "test must change the recorded start time");
      Files.writeString(marker, contents);

      new WorkspaceProcessRegistryImpl(temp.toString()).run((ApplicationArguments) null);

      assertTrue(child.isAlive(), "a recycled PID with a different start time must survive");
      assertFalse(Files.exists(marker), "mismatched marker should be discarded");
    } finally {
      if (registration != null) registration.close();
      child.destroyForcibly();
      child.waitFor(2, TimeUnit.SECONDS);
    }
  }

  @Test
  void mismatchedSessionMarkerIsDiscardedWithoutStoppingTheProcess() throws Exception {
    WorkspaceProcessRegistryImpl previousServer = new WorkspaceProcessRegistryImpl(temp.toString());
    Path java = javaExecutable();
    Path testClasses =
        Path.of(
            WorkspaceProcessRegistryImplTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    Process child =
        new ProcessBuilder(
                previousServer.dedicatedSessionCommand(
                    List.of(
                        java.toString(),
                        "-cp",
                        testClasses.toString(),
                        ChildProcess.class.getName())))
            .start();
    WorkspaceProcessRegistry.Registration registration = null;
    try {
      awaitProcessIdentity(child);
      registration =
          previousServer.track(
              new WorkspaceId(UUID.randomUUID()), WorkspaceProcessRegistry.Kind.TASK, child);
      Path marker = temp.resolve("managed-processes").resolve(registration.id() + ".process");
      String contents = Files.readString(marker);
      contents = contents.replaceFirst("(?m)^session=.*$", "session=999999999");
      Files.writeString(marker, contents);

      new WorkspaceProcessRegistryImpl(temp.toString()).run((ApplicationArguments) null);

      assertTrue(child.isAlive(), "a marker with a different session must not kill the process");
      assertFalse(Files.exists(marker), "mismatched marker should be discarded");
    } finally {
      if (registration != null) registration.close();
      child.destroyForcibly();
      child.waitFor(2, TimeUnit.SECONDS);
    }
  }

  @Test
  void restartRecoveryStopsOrphanedChildrenFromTheRecordedLinuxSession() throws Exception {
    Assumptions.assumeTrue(isLinux(), "Linux sessions are required for this recovery check");
    WorkspaceProcessRegistryImpl previousServer = new WorkspaceProcessRegistryImpl(temp.toString());
    Path java = javaExecutable();
    Path testClasses =
        Path.of(
            WorkspaceProcessRegistryImplTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    Path childPidFile = temp.resolve("orphan.pid");
    Path readyFile = temp.resolve("parent.ready");
    Path releaseFile = temp.resolve("parent.release");
    Process parent =
        new ProcessBuilder(
                previousServer.dedicatedSessionCommand(
                    List.of(
                        java.toString(),
                        "-cp",
                        testClasses.toString(),
                        ChildProcess.class.getName(),
                        "parent",
                        childPidFile.toString(),
                        readyFile.toString(),
                        releaseFile.toString())))
            .start();
    WorkspaceProcessRegistry.Registration registration = null;
    try {
      awaitFile(readyFile);
      long orphanPid = Long.parseLong(Files.readString(childPidFile));
      registration =
          previousServer.track(
              new WorkspaceId(UUID.randomUUID()), WorkspaceProcessRegistry.Kind.TASK, parent);
      Path marker = temp.resolve("managed-processes").resolve(registration.id() + ".process");
      Files.writeString(releaseFile, "exit");
      assertTrue(parent.waitFor(5, TimeUnit.SECONDS), "session leader should exit for this check");
      assertTrue(ProcessHandle.of(orphanPid).map(ProcessHandle::isAlive).orElse(false));

      new WorkspaceProcessRegistryImpl(temp.toString()).run((ApplicationArguments) null);

      assertFalse(ProcessHandle.of(orphanPid).map(ProcessHandle::isAlive).orElse(false));
      assertFalse(Files.exists(marker));
    } finally {
      if (registration != null) registration.close();
      if (parent.isAlive()) parent.destroyForcibly();
    }
  }

  private static Path javaExecutable() {
    String executable =
        System.getProperty("os.name", "").toLowerCase().contains("windows") ? "java.exe" : "java";
    return Path.of(System.getProperty("java.home"), "bin", executable);
  }

  private static boolean isLinux() {
    return System.getProperty("os.name", "").toLowerCase().contains("linux");
  }

  private static void awaitFile(Path file) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      if (Files.isRegularFile(file)) return;
      Thread.sleep(10);
    }
    throw new AssertionError("child process did not become ready");
  }

  private static void awaitProcessIdentity(Process process) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      if (!process.isAlive()) throw new AssertionError("test child process exited early");
      if (process.toHandle().info().startInstant().isPresent()
          && process.toHandle().info().command().isPresent()) return;
      Thread.sleep(10);
    }
    throw new AssertionError("test child process identity did not become available");
  }

  /** Child JVM used to verify recovery without targeting a process outside the test. */
  public static final class ChildProcess {
    private ChildProcess() {}

    public static void main(String[] args) throws Exception {
      if (args.length == 4 && args[0].equals("parent")) {
        Path testClasses =
            Path.of(ChildProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Process child =
            new ProcessBuilder(
                    javaExecutable().toString(),
                    "-cp",
                    testClasses.toString(),
                    ChildProcess.class.getName())
                .start();
        Files.writeString(Path.of(args[1]), Long.toString(child.pid()));
        Files.writeString(Path.of(args[2]), "ready");
        Path release = Path.of(args[3]);
        while (!Files.exists(release)) Thread.sleep(10);
        return;
      }
      Thread.sleep(TimeUnit.MINUTES.toMillis(2));
    }
  }
}
