/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.cli.CliTestSupport.Output;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliInitUpgradeTest {
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
  void initWritesEnvWithSecretAndData() throws Exception {
    Output output = CliTestSupport.dispatch("init");
    assertEquals(0, output.code());
    Path env = jar.resolve(".env");
    assertTrue(Files.isRegularFile(env));
    String body = Files.readString(env, StandardCharsets.UTF_8);
    for (String key : Cli.BASIS_VARS) {
      assertTrue(body.contains(key + "="), key);
    }
    String secret =
        body.lines()
            .filter(line -> line.startsWith("BASIS_CLIENT_SECRET="))
            .map(line -> line.substring("BASIS_CLIENT_SECRET=".length()))
            .findFirst()
            .orElseThrow();
    assertEquals(64, secret.length());
    assertEquals(secret.toLowerCase(), secret);
    HexFormat.of().parseHex(secret);
    assertTrue(Files.isDirectory(jar.resolve("data").resolve("tmp")));
    assertTrue(!Files.exists(jar.resolve("data").resolve("keys").resolve("jwt.key")));
    assertTrue(output.out().contains("jahaziel: wrote " + env));
    assertTrue(output.out().contains("run `doctor`"));
  }

  @Test
  void initRefusesExisting() throws Exception {
    CliTestSupport.writeEnv(jar, "BASIS_CLIENT_ID=kept\n");
    Output output = CliTestSupport.dispatch("init");
    assertEquals(1, output.code());
    assertTrue(output.err().contains(".env already exists"));
    assertTrue(output.err().contains("fix: edit it in place"));
    assertEquals("BASIS_CLIENT_ID=kept\n", Files.readString(jar.resolve(".env")));
  }

  @Test
  void initThrowsWhenJarIsFile() throws Exception {
    Path file = jar.resolve("blocker");
    Files.writeString(file, "x", StandardCharsets.UTF_8);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    assertThrows(java.io.IOException.class, () -> Cli.init(file, out, out));
  }

  @Test
  void upgradeCheckAndBare() {
    Output check = CliTestSupport.dispatch("upgrade", "--check");
    assertEquals(0, check.code());
    assertTrue(check.out().contains("jahaziel " + Cli.version()));
    assertTrue(check.out().contains("upgrade --to <jar>"));
    Output bare = CliTestSupport.dispatch("upgrade");
    assertEquals(0, bare.code());
    assertTrue(bare.out().contains("jahaziel " + Cli.version()));
  }

  @Test
  void upgradeNeedsTarget() {
    Output missing = CliTestSupport.dispatch("upgrade", "--to");
    assertEquals(2, missing.code());
    assertTrue(missing.err().contains("upgrade needs --to <jar>"));
    Output other = CliTestSupport.dispatch("upgrade", "nope");
    assertEquals(2, other.code());
    assertTrue(other.err().contains("upgrade needs --to <jar>"));
  }

  @Test
  void upgradeRejectsAbsentJar() throws Exception {
    Path absent = jar.resolve("absent.jar");
    Output output = CliTestSupport.dispatch("upgrade", "--to", absent.toString());
    assertEquals(1, output.code());
    assertTrue(output.err().contains("jahaziel: no such jar: "));
    Path dir = jar.resolve("adir");
    Files.createDirectories(dir);
    Output dirOut = CliTestSupport.dispatch("upgrade", "--to=" + dir);
    assertEquals(1, dirOut.code());
    assertTrue(dirOut.err().contains("jahaziel: no such jar: "));
  }

  @Test
  void upgradeAlreadyOnSameVersion() throws Exception {
    Path live = jar.resolve("jahaziel.jar");
    CliTestSupport.makeJar(live, "1.0.0");
    Path next = jar.resolve("next.jar");
    CliTestSupport.makeJar(next, Cli.version());
    Output output = CliTestSupport.dispatch("upgrade", "--to", next.toString());
    assertEquals(0, output.code());
    assertTrue(output.out().contains("already on " + Cli.version()));
    assertEquals("1.0.0", Cli.jarVersion(live));
  }

  @Test
  void upgradeReplacesLiveAndKeepsBackup() throws Exception {
    Path live = jar.resolve("jahaziel.jar");
    CliTestSupport.makeJar(live, "1.0.0");
    Path next = jar.resolve("next.jar");
    CliTestSupport.makeJar(next, "2.0.0");
    Output output = CliTestSupport.dispatch("upgrade", "--to=" + next);
    assertEquals(0, output.code());
    assertTrue(output.out().contains("upgraded " + Cli.version() + " -> 2.0.0"));
    assertTrue(output.out().contains("backup: "));
    assertEquals("2.0.0", Cli.jarVersion(live));
    try (var stream = Files.list(jar)) {
      assertTrue(
          stream.anyMatch(path -> path.getFileName().toString().startsWith("jahaziel.jar.bak-")));
    }
  }

  @Test
  void upgradeWithoutLiveJarFailsToReplace() throws Exception {
    Path next = jar.resolve("next.jar");
    CliTestSupport.makeJar(next, "2.0.0");
    Output output = CliTestSupport.dispatch("upgrade", "--to", next.toString());
    assertEquals(1, output.code());
    assertTrue(output.err().contains("jahaziel: cannot replace "));
    assertTrue(output.err().contains("stop the running server first"));
  }
}
