/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.ide.internal.IdeService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

class IdeCoverageGapTest {
  @TempDir private Path tmp;

  private Path stagedTarball(Path ide) throws Exception {
    Files.createDirectories(ide);
    Path tarball = ide.resolve("jahaziel-code-server-linux-x64.tar.zst");
    byte[] payload = new byte[] {1, 2, 3};
    Files.write(tarball, payload);
    String hex = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
    Files.writeString(tarball.resolveSibling(tarball.getFileName() + ".sha256"), hex);
    return tarball;
  }

  private static void stubStart(ProcessBuilder mock, Process proc) {
    try {
      doReturn(mock).when(mock).inheritIO();
      doReturn(proc).when(mock).start();
    } catch (IOException failure) {
      throw new AssertionError(failure);
    }
  }

  private static void stubStartThrows(ProcessBuilder mock, IOException failure) {
    try {
      doReturn(mock).when(mock).inheritIO();
      doThrow(failure).when(mock).start();
    } catch (IOException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static MockedConstruction<ProcessBuilder> tarConstruction(Process proc) {
    return Mockito.mockConstruction(ProcessBuilder.class, (mock, context) -> stubStart(mock, proc));
  }

  private static MockedConstruction<ProcessBuilder> failingTarConstruction(IOException failure) {
    return Mockito.mockConstruction(
        ProcessBuilder.class, (mock, context) -> stubStartThrows(mock, failure));
  }

  @Test
  void extractionSuccessWritesSentinel() throws Exception {
    Path ide = tmp.resolve("ok");
    stagedTarball(ide);
    IdeService svc = new IdeService(ide.toString(), "1.0", "linux-x64");
    Process proc = mock(Process.class);
    when(proc.waitFor()).thenReturn(0);
    try (var construction = tarConstruction(proc)) {
      Path target = svc.ensureExtracted();
      assertEquals(ide.resolve("linux-x64"), target);
      assertTrue(Files.exists(target.resolve(".extracted-1.0-linux-x64")));
    }
  }

  @Test
  void extractionFailureThrows() throws Exception {
    Path ide = tmp.resolve("fail");
    stagedTarball(ide);
    IdeService svc = new IdeService(ide.toString(), "1.0", "linux-x64");
    Process proc = mock(Process.class);
    when(proc.waitFor()).thenReturn(1);
    try (var construction = tarConstruction(proc)) {
      assertThrows(IllegalStateException.class, svc::ensureExtracted);
    }
  }

  @Test
  void extractionStartFailureWraps() throws Exception {
    Path ide = tmp.resolve("start-fail");
    stagedTarball(ide);
    IdeService svc = new IdeService(ide.toString(), "1.0", "linux-x64");
    try (var construction = failingTarConstruction(new IOException("boom"))) {
      assertThrows(UncheckedIOException.class, svc::ensureExtracted);
    }
  }

  @Test
  void extractionInterruptRestoresFlag() throws Exception {
    Path ide = tmp.resolve("interrupted");
    stagedTarball(ide);
    IdeService svc = new IdeService(ide.toString(), "1.0", "linux-x64");
    Process proc = mock(Process.class);
    doThrow(new InterruptedException("boom")).when(proc).waitFor();
    try (var construction = tarConstruction(proc)) {
      assertThrows(IllegalStateException.class, svc::ensureExtracted);
    }
    assertTrue(Thread.interrupted());
  }

  @Test
  void verifyIoFailureWraps() throws Exception {
    Path ide = tmp.resolve("io");
    stagedTarball(ide);
    IdeService svc = new IdeService(ide.toString(), "1.0", "linux-x64");
    try (var files = Mockito.mockStatic(Files.class)) {
      files.when(() -> Files.exists(any(Path.class))).thenReturn(false);
      files.when(() -> Files.isRegularFile(any(Path.class))).thenReturn(true);
      files.when(() -> Files.readString(any(Path.class))).thenThrow(new IOException("boom"));
      assertThrows(UncheckedIOException.class, svc::ensureExtracted);
    }
  }

  @Test
  void verifyMissingAlgorithmFailsFast() throws Exception {
    Path ide = tmp.resolve("alg");
    stagedTarball(ide);
    IdeService svc = new IdeService(ide.toString(), "1.0", "linux-x64");
    try (var digest = Mockito.mockStatic(MessageDigest.class)) {
      digest
          .when(() -> MessageDigest.getInstance(anyString()))
          .thenThrow(new NoSuchAlgorithmException("boom"));
      assertThrows(IllegalStateException.class, svc::ensureExtracted);
    }
  }
}
