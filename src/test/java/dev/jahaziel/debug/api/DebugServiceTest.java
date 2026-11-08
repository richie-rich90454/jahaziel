/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.debug.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DebugServiceTest {
  @Test
  void acceptsWorkspaceRelativeNodeLaunch() {
    var request =
        new DebugService.StartRequest(
            "node", "src/../server.js", null, List.of("--inspect"), "src", true);

    assertEquals("server.js", request.program());
    assertEquals("src", request.workingDirectory());
    assertEquals(List.of("--inspect"), request.arguments());
    assertEquals(true, request.stopOnEntry());
  }

  @Test
  void defaultsWorkingDirectoryToWorkspaceRoot() {
    var request = new DebugService.StartRequest("python", "main.py", null, null, null, false);

    assertEquals(".", request.workingDirectory());
  }

  @Test
  void acceptsJavaMainClassWithoutExecutablePath() {
    var request =
        new DebugService.StartRequest("java", null, "com.example.Main", List.of(), ".", false);

    assertEquals("com.example.Main", request.mainClass());
  }

  @Test
  void rejectsUnknownLanguage() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new DebugService.StartRequest("shell", "script.sh", null, List.of(), ".", false));
  }

  @Test
  void rejectsAbsoluteAndTraversingProgramPaths() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DebugService.StartRequest(
                "node", "C:\\Windows\\system32\\calc.exe", null, List.of(), ".", false));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DebugService.StartRequest("node", "../../outside.js", null, List.of(), ".", false));
  }

  @Test
  void rejectsExecutableAndRemoteAttachFieldsForJava() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DebugService.StartRequest(
                "java", "java.exe", "com.example.Main", List.of(), ".", false));
  }

  @Test
  void boundsArgumentsAndValidatesJavaMainClass() {
    List<String> tooMany = java.util.Collections.nCopies(129, "x");
    assertThrows(
        IllegalArgumentException.class,
        () -> new DebugService.StartRequest("go", "main.go", null, tooMany, ".", false));
    assertThrows(
        IllegalArgumentException.class,
        () -> new DebugService.StartRequest("java", null, "../Main", List.of(), ".", false));
  }
}
