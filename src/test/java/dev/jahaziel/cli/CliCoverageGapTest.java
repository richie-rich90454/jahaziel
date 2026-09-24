/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

import dev.jahaziel.cli.CliTestSupport.Output;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** Closes the JaCoCo gaps in {@link Cli}. */
class CliCoverageGapTest {
  @TempDir private Path jar;

  private String userDir;

  @BeforeEach
  void pinUserDir() {
    userDir = System.getProperty("user.dir");
    System.setProperty("user.dir", jar.toString());
  }

  @AfterEach
  void restoreUserDir() {
    System.setProperty("user.dir", userDir);
  }

  @Test
  void bannerUnknownHost() throws Exception {
    try (MockedStatic<InetAddress> mocked =
        Mockito.mockStatic(InetAddress.class, Mockito.CALLS_REAL_METHODS)) {
      mocked.when(InetAddress::getLocalHost).thenThrow(new java.net.UnknownHostException("mock"));
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      Cli.printBanner(
          new PrintStream(buffer, true, StandardCharsets.UTF_8),
          jar,
          jar.resolve("data"),
          11451,
          "local",
          false);
      assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("  host: unknown"));
    }
  }

  @Test
  void sha256ImpossibleMapsToIllegalState(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("a.txt");
    Files.writeString(file, "abc", StandardCharsets.UTF_8);
    try (MockedStatic<MessageDigest> mocked = Mockito.mockStatic(MessageDigest.class)) {
      mocked
          .when(() -> MessageDigest.getInstance(anyString()))
          .thenThrow(new java.security.NoSuchAlgorithmException("mock"));
      assertThrows(IllegalStateException.class, () -> Cli.sha256(file));
    }
  }

  @Test
  void versionFallbackAndJarBranches(@TempDir Path dir) throws Exception {
    assertEquals("0.1.0-SNAPSHOT (development)", Cli.version());
    Path with = dir.resolve("with.jar");
    CliTestSupport.makeJar(with, "9.9.9");
    assertEquals("9.9.9", Cli.jarVersion(with));
    Path bare = dir.resolve("bare.jar");
    CliTestSupport.makeJar(bare, null);
    assertEquals("unknown", Cli.jarVersion(bare));
    Path text = dir.resolve("note.txt");
    Files.writeString(text, "x", StandardCharsets.UTF_8);
    assertEquals("unknown", Cli.jarVersion(text));
  }

  @Test
  void versionFromJarManifest(@TempDir Path dir) throws Exception {
    Path manifestJar = dir.resolve("versioned.jar");
    CliTestSupport.makeJar(manifestJar, "7.7.7");
    assertEquals("7.7.7", Cli.jarVersion(manifestJar));
    Path jarWithCli = dir.resolve("cli-versioned.jar");
    CliTestSupport.makeJar(jarWithCli, "7.7.7");
    assertEquals("7.7.7", Cli.jarVersion(jarWithCli));
  }

  @Test
  void restoreGzipEdgeBranches() throws Exception {
    Path empty = jar.resolve("empty.tar.gz");
    Files.createFile(empty);
    assertTrue(CliTestSupport.dispatch("restore", empty.toString()).err().contains("not a gzip"));
    Path one = jar.resolve("one.tar.gz");
    Files.write(one, new byte[] {0x1f});
    assertTrue(CliTestSupport.dispatch("restore", one.toString()).err().contains("not a gzip"));
    Path fake = jar.resolve("fake2.tar.gz");
    CliTestSupport.writeGzip(fake, "not a tar".getBytes(StandardCharsets.UTF_8));
    Output out = CliTestSupport.dispatch("restore", fake.toString());
    assertEquals(1, out.code());
  }

  @Test
  void restoreOnlyEnvProceeds() throws Exception {
    Path src = jar.resolve("onlyenv");
    Files.createDirectories(src);
    Files.writeString(src.resolve(".env"), "A=1\n", StandardCharsets.UTF_8);
    Path archive = jar.resolve("onlyenv.tar.gz");
    CliTestSupport.runTar(
        List.of("tar", "-czf", archive.toString(), "-C", src.toString(), ".env"), jar);
    Path fresh = Files.createDirectory(jar.resolve("fresh-env"));
    String prev = System.getProperty("user.dir");
    System.setProperty("user.dir", fresh.toString());
    try {
      Output out = CliTestSupport.dispatch("restore", archive.toString());
      assertEquals(0, out.code());
      assertTrue(Files.isRegularFile(fresh.resolve(".env")));
    } finally {
      System.setProperty("user.dir", prev);
    }
  }

  @Test
  void restoreOnlyDataProceeds() throws Exception {
    Path src = jar.resolve("onlydata");
    Files.createDirectories(src.resolve("data"));
    Files.writeString(src.resolve("data").resolve("k.txt"), "v", StandardCharsets.UTF_8);
    Path archive = jar.resolve("onlydata.tar.gz");
    CliTestSupport.runTar(
        List.of("tar", "-czf", archive.toString(), "-C", src.toString(), "data"), jar);
    Path fresh = Files.createDirectory(jar.resolve("fresh-data"));
    String prev = System.getProperty("user.dir");
    System.setProperty("user.dir", fresh.toString());
    try {
      Output out = CliTestSupport.dispatch("restore", archive.toString());
      assertEquals(0, out.code());
      assertTrue(Files.isRegularFile(fresh.resolve("data").resolve("k.txt")));
    } finally {
      System.setProperty("user.dir", prev);
    }
  }

  @Test
  void portExplicitEdges() {
    assertEquals(11451, Cli.port(new String[] {"--port=0"}, Map.of()));
    assertEquals(11451, Cli.port(new String[] {"--port=70000"}, Map.of()));
    assertEquals(11451, Cli.port(new String[] {"--port=abc"}, Map.of()));
    assertEquals(1234, Cli.port(new String[] {"--port=  1234  "}, Map.of()));
    assertEquals(11451, Cli.port(new String[] {"--port="}, Map.of()));
  }

  @Test
  void malformedLinesEdges(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("m.env");
    Files.writeString(
        file,
        "OK=1\nexport OK2=2\nexport BAD KEY=3\nNOT VALID\n=nokey\nA\tB=C\n# c\n\n",
        StandardCharsets.UTF_8);
    List<String> bad = Cli.malformedLines(file);
    assertTrue(bad.contains("NOT VALID"));
    assertTrue(bad.stream().anyMatch(l -> l.contains("BAD KEY")));
    Path clean = dir.resolve("c.env");
    Files.writeString(clean, "A=1\nexport B=2\n", StandardCharsets.UTF_8);
    assertTrue(Cli.malformedLines(clean).isEmpty());
  }

  @Test
  void unquoteEdges(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("q.env");
    Files.writeString(
        file,
        "A=x\nB=\"dq\"\nC='sq'\nD=\"unclosed\nE='unclosed\nF=\"\"\nG=''\n",
        StandardCharsets.UTF_8);
    Map<String, String> m = Cli.parseDotEnv(file);
    assertEquals("x", m.get("A"));
    assertEquals("dq", m.get("B"));
    assertEquals("sq", m.get("C"));
    assertEquals("\"unclosed", m.get("D"));
    assertEquals("'unclosed", m.get("E"));
    assertEquals("", m.get("F"));
    assertEquals("", m.get("G"));
  }
}
