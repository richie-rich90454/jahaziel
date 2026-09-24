/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.cli.CliTestSupport.Output;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliDispatchTest {
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
  void bareWithoutEnvPrintsUsage() {
    Output output = CliTestSupport.dispatch();
    assertEquals(0, output.code());
    assertTrue(output.out().contains("Usage: java -jar jahaziel.jar"));
    assertEquals("", output.err());
  }

  @Test
  void bareWithEnvPassesThroughWithBanner() throws Exception {
    CliTestSupport.writeEnv(jar, CliTestSupport.validEnv());
    Output output = CliTestSupport.dispatch();
    assertEquals(Cli.PASS_THROUGH, output.code());
    assertTrue(output.out().contains("jahaziel " + Cli.version()));
    assertTrue(output.out().contains("  port: 11451"));
    assertTrue(output.out().contains("  backend: local"));
    assertTrue(output.out().contains("  status: starting (basis-auth)"));
  }

  @Test
  void helpFlags() {
    for (String flag : new String[] {"--help", "-h"}) {
      Output output = CliTestSupport.dispatch(flag);
      assertEquals(0, output.code());
      assertTrue(output.out().contains("Usage: java -jar jahaziel.jar"));
    }
  }

  @Test
  void versionFlag() {
    Output output = CliTestSupport.dispatch("--version");
    assertEquals(0, output.code());
    assertTrue(output.out().contains("jahaziel " + Cli.version()));
  }

  @Test
  void unknownCommand() {
    Output output = CliTestSupport.dispatch("frobnicate");
    assertEquals(2, output.code());
    assertTrue(output.err().contains("jahaziel: unknown command: frobnicate"));
    assertTrue(output.err().contains("Usage: java -jar jahaziel.jar"));
  }

  @Test
  void dashLedRunIsValidatedPassThrough() throws Exception {
    CliTestSupport.writeEnv(jar, CliTestSupport.validEnv());
    Output output = CliTestSupport.dispatch("--port=4321");
    assertEquals(Cli.PASS_THROUGH, output.code());
    assertTrue(output.out().contains("  port: 4321"));
  }

  @Test
  void demoRunMarksBanner() throws Exception {
    CliTestSupport.writeEnv(jar, CliTestSupport.validEnv());
    Output output = CliTestSupport.dispatch("--demo");
    assertEquals(Cli.PASS_THROUGH, output.code());
    assertTrue(output.out().contains("(demo: stub IdP, BASIS_* ignored)"));
  }

  @Test
  void dataDirFlagReachesBanner() throws Exception {
    Path data = jar.resolve("custom-data");
    Output output = CliTestSupport.dispatch("--data-dir=" + data);
    assertEquals(Cli.PASS_THROUGH, output.code());
    assertTrue(output.out().contains("  data: " + data.toAbsolutePath().normalize()));
  }

  @Test
  void ioFailureMapsToOne() throws Exception {
    Path file = jar.resolve("blocking-file");
    Files.writeString(file, "x", StandardCharsets.UTF_8);
    System.setProperty("user.dir", file.toString());
    try {
      Output output = CliTestSupport.dispatch("init");
      assertEquals(1, output.code());
      assertTrue(output.err().contains("jahaziel: failed: "));
    } finally {
      System.setProperty("user.dir", jar.toString());
    }
  }

  @Test
  void doctorVerbReportsChecks() throws Exception {
    CliTestSupport.writeEnv(jar, CliTestSupport.validEnv());
    Output output = CliTestSupport.dispatch("doctor");
    assertTrue(output.code() == 0 || output.code() == 1);
    assertTrue(output.out().contains("doctor: "));
    assertTrue(output.out().contains(" checks passed"));
  }

  @Test
  void doctorVerbHonorsDataDirAndPort() throws Exception {
    CliTestSupport.writeEnv(jar, CliTestSupport.validEnv());
    Path data = jar.resolve("doc-data");
    Files.createDirectories(data);
    int port = CliTestSupport.freePort();
    Output output = CliTestSupport.dispatch("doctor", "--data-dir=" + data, "--port=" + port);
    assertTrue(output.out().contains("doctor: "));
    assertTrue(
        output.out().contains(port + " is free") || output.out().contains(port + " is in use"));
  }
}
