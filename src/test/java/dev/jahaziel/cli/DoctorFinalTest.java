/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Direct unit tests for the stray-signal decision plus the REH catch path. */
class DoctorFinalTest {
  @TempDir private Path root;

  private static ProcessHandle process(long pid, Optional<String> commandLine, String command) {
    ProcessHandle handle = mock(ProcessHandle.class);
    when(handle.pid()).thenReturn(pid);
    ProcessHandle.Info info = mock(ProcessHandle.Info.class);
    when(handle.info()).thenReturn(info);
    when(info.commandLine()).thenReturn(commandLine);
    when(info.command()).thenReturn(Optional.ofNullable(command));
    return handle;
  }

  @Test
  void straySignalSkipsSelf() {
    assertTrue(Doctor.straySignal(process(7L, Optional.of("x"), "x"), "x", 7L).isEmpty());
  }

  @Test
  void straySignalMatchesNeedleAndJarName() {
    assertEquals(
        "pid 9",
        Doctor.straySignal(
                process(9L, Optional.of("C:\\opt\\jahaziel\\jahaziel.jar --demo"), "java"),
                "c:\\opt\\jahaziel",
                7L)
            .orElseThrow());
    assertEquals(
        "pid 10",
        Doctor.straySignal(
                process(10L, Optional.of("Anything With JAHAZIEL.JAR inside"), "java"),
                "zzz-no-match",
                7L)
            .orElseThrow());
  }

  @Test
  void straySignalRejectsUnrelated() {
    assertTrue(
        Doctor.straySignal(
                process(11L, Optional.of("C:\\Windows\\explorer.exe"), "explorer"), "c:\\opt", 7L)
            .isEmpty());
  }

  @Test
  void straySignalFallsBackToCommand() {
    assertEquals(
        "pid 12",
        Doctor.straySignal(process(12L, Optional.empty(), "/opt/jahaziel.jar"), "zzz", 7L)
            .orElseThrow());
    assertTrue(
        Doctor.straySignal(process(13L, Optional.empty(), "/usr/bin/java"), "zzz", 7L).isEmpty());
    assertTrue(Doctor.straySignal(process(14L, Optional.empty(), null), "zzz", 7L).isEmpty());
  }

  @Test
  void straySignalMatchesNeedleDirectly() {
    assertTrue(Doctor.matchesStray("C:\\A\\jahaziel.jar", "c:\\a"));
    assertTrue(Doctor.matchesStray("JAHAZIEL.JAR", "zzz"));
    assertFalse(Doctor.matchesStray("plain process", "zzz"));
  }

  @Test
  void rehCatchWhenSlugIsAFile() throws Exception {
    Path jar = root.resolve("jar");
    Files.createDirectories(jar);
    Files.writeString(jar.resolve(".env"), CliTestSupport.validEnv());
    Path data = jar.resolve("data");
    Files.createDirectories(data);
    Path ide = data.resolve("ide");
    Files.createDirectories(ide);
    Files.writeString(ide.resolve(Cli.rehSlug()), "not-a-directory");
    Path tmp = data.resolve("tmp");
    Files.createDirectories(tmp);
    String before = System.getProperty("java.io.tmpdir");
    System.setProperty("java.io.tmpdir", tmp.toString());
    try {
      Doctor.Check check =
          CliTestSupport.find(
              Doctor.run(
                  jar,
                  data,
                  CliTestSupport.freePort(),
                  Cli.parseDotEnv(jar.resolve(".env")),
                  jar.resolve("jahaziel.jar")),
              "reh");
      assertFalse(check.ok());
      assertTrue(check.detail().contains("REH tarball missing: "));
    } finally {
      if (before == null) {
        System.clearProperty("java.io.tmpdir");
      } else {
        System.setProperty("java.io.tmpdir", before);
      }
    }
  }
}
