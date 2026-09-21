/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Detection, variants, and path conversions. No servers start here. */
class PlatformsTest {
  @TempDir private Path tmp;

  @Test
  void detectsWindowsAndLinuxAndMac() {
    Platform win =
        Platforms.detect("Windows 11", "amd64", tmp.resolve("jar"), tmp.resolve("data").toString());
    assertEquals(Platform.Os.WINDOWS, win.os());
    assertEquals(Platform.Arch.X64, win.arch());
    assertEquals("win32-x64", win.rehVariant());
    assertEquals(";", win.pathSeparator());

    Platform mac = Platforms.detect("Mac OS X", "aarch64", tmp.resolve("jar"), null);
    assertEquals(Platform.Os.MACOS, mac.os());
    assertEquals(Platform.Arch.ARM64, mac.arch());
    assertEquals("darwin-arm64", mac.rehVariant());
    assertEquals(":", mac.pathSeparator());

    Platform linux = Platforms.detect("Linux", "x86_64", tmp.resolve("jar"), null);
    assertEquals(Platform.Os.LINUX, linux.os());
    assertEquals("linux-x64", linux.rehVariant());
  }

  @Test
  void mapsArchAliases() {
    assertEquals(Platform.Arch.X64, Platforms.detect("Linux", "amd64", tmp, null).arch());
    assertEquals(Platform.Arch.ARM64, Platforms.detect("Linux", "aarch64", tmp, null).arch());
    assertEquals(Platform.Arch.X64, Platforms.detect("Windows 10", "x86_64", tmp, null).arch());
    assertEquals(Platform.Arch.ARM64, Platforms.detect("Darwin", "arm64", tmp, null).arch());
  }

  @Test
  void allSixVariants() {
    assertEquals(
        "win32-arm64", Platforms.detect("Windows Server", "arm64", tmp, null).rehVariant());
    assertEquals("darwin-x64", Platforms.detect("darwin", "x64", tmp, null).rehVariant());
    assertEquals("linux-arm64", Platforms.detect("Linux", "aarch64", tmp, null).rehVariant());
  }

  @Test
  void unsupportedThrowsWithSupportedList() {
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class, () -> Platforms.detect("Haiku", "sparc", tmp, null));
    assertTrue(failure.getMessage().contains("win32-x64"));
    assertTrue(failure.getMessage().contains("darwin-arm64"));
    assertTrue(failure.getMessage().contains("linux-x64"));
    assertTrue(failure.getMessage().contains("Haiku"));
  }

  @Test
  void explicitDataDirWinsOverJarAdjacent() {
    Path explicit = tmp.resolve("custom");
    Platform platform =
        Platforms.detect("Linux", "x86_64", tmp.resolve("jar"), explicit.toString());
    assertEquals(explicit.toAbsolutePath().normalize(), platform.dataDir());
    Platform adjacent = Platforms.detect("Linux", "x86_64", tmp.resolve("jar"), null);
    assertEquals(
        tmp.resolve("jar").resolve("data").toAbsolutePath().normalize(), adjacent.dataDir());
  }

  @Test
  void toPosixAlwaysUsesSlashes() {
    Platform win = Platforms.detect("Windows 11", "amd64", tmp, null);
    Path backslashed = Path.of("C:\\data\\jahaziel.db");
    assertEquals("C:/data/jahaziel.db", win.toPosix(backslashed));
    Platform linux = Platforms.detect("Linux", "x86_64", tmp, null);
    assertEquals("/data/jahaziel.db", linux.toPosix(Path.of("/data/jahaziel.db")));
    assertEquals("/data/jahaziel.db", linux.toNative(Path.of("/data/jahaziel.db")));
  }

  @Test
  void toNativeFollowsHost() {
    Platform win = Platforms.detect("Windows 11", "amd64", tmp, null);
    assertTrue(win.toNative(Path.of("a/b")).contains("\\"));
    Platform linux = Platforms.detect("Linux", "x86_64", tmp, null);
    assertEquals("a/b", linux.toNative(Path.of("a/b")));
  }

  @Test
  void subprocessEnvPointsAtDataTmp() {
    Platform platform = Platforms.detect("Linux", "x86_64", tmp.resolve("jar"), null);
    Map<String, String> env = platform.subprocessEnv();
    String expected = platform.toNative(platform.tempDir());
    assertEquals(expected, env.get("TEMP"));
    assertEquals(expected, env.get("TMP"));
    assertEquals(expected, env.get("TMPDIR"));
  }

  @Test
  void rehLayoutAndArgs() {
    Platform platform =
        Platforms.detect("Linux", "x86_64", tmp.resolve("jar"), tmp.resolve("data").toString());
    assertTrue(platform.rehExecutable().startsWith(platform.rehDir()));
    List<String> args = platform.rehLaunchArgs(tmp.resolve("ws"), 11451);
    assertTrue(args.contains("11451"));
    assertTrue(args.contains("--without-connection-token"));
    assertTrue(platform.terminal(tmp).size() >= 2);
  }
}
