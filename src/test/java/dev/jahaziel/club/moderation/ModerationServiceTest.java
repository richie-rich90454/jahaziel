/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.club.api.ClubAccessDeniedException;
import dev.jahaziel.club.api.ClubLimitException;
import dev.jahaziel.club.domain.ClubRoles;
import dev.jahaziel.club.domain.ClubSession;
import dev.jahaziel.club.session.ClubSessionService;
import dev.jahaziel.club.support.Fakes;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/** Chat persistence, mute, pin, lock, removal, and help queue behavior. */
class ModerationServiceTest {
  @TempDir Path temp;

  private ModerationService moderation;
  private ClubSession session;
  private UserId host;
  private UserId attendee;
  private UserId observer;
  private UserId second;

  @BeforeEach
  void setUp() {
    moderation = new ModerationService(temp.resolve("club"));
    ClubSessionService sessions =
        new ClubSessionService(new Fakes.Workspace(), new Fakes.Tasks(), new NodeId("node-a"));
    host = new UserId(UUID.randomUUID());
    attendee = new UserId(UUID.randomUUID());
    observer = new UserId(UUID.randomUUID());
    second = new UserId(UUID.randomUUID());
    session =
        sessions.createSession(
            host,
            "java",
            Instant.parse("2026-11-19T10:00:00Z"),
            Instant.parse("2026-11-19T12:00:00Z"),
            10);
    session.join(attendee, ClubRoles.ATTENDEE);
    session.join(second, ClubRoles.ATTENDEE);
    session.join(observer, ClubRoles.OBSERVER);
  }

  @Test
  void chatIsPersistedBelowTheSessionDirectory() {
    ModerationService.ChatMessage message = moderation.post(session, attendee, "hello club");
    Path expected =
        temp.resolve("club").resolve("sessions").resolve(session.id()).resolve("chat.db");
    assertTrue(Files.isRegularFile(expected));
    List<ModerationService.ChatMessage> history = moderation.history(session, observer);
    assertEquals(1, history.size());
    assertEquals("hello club", history.get(0).text());
    assertEquals(message.id(), history.get(0).id());

    assertThrows(
        ClubAccessDeniedException.class,
        new Executable() {
          @Override
          public void execute() {
            moderation.post(session, observer, "observers are read only");
          }
        });
  }

  @Test
  void muteAndUnmuteBlockAndAllowPosts() {
    moderation.mute(session, host, attendee);
    assertTrue(session.participant(attendee).isMuted());
    assertThrows(
        ClubAccessDeniedException.class,
        new Executable() {
          @Override
          public void execute() {
            moderation.post(session, attendee, "blocked");
          }
        });
    assertThrows(
        ClubAccessDeniedException.class,
        new Executable() {
          @Override
          public void execute() {
            moderation.mute(session, attendee, second);
          }
        });
    moderation.unmute(session, host, attendee);
    assertNotNull(moderation.post(session, attendee, "allowed"));
  }

  @Test
  void pinLockAndRemoveAreStaffActions() {
    ModerationService.ChatMessage message = moderation.post(session, attendee, "pin me");
    moderation.pin(session, host, message.id());
    assertEquals(1, moderation.pinned(session, attendee).size());
    assertTrue(moderation.pinned(session, attendee).get(0).isPinned());

    moderation.lock(session, host);
    assertTrue(session.isLocked());
    assertThrows(
        ClubLimitException.class,
        new Executable() {
          @Override
          public void execute() {
            moderation.post(session, attendee, "blocked by lock");
          }
        });
    moderation.unlock(session, host);

    moderation.remove(session, host, second);
    assertEquals(null, session.participant(second));
    assertThrows(
        ClubAccessDeniedException.class,
        new Executable() {
          @Override
          public void execute() {
            moderation.remove(session, attendee, observer);
          }
        });
  }

  @Test
  void helpQueueIsFifoAndObserversCannotJoin() {
    moderation.joinHelpQueue(session, attendee);
    moderation.joinHelpQueue(session, second);
    assertEquals(2, moderation.helpQueueSize(session));
    assertEquals(attendee, moderation.nextHelp(session, host));
    assertEquals(second, moderation.nextHelp(session, host));
    assertEquals(0, moderation.helpQueueSize(session));

    assertThrows(
        ClubAccessDeniedException.class,
        new Executable() {
          @Override
          public void execute() {
            moderation.joinHelpQueue(session, observer);
          }
        });
    assertThrows(
        IllegalStateException.class,
        new Executable() {
          @Override
          public void execute() {
            moderation.nextHelp(session, host);
          }
        });
    assertFalse(session.isLocked());
  }
}
