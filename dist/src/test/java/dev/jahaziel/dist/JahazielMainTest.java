/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Behavior of {@link JahazielMain} argument handling. No servers start here. */
class JahazielMainTest {
  @Test
  void bareJarIsHelpAlias() {
    Output output = run();
    assertEquals(0, output.code());
    assertTrue(output.text().contains("Usage: java -jar jahaziel.jar"));
  }

  @Test
  void helpAndVersion() {
    assertEquals(0, run("--help").code());
    assertTrue(run("--help").text().contains("--run=all"));
    assertTrue(run("--version").text().startsWith("jahaziel "));
  }

  @Test
  void rejectsUnknownAndMissingMode() {
    assertEquals(2, run("--bogus").code());
    assertEquals(2, run("--port=9000").code());
    assertEquals(2, run("--run=bogus").code());
    assertEquals(2, run("--run").code());
    assertEquals(2, run("--port=abc", "--run=all").code());
  }

  @Test
  void parsesModes() {
    assertEquals(
        JahazielMain.Mode.ALL, JahazielMain.Arguments.parse(new String[] {"--run=all"}).mode());
    assertEquals(
        JahazielMain.Mode.CONTROL_PLANE,
        JahazielMain.Arguments.parse(new String[] {"--run", "control-plane"}).mode());
    assertEquals(
        JahazielMain.Mode.NODE_AGENT,
        JahazielMain.Arguments.parse(new String[] {"--run=node-agent"}).mode());
    assertEquals(
        JahazielMain.Mode.GATEWAY,
        JahazielMain.Arguments.parse(new String[] {"--run=gateway"}).mode());
  }

  private static Output run(String... args) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    int code = JahazielMain.run(args, out, out);
    return new Output(code, buffer.toString(StandardCharsets.UTF_8));
  }

  private record Output(int code, String text) {}
}
