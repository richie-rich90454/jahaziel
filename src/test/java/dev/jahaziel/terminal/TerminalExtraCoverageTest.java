/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.terminal.domain.TerminalSession;
import dev.jahaziel.terminal.domain.TerminalSessionId;
import dev.jahaziel.terminal.internal.TerminalServiceImpl;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class TerminalExtraCoverageTest {
  private TerminalServiceImpl svc() {
    return new TerminalServiceImpl();
  }

  @Test
  void openGetCloseListActivity() {
    TerminalServiceImpl s = svc();
    WorkspaceId w = new WorkspaceId(UUID.randomUUID());
    UserId u = new UserId(UUID.randomUUID());
    TerminalSession opened = s.open(w, u, 24, 80);
    assertEquals(opened, s.get(opened.id()));
    s.activity(opened.id());
    assertEquals(1, s.listByWorkspace(w).size());
    assertTrue(s.listByWorkspace(new WorkspaceId(UUID.randomUUID())).isEmpty());
    assertThrows(NullPointerException.class, () -> s.get(null));
    assertThrows(NullPointerException.class, () -> s.close(null));
    assertThrows(NullPointerException.class, () -> s.listByWorkspace(null));
    TerminalSessionId missing = new TerminalSessionId(UUID.randomUUID());
    assertThrows(dev.jahaziel.terminal.api.TerminalNotFoundException.class, () -> s.get(missing));
    assertThrows(dev.jahaziel.terminal.api.TerminalNotFoundException.class, () -> s.close(missing));
    assertThrows(
        dev.jahaziel.terminal.api.TerminalNotFoundException.class, () -> s.activity(missing));
    s.close(opened.id());
    assertTrue(s.listByWorkspace(w).isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  void evictIdleRemovesStaleWhenOverLimit() throws Exception {
    TerminalServiceImpl s = svc();
    Field f = TerminalServiceImpl.class.getDeclaredField("sessions");
    f.setAccessible(true);
    ConcurrentHashMap<TerminalSessionId, TerminalSession> map =
        (ConcurrentHashMap<TerminalSessionId, TerminalSession>) f.get(s);
    WorkspaceId w = new WorkspaceId(UUID.randomUUID());
    UserId u = new UserId(UUID.randomUUID());
    for (int i = 0; i < 10005; i++) {
      TerminalSession sess =
          new TerminalSession(new TerminalSessionId(UUID.randomUUID()), w, u, 24, 80);
      Field last = TerminalSession.class.getDeclaredField("lastActivityAt");
      last.setAccessible(true);
      last.set(sess, java.time.Instant.now().minus(java.time.Duration.ofHours(25)));
      map.put(sess.id(), sess);
    }
    s.open(w, u, 24, 80);
    assertTrue(map.size() < 10006);
  }

  @Test
  void terminalSessionValidation() {
    WorkspaceId w = new WorkspaceId(UUID.randomUUID());
    UserId u = new UserId(UUID.randomUUID());
    assertThrows(
        IllegalArgumentException.class,
        () -> new TerminalSession(new TerminalSessionId(UUID.randomUUID()), w, u, 0, 80));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TerminalSession(new TerminalSessionId(UUID.randomUUID()), w, u, 24, 0));
    TerminalSession s = new TerminalSession(new TerminalSessionId(UUID.randomUUID()), w, u, 24, 80);
    s.resize(30, 100);
    assertEquals(30, s.rowCount());
    assertThrows(IllegalArgumentException.class, () -> s.resize(0, 1));
    assertThrows(NullPointerException.class, () -> new TerminalSession(null, w, u, 1, 1));
  }
}
