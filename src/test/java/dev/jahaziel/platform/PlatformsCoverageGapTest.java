/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

/** Closes the JaCoCo gaps in {@link Platforms}, {@link PosixPlatform} and {@link DotEnv}. */
class PlatformsCoverageGapTest {
  @TempDir private Path tmp;

  private String osName;
  private String osArch;

  @BeforeEach
  void saveOs() {
    osName = System.getProperty("os.name");
    osArch = System.getProperty("os.arch");
  }

  @AfterEach
  void restoreOs() {
    System.setProperty("os.name", osName);
    System.setProperty("os.arch", osArch);
  }

  @Test
  void hostVariantAllOses() {
    System.setProperty("os.name", "Windows 11");
    System.setProperty("os.arch", "amd64");
    assertEquals("win32-x64", Platforms.hostRehVariant());
    System.setProperty("os.arch", "aarch64");
    assertEquals("win32-arm64", Platforms.hostRehVariant());
    System.setProperty("os.name", "Mac OS X");
    System.setProperty("os.arch", "x86_64");
    assertEquals("darwin-x64", Platforms.hostRehVariant());
    System.setProperty("os.arch", "arm64");
    assertEquals("darwin-arm64", Platforms.hostRehVariant());
    System.setProperty("os.name", "Linux");
    System.setProperty("os.arch", "amd64");
    assertEquals("linux-x64", Platforms.hostRehVariant());
    System.setProperty("os.arch", "aarch64");
    assertEquals("linux-arm64", Platforms.hostRehVariant());
    System.setProperty("os.name", "darwin");
    System.setProperty("os.arch", "x64");
    assertEquals("darwin-x64", Platforms.hostRehVariant());
  }

  @Test
  void detectPartialUnsupported() {
    assertTrue(
        assertThrows(
                IllegalStateException.class, () -> Platforms.detect("Linux", "sparc", tmp, null))
            .getMessage()
            .contains("os.arch=sparc"));
    assertTrue(
        assertThrows(
                IllegalStateException.class, () -> Platforms.detect("Haiku", "amd64", tmp, null))
            .getMessage()
            .contains("Haiku"));
  }

  @Test
  void mapOsArchEdges() {
    assertEquals(Platform.Os.MACOS, Platforms.mapOs("darwin"));
    assertEquals(Platform.Os.MACOS, Platforms.mapOs("Macintosh"));
    assertEquals(Platform.Os.WINDOWS, Platforms.mapOs("Windows 10"));
    assertEquals(Platform.Os.LINUX, Platforms.mapOs("linux"));
    assertEquals(Platform.Os.LINUX, Platforms.mapOs("nux"));
    assertEquals(Platform.Os.LINUX, Platforms.mapOs("nix"));
    assertEquals(null, Platforms.mapOs("haiku"));
    assertEquals(null, Platforms.mapOs(null));
    assertEquals(Platform.Arch.X64, Platforms.mapArch("amd64"));
    assertEquals(Platform.Arch.X64, Platforms.mapArch("x86_64"));
    assertEquals(Platform.Arch.X64, Platforms.mapArch("x64"));
    assertEquals(Platform.Arch.X64, Platforms.mapArch("x86-64"));
    assertEquals(Platform.Arch.ARM64, Platforms.mapArch("aarch64"));
    assertEquals(Platform.Arch.ARM64, Platforms.mapArch("arm64"));
    assertEquals(null, Platforms.mapArch("sparc"));
    assertEquals(null, Platforms.mapArch(null));
  }

  @Test
  void resolveJarDirFromJarSource(@TempDir Path dir) throws Exception {
    Path fakeJar = dir.resolve("app.jar");
    Files.createFile(fakeJar);
    File mockFile = Mockito.mock(File.class);
    Mockito.when(mockFile.getName()).thenReturn("app.jar");
    Mockito.when(mockFile.getAbsoluteFile()).thenReturn(mockFile);
    Mockito.when(mockFile.getParentFile()).thenReturn(dir.toFile());
    try (MockedConstruction<org.springframework.boot.system.ApplicationHome> ignored =
        Mockito.mockConstruction(
            org.springframework.boot.system.ApplicationHome.class,
            (mock, ctx) -> Mockito.when(mock.getSource()).thenReturn(mockFile))) {
      Path resolved = Platforms.resolveJarDir();
      assertEquals(dir.toAbsolutePath().normalize(), resolved);
    }
  }

