/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.language.api.LanguagePackService;
import dev.jahaziel.workspace.api.WorkspaceDirectoryService;
import dev.jahaziel.workspace.api.WorkspaceProcessRegistry;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class LanguageServerProcessManagerTest {
  @TempDir Path temporary;

  @Test
  void rejectsUnlistedLanguageBeforeUsingTheLanguageIdInAStoragePath() throws Exception {
    LanguagePackService packs = mock(LanguagePackService.class);
    WorkspaceDirectoryService workspaces = mock(WorkspaceDirectoryService.class);
    LanguageServerProcessManager manager =
        new LanguageServerProcessManager(
            packs,
            workspaces,
            new ObjectMapper(),
            temporary.toString(),
            mock(WorkspaceProcessRegistry.class));

    try {
      assertThrows(
          IllegalArgumentException.class,
          () -> manager.open(new WorkspaceId(UUID.randomUUID()), "../escape", ignored -> {}));

      verify(packs).supportsLanguageService("../escape");
      verify(workspaces, never()).home(any(WorkspaceId.class));
      org.junit.jupiter.api.Assertions.assertFalse(
          Files.exists(temporary.resolve("language-packs")));
    } finally {
      manager.closeAll();
    }
  }

  @Test
  void initializeUsesServerOwnedTypeScriptOptionsAndWorkspaceRoot() throws Exception {
    Path node = nodeExecutable();
    org.junit.jupiter.api.Assumptions.assumeTrue(node != null, "Node.js is available on PATH");
    Path workspace = temporary.resolve("workspace");
    Path captured = temporary.resolve("initialize.json");
    Files.createDirectories(workspace);
    String script =
        "const fs=require('node:fs');let b=Buffer.alloc(0);const d='\\r\\n\\r\\n';"
            + "function read(){for(;;){const p=b.indexOf(d);if(p<0)return;"
            + "const m=/Content-Length: (\\d+)/i.exec(b.subarray(0,p).toString());if(!m)process.exit(2);"
            + "const n=Number(m[1]);if(b.length<p+4+n)return;const x=JSON.parse(b.subarray(p+4,p+4+n));"
            + "b=b.subarray(p+4+n);if(x.method==='exit')process.exit(0);"
            + "fs.writeFileSync(process.argv[1],JSON.stringify(x));"
            + "const o=Buffer.from(JSON.stringify({jsonrpc:'2.0',id:x.id,result:{}}));"
            + "process.stdout.write(Buffer.concat([Buffer.from('Content-Length: '+o.length+d),o]));}}"
            + "process.stdin.on('data',x=>{b=Buffer.concat([b,x]);read()});";
    LanguagePackService packs = mock(LanguagePackService.class);
    when(packs.supportsLanguageService("typescript")).thenReturn(true);
    when(packs.launchCommandFor(
            any(WorkspaceId.class), org.mockito.ArgumentMatchers.eq("typescript"), any(Path.class)))
        .thenReturn(List.of(node.toString(), "-e", script, captured.toString()));
    when(packs.initializationOptionsFor(
            any(WorkspaceId.class), org.mockito.ArgumentMatchers.eq("typescript")))
        .thenReturn(Map.of("tsserver", Map.of("path", "/server-cache/typescript/tsserver.js")));
    WorkspaceDirectoryService directories = mock(WorkspaceDirectoryService.class);
    when(directories.home(any(WorkspaceId.class))).thenReturn(workspace);
    WorkspaceProcessRegistry registry = mock(WorkspaceProcessRegistry.class);
    when(registry.dedicatedSessionCommand(any())).thenAnswer(call -> call.getArgument(0));
    when(registry.track(any(WorkspaceId.class), any(), any()))
        .thenReturn(mock(WorkspaceProcessRegistry.Registration.class));
    LanguageServerProcessManager manager =
        new LanguageServerProcessManager(
            packs, directories, new ObjectMapper(), temporary.toString(), registry);
    LanguageServerProcessManager.Session session =
        manager.open(new WorkspaceId(UUID.randomUUID()), "typescript", ignored -> {});

    try {
      session.send(
          "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
              + "\"rootUri\":\"file:///attacker\",\"initializationOptions\":{"
              + "\"tsserver\":{\"path\":\"/attacker/tool.js\"}}}}");
      for (int attempt = 0; attempt < 100 && !Files.exists(captured); attempt++) {
        Thread.sleep(20);
      }
      assertTrue(Files.isRegularFile(captured));
      var message = new ObjectMapper().readTree(Files.readString(captured));
      assertEquals(workspace.toUri().toString(), message.path("params").path("rootUri").asString());
      assertEquals(
          "/server-cache/typescript/tsserver.js",
          message
              .path("params")
              .path("initializationOptions")
              .path("tsserver")
              .path("path")
              .asString());
    } finally {
      session.close();
      manager.closeAll();
    }
  }

  private static Path nodeExecutable() {
    String executable =
        System.getProperty("os.name", "").toLowerCase().contains("windows") ? "node.exe" : "node";
    String path = System.getenv("PATH");
    if (path == null) return null;
    for (String entry : path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
      Path candidate = Path.of(entry).resolve(executable);
      if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return candidate;
    }
    return null;
  }
}
