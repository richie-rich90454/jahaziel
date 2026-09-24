/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.platform.internal.AdmissionControl;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlatformExtraCoverageTest {
  @TempDir private Path tmp;

  @Test
  void admissionDegradedAndRefused() {
    var admit = AdmissionControl.evaluate(8000, 1000, 1000, 1000, 500, 500, 512, 0.9);
    assertTrue(admit instanceof dev.jahaziel.platform.api.AdmissionDecision.Admit);
    var degraded = AdmissionControl.evaluate(8000, 1000, 6000, 1000, 500, 500, 512, 0.9);
    assertTrue(degraded instanceof dev.jahaziel.platform.api.AdmissionDecision.Admit);
    assertEquals(512L, ((dev.jahaziel.platform.api.AdmissionDecision.Admit) degraded).memoryMib());
    var refusedFloorTooBig = AdmissionControl.evaluate(4000, 1000, 6000, 1000, 500, 500, 5000, 0.9);
    assertTrue(refusedFloorTooBig instanceof dev.jahaziel.platform.api.AdmissionDecision.Refuse);
    var refusedPressure = AdmissionControl.evaluate(4000, 3900, 6000, 0, 0, 0, 50, 0.5);
    assertTrue(refusedPressure instanceof dev.jahaziel.platform.api.AdmissionDecision.Refuse);
    var refusedSmallFloor = AdmissionControl.evaluate(8000, 7900, 6000, 0, 0, 0, 512, 0.9);
    assertTrue(refusedSmallFloor instanceof dev.jahaziel.platform.api.AdmissionDecision.Refuse);
  }

  @Test
  void platformsEdgeBranches() {
    assertEquals(Platform.Os.MACOS, Platforms.mapOs("darwin"));
    assertEquals(Platform.Os.MACOS, Platforms.mapOs("Macintosh"));
    assertEquals(Platform.Os.WINDOWS, Platforms.mapOs("Windows 10"));
    assertEquals(Platform.Os.LINUX, Platforms.mapOs("nux"));
    assertEquals(Platform.Os.LINUX, Platforms.mapOs("nix"));
    assertEquals(Platform.Os.LINUX, Platforms.mapOs("Linux"));
    assertEquals(null, Platforms.mapOs("haiku"));
    assertEquals(null, Platforms.mapOs(null));
    assertEquals(Platform.Arch.X64, Platforms.mapArch("x64"));
    assertEquals(Platform.Arch.X64, Platforms.mapArch("x86-64"));
    assertEquals(Platform.Arch.X64, Platforms.mapArch("X86_64"));
    assertEquals(null, Platforms.mapArch("sparc"));
    assertEquals(null, Platforms.mapArch(null));
    assertThrows(NullPointerException.class, () -> Platforms.detect("Linux", "x86_64", null, null));
    Platform blankDir = Platforms.detect("Linux", "x86_64", tmp, "  ");
    assertTrue(blankDir.dataDir().toString().endsWith("data"));
    assertTrue(Platforms.hostRehVariant().matches("(win32|darwin|linux)-(x64|arm64)"));
    assertTrue(
        Platforms.SUPPORTED_VARIANTS.contains(Platforms.hostRehVariant())
            || Platforms.hostRehVariant().startsWith("linux"));
    assertTrue(Platforms.resolveJarDir() != null);
  }

  @Test
  void windowsFullSurface() {
    WindowsPlatform w = new WindowsPlatform(tmp, tmp.resolve("data"), Platform.Arch.ARM64);
    assertEquals(Platform.Os.WINDOWS, w.os());
    assertEquals(Platform.Arch.ARM64, w.arch());
    assertEquals("win32-arm64", w.rehVariant());
    assertTrue(w.rehExecutable().toString().endsWith(".cmd"));
    assertTrue(w.rehDir().toString().contains("win32-arm64"));
    assertTrue(w.tempDir().toString().endsWith("tmp"));
    assertEquals(tmp.toAbsolutePath().normalize(), w.jarDir());
    List<String> args = w.rehLaunchArgs(tmp.resolve("ws"), 8080);
    assertTrue(args.contains("8080"));
    assertTrue(w.terminal(tmp).contains("cmd.exe"));
    assertEquals(";", w.pathSeparator());
    assertThrows(NullPointerException.class, () -> w.toNative(null));
    assertThrows(NullPointerException.class, () -> w.toPosix(null));
    assertThrows(NullPointerException.class, () -> w.rehLaunchArgs(null, 1));
    assertThrows(NullPointerException.class, () -> w.terminal(null));
    Map<String, String> env = w.subprocessEnv();
    assertTrue(env.get("TEMP").contains("tmp"));
  }

  @Test
  void posixRejectsWindowsAndCoversMacTerminal() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new PosixPlatform(Platform.Os.WINDOWS, Platform.Arch.X64, tmp, tmp));
    PosixPlatform mac = new PosixPlatform(Platform.Os.MACOS, Platform.Arch.X64, tmp, tmp);
    assertTrue(mac.terminal(tmp).contains("open"));
    assertEquals("darwin-x64", mac.rehVariant());
    PosixPlatform linux = new PosixPlatform(Platform.Os.LINUX, Platform.Arch.ARM64, tmp, tmp);
    assertEquals("linux-arm64", linux.rehVariant());
    assertTrue(linux.terminal(tmp).contains("x-terminal-emulator"));
    assertThrows(NullPointerException.class, () -> linux.toNative(null));
    assertThrows(NullPointerException.class, () -> linux.toPosix(null));
    assertThrows(NullPointerException.class, () -> linux.rehLaunchArgs(null, 1));
    assertThrows(NullPointerException.class, () -> linux.terminal(null));
  }

  @Test
  void dotEnvOddLines() throws Exception {
    Path file = tmp.resolve(".env");
    Files.writeString(
        file,
        "NOEQUALS\n"
            + "=noname\n"
            + "BAD KEY=v\n"
            + "BAD\tKEY=v\n"
            + "export\n"
            + "export   SPACED = spacedval\n"
            + "K1=v1#nospace\n"
            + "K2=v2 # cut\n"
            + "K3=\"a # b\" # cut\n"
            + "K4='a # b' # cut\n"
            + "K5=\"quoted\"\n"
            + "K6='single'\n"
            + "# comment\n"
            + "\n",
        StandardCharsets.UTF_8);
    Map<String, String> m = DotEnv.parse(file);
    assertTrue(!m.containsKey("NOEQUALS"));
    assertTrue(!m.containsKey("BAD KEY"));
    assertEquals("spacedval", m.get("SPACED"));
    assertEquals("v1#nospace", m.get("K1"));
    assertEquals("v2", m.get("K2"));
    assertEquals("a # b", m.get("K3"));
    assertEquals("a # b", m.get("K4"));
    assertEquals("quoted", m.get("K5"));
    assertEquals("single", m.get("K6"));
    assertTrue(DotEnv.parse(null).isEmpty());
    Path dirAsFile = tmp.resolve("adir");
    Files.createDirectory(dirAsFile);
    assertTrue(DotEnv.parse(dirAsFile).isEmpty());
    assertEquals(
        List.of(),
        DotEnv.missingBasisVars(DotEnv.withRealEnvWins(Map.of(), Map.of())).stream()
            .filter(s -> s.equals("NEVER"))
            .toList());
  }
}
