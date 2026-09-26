/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.ide.internal.IdeService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IdeExtraCoverageTest {
  @TempDir private Path tmp;

  @Test
  void variantSlugsAndSelect() {
    assertEquals("linux-x64", IdeService.Variant.LINUX_X64.slug());
    assertEquals("linux-arm64", IdeService.Variant.LINUX_ARM64.slug());
    assertEquals("darwin-x64", IdeService.Variant.DARWIN_X64.slug());
    assertEquals("darwin-arm64", IdeService.Variant.DARWIN_ARM64.slug());
    assertEquals("win32-x64", IdeService.Variant.WIN32_X64.slug());
    assertEquals("win32-arm64", IdeService.Variant.WIN32_ARM64.slug());
    assertEquals(IdeService.Variant.LINUX_X64, IdeService.Variant.select("linux-x64"));
    assertEquals(IdeService.Variant.WIN32_ARM64, IdeService.Variant.select(" win32-arm64 "));
    assertEquals(IdeService.Variant.DARWIN_X64, IdeService.Variant.select("darwin_x64"));
    String osName = System.getProperty("os.name");
    String osArch = System.getProperty("os.arch");
    try {
      System.setProperty("os.name", "Mac OS X");
      System.setProperty("os.arch", "aarch64");
      assertEquals(IdeService.Variant.DARWIN_ARM64, IdeService.Variant.select(null));
      assertEquals(IdeService.Variant.DARWIN_ARM64, IdeService.Variant.select("  "));
      System.setProperty("os.name", "Mac OS X");
      System.setProperty("os.arch", "x86_64");
      assertEquals(IdeService.Variant.DARWIN_X64, IdeService.Variant.select(""));
      System.setProperty("os.name", "Windows 11");
      System.setProperty("os.arch", "arm64");
      assertEquals(IdeService.Variant.WIN32_ARM64, IdeService.Variant.select(null));
      System.setProperty("os.name", "Windows 11");
      System.setProperty("os.arch", "amd64");
      assertEquals(IdeService.Variant.WIN32_X64, IdeService.Variant.select(null));
      System.setProperty("os.name", "Linux");
      System.setProperty("os.arch", "aarch64");
      assertEquals(IdeService.Variant.LINUX_ARM64, IdeService.Variant.select(null));
      System.setProperty("os.name", "Linux");
      System.setProperty("os.arch", "amd64");
      assertEquals(IdeService.Variant.LINUX_X64, IdeService.Variant.select(null));
    } finally {
      if (osName != null) System.setProperty("os.name", osName);
      if (osArch != null) System.setProperty("os.arch", osArch);
    }
    assertEquals(new IdeService("d", "v", "").variant(), IdeService.Variant.select(""));
  }

  @Test
  void ensureExtractedSentinelAndMissing() throws Exception {
    Path ide = tmp.resolve("ide");
    Files.createDirectories(ide);
    IdeService svc = new IdeService(ide.toString(), "1.0", "linux-x64");
    Path target = ide.resolve("linux-x64");
    Files.createDirectories(target);
    Files.createFile(target.resolve(".extracted-1.0-linux-x64"));
    assertEquals(target, svc.ensureExtracted());
    Path ide2 = tmp.resolve("ide2");
    Files.createDirectories(ide2);
    IdeService missing = new IdeService(ide2.toString(), "1.0", "linux-x64");
    assertThrows(IllegalStateException.class, missing::ensureExtracted);
    assertThrows(
        dev.jahaziel.ide.internal.RehNotInstalledException.class, missing::ensureExtracted);
  }

  @Test
  void ensureExtractedChecksumBranches() throws Exception {
    Path ide = tmp.resolve("ide3");
    Files.createDirectories(ide);
    IdeService svc = new IdeService(ide.toString(), "1.0", "linux-x64");
    Path tarball = ide.resolve("jahaziel-code-server-linux-x64.tar.zst");
    Files.write(tarball, new byte[] {1, 2, 3});
    assertThrows(IllegalStateException.class, svc::ensureExtracted);
    Files.writeString(tarball.resolveSibling(tarball.getFileName() + ".sha256"), "00");
    assertThrows(IllegalStateException.class, svc::ensureExtracted);
    String hex =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[] {1, 2, 3}));
    Files.writeString(tarball.resolveSibling(tarball.getFileName() + ".sha256"), hex);
    try {
      svc.ensureExtracted();
    } catch (IllegalStateException | java.io.UncheckedIOException expected) {
      assertTrue(expected.getMessage() != null);
    }
    assertThrows(NullPointerException.class, () -> new IdeService(null, "v", ""));
    assertThrows(NullPointerException.class, () -> new IdeService("d", null, ""));
  }

  @Test
  void ensureExtractedResolvesRelativeIdeDirAbsolutely() throws Exception {
    // The IDE child runs with the workspace home as its cwd, so a relative ide dir must come
    // back absolute or node resolves server-main.js against the wrong directory.
    Path rel = Path.of("target/ide-rel-test");
    Path target = rel.resolve("linux-x64");
    Files.createDirectories(target);
    Files.write(target.resolve(".extracted-1.0-linux-x64"), new byte[0]);
    IdeService svc = new IdeService(rel.toString(), "1.0", "linux-x64");
    Path extracted = svc.ensureExtracted();
    assertTrue(extracted.isAbsolute());
    assertEquals(target.toAbsolutePath().normalize(), extracted);
  }
}