  @Test
  void resolveJarDirFallsBackWhenNoJar() {
    Path resolved = Platforms.resolveJarDir();
    assertTrue(resolved != null);
    assertTrue(Files.isDirectory(resolved) || !Files.exists(resolved));
  }

  @Test
  void posixJarDirAndSurface() {
    PosixPlatform p = new PosixPlatform(Platform.Os.LINUX, Platform.Arch.X64, tmp, tmp);
    assertEquals(tmp.toAbsolutePath().normalize(), p.jarDir());
    assertEquals(tmp.toAbsolutePath().normalize(), p.dataDir());
    assertEquals(Platform.Os.LINUX, p.os());
    assertEquals(Platform.Arch.X64, p.arch());
    assertEquals(tmp.toAbsolutePath().normalize().resolve("tmp"), p.tempDir());
    assertTrue(p.rehDir().toString().contains("linux-x64"));
    assertEquals("linux-x64", p.rehVariant());
    assertTrue(p.rehExecutable().toString().contains("jahaziel-code-server"));
    assertEquals(":", p.pathSeparator());
    assertEquals("a/b", p.toNative(Path.of("a/b")));
    assertEquals("a/b", p.toPosix(Path.of("a/b")));
    List<String> args = p.rehLaunchArgs(tmp, 1234);
    assertTrue(args.contains("1234"));
    assertTrue(p.subprocessEnv().containsKey("TMPDIR"));
  }

  @Test
  void dotEnvIoFailure() throws Exception {
    Path file = tmp.resolve("unreadable.env");
    Files.writeString(file, "A=1\n", StandardCharsets.UTF_8);
    try (var mocked = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      mocked
          .when(() -> Files.readString(file, StandardCharsets.UTF_8))
          .thenThrow(new java.io.IOException("mock"));
      assertThrows(java.io.UncheckedIOException.class, () -> DotEnv.parse(file));
    }
  }

  @Test
  void dotEnvBranches() throws Exception {
    assertTrue(DotEnv.parse(null).isEmpty());
    Path dirAsFile = tmp.resolve("dotdir");
    Files.createDirectory(dirAsFile);
    assertTrue(DotEnv.parse(dirAsFile).isEmpty());
    Path file = tmp.resolve("b.env");
    Files.writeString(
        file,
        "\n# c\nexport E1=v1\nNOEQUALS\n=nokey\nBAD KEY=v\nBAD\tK=v\n"
            + "K1=v1#kept\nK2=v2 # cut\nK3=\"a # b\"\nK4='a # b'\nEMPTY=\nQ=\"q\"\nS='s'\n",
        StandardCharsets.UTF_8);
    Map<String, String> m = DotEnv.parse(file);
    assertEquals("v1", m.get("E1"));
    assertEquals("v1#kept", m.get("K1"));
    assertEquals("v2", m.get("K2"));
    assertEquals("a # b", m.get("K3"));
    assertEquals("a # b", m.get("K4"));
    assertEquals("", m.get("EMPTY"));
    assertEquals("q", m.get("Q"));
    assertEquals("s", m.get("S"));
    assertTrue(!m.containsKey("NOEQUALS"));
    assertTrue(
        DotEnv.missingBasisVars(Map.of("BASIS_AUTH_ISSUER", "   ")).contains("BASIS_AUTH_ISSUER"));
    assertTrue(
        DotEnv.missingBasisVars(Map.of("BASIS_AUTH_ISSUER", "")).contains("BASIS_AUTH_ISSUER"));
    assertEquals(5, DotEnv.missingBasisVars(Map.of()).size());
    Map<String, String> merged =
        DotEnv.withRealEnvWins(Map.of("A", "file"), Map.of("A", "env", "B", "env"));
    assertEquals("env", merged.get("A"));
    assertEquals("env", merged.get("B"));
  }

  @Test
  void admissionRequestedBelowFloor() {
    var refused =
        dev.jahaziel.platform.internal.AdmissionControl.evaluate(
            2000, 1900, 200, 0, 0, 0, 500, 0.9);
    assertTrue(refused instanceof dev.jahaziel.platform.api.AdmissionDecision.Refuse);
    var admit =
        dev.jahaziel.platform.internal.AdmissionControl.evaluate(8000, 0, 100, 0, 0, 0, 512, 0.9);
    assertTrue(admit instanceof dev.jahaziel.platform.api.AdmissionDecision.Admit);
  }
}
