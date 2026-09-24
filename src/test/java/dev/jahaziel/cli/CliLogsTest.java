/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.cli.CliTestSupport.Output;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CliLogsTest {
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
  void missingLogFile() {
    Output output = CliTestSupport.dispatch("logs");
    assertEquals(1, output.code());
    assertTrue(output.err().contains("jahaziel: no log file yet: "));
    assertTrue(output.err().contains("start the server first"));
  }

  @Test
  void unknownLevel() throws Exception {
    writeLog("2026-01-01 INFO up\n");
    Output output = CliTestSupport.dispatch("logs", "--level=BOGUS");
    assertEquals(2, output.code());
    assertTrue(output.err().contains("jahaziel: unknown level: BOGUS"));
  }

  @Test
  void tailsLastHundredLines() throws Exception {
    StringBuilder body = new StringBuilder();
    IntStream.rangeClosed(1, 150).forEach(i -> body.append("line-").append(i).append(" INFO m\n"));
    writeLog(body.toString());
    Output output = CliTestSupport.dispatch("logs");
    assertEquals(0, output.code());
    String[] lines = output.out().strip().split("\r?\n");
    assertEquals(100, lines.length);
    assertTrue(lines[0].startsWith("line-51 "));
    assertTrue(lines[99].startsWith("line-150 "));
  }

  @ParameterizedTest
  @CsvSource({
    "TRACE, true, true, true, true, true",
    "DEBUG, false, true, true, true, true",
    "INFO, false, false, true, true, true",
    "WARN, false, false, false, true, true",
    "ERROR, false, false, false, false, true",
  })
  void levelFilter(
      String level, boolean plain, boolean debug, boolean info, boolean warn, boolean error)
      throws Exception {
    writeLog(
        "plain line\n"
            + "2026-01-01 DEBUG d\n"
            + "2026-01-01 INFO i\n"
            + "2026-01-01 WARN w\n"
            + "2026-01-01 ERROR e\n"
            + "2026-01-01 TRACE t\n");
    Output output = CliTestSupport.dispatch("logs", "--level=" + level);
    assertEquals(0, output.code());
    assertEquals(plain, output.out().contains("plain line"));
    assertEquals(debug, output.out().contains("DEBUG d"));
    assertEquals(info, output.out().contains("INFO i"));
    assertEquals(warn, output.out().contains("WARN w"));
    assertEquals(error, output.out().contains("ERROR e"));
  }

  @Test
  void warningAliasCaseAndSpaceForm() throws Exception {
    writeLog("2026-01-01 info i\n2026-01-01 warn w\n2026-01-01 error e\n");
    Output alias = CliTestSupport.dispatch("logs", "--level=warning");
    assertEquals(0, alias.code());
    assertTrue(!alias.out().contains("info i"));
    assertTrue(alias.out().contains("warn w"));
    Output lower = CliTestSupport.dispatch("logs", "--level", "warn");
    assertTrue(lower.out().contains("warn w"));
    assertTrue(!lower.out().contains("info i"));
    Output all = CliTestSupport.dispatch("logs");
    assertTrue(all.out().contains("info i"));
  }

  @Test
  void mixedLineUsesHighestSeverity() throws Exception {
    writeLog("warn then error INFO?\nerror with warn inside\n");
    Output output = CliTestSupport.dispatch("logs", "--level=ERROR");
    assertEquals(0, output.code());
    assertTrue(output.out().contains("warn then error"));
    assertTrue(output.out().contains("error with warn"));
  }

  @Test
  void followWithFilterRotationAndInterrupt() throws Exception {
    Path log = writeLog("2026-01-01 INFO boot\n2026-01-01 ERROR bad\n");
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    PrintStream err = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
    AtomicInteger code = new AtomicInteger(-1);
    Thread worker =
        new Thread(
            () -> code.set(Cli.dispatch(new String[] {"logs", "-f", "--level=ERROR"}, out, err)));
    worker.setDaemon(true);
    worker.start();
    Thread.sleep(900);
    Files.writeString(
        log,
        "2026-01-01 INFO hidden\n2026-01-01 ERROR shown\n",
        StandardCharsets.UTF_8,
        java.nio.file.StandardOpenOption.APPEND);
    Thread.sleep(1300);
    Files.writeString(log, "tiny\n", StandardCharsets.UTF_8);
    Thread.sleep(1300);
    Files.writeString(
        log,
        "2026-01-01 ERROR after-rotation\n",
        StandardCharsets.UTF_8,
        java.nio.file.StandardOpenOption.APPEND);
    Thread.sleep(1300);
    worker.interrupt();
    worker.join(10_000);
    assertTrue(!worker.isAlive());
    assertEquals(0, code.get());
    String text = buffer.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains("ERROR bad"));
    assertTrue(text.contains("ERROR shown"));
    assertTrue(text.contains("ERROR after-rotation"));
    assertTrue(!text.contains("INFO boot"));
    assertTrue(!text.contains("INFO hidden"));
  }

  @Test
  void followLongFlagExitsOnInterrupt() throws Exception {
    writeLog("2026-01-01 INFO boot\n");
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    AtomicInteger code = new AtomicInteger(-1);
    Thread worker =
        new Thread(
            () ->
                code.set(
                    Cli.dispatch(
                        new String[] {"logs", "--follow"},
                        out,
                        new PrintStream(
                            new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))));
    worker.setDaemon(true);
    worker.start();
    Thread.sleep(900);
    worker.interrupt();
    worker.join(10_000);
    assertEquals(0, code.get());
    assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("INFO boot"));
  }

  private Path writeLog(String body) throws Exception {
    Path logs = jar.resolve("data").resolve("logs");
    Files.createDirectories(logs);
    Path log = logs.resolve("control-plane.log");
    Files.writeString(log, body, StandardCharsets.UTF_8);
    return log;
  }
}
