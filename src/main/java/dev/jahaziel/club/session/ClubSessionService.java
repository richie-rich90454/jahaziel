/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.session;

import dev.jahaziel.club.api.ClubAccessDeniedException;
import dev.jahaziel.club.domain.ClubRoles;
import dev.jahaziel.club.domain.ClubSession;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Session lifecycle and workspace provisioning.
 *
 * <p>Every workspace is created through {@link WorkspaceService} and initialized through {@link
 * WorkspaceTaskService}; no club code copies or edits workspace internals. Observers never receive a
 * fork. The optional shared demo has exactly one controller; everyone else only observes it.
 */
public final class ClubSessionService {
  private static final int DEFAULT_VCPU = 2;
  private static final long DEFAULT_MEMORY_MIB = 2048L;

  private final WorkspaceService workspaces;
  private final WorkspaceTaskService tasks;
  private final NodeId node;

  /**
   * Creates the service.
   *
   * @param workspaces the existing workspace service
   * @param tasks the existing workspace task service
   * @param node the node that hosts club workspaces
   */
  public ClubSessionService(WorkspaceService workspaces, WorkspaceTaskService tasks, NodeId node) {
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    this.tasks = Objects.requireNonNull(tasks, "tasks");
    this.node = Objects.requireNonNull(node, "node");
  }

  /**
   * Builds a new session. The host is recorded as an instructor participant.
   *
   * @param host the instructor
   * @param template the workspace template name
   * @param startsAt session start
   * @param endsAt session end
   * @param capacity maximum roster size
   * @return the session with the host joined
   */
  public ClubSession createSession(
      UserId host, String template, Instant startsAt, Instant endsAt, int capacity) {
    ClubSession session =
        new ClubSession(
            UUID.randomUUID().toString(), host, template, startsAt, endsAt, capacity);
    session.join(host, ClubRoles.INSTRUCTOR);
    return session;
  }

  /**
   * Forks an isolated workspace for one participant from the session template.
   *
   * @param session the session
   * @param user an attendee, instructor, or assistant (never an observer)
   * @return the newly created workspace
   * @throws ClubAccessDeniedException when the user may not write or has not joined
   */
  public Workspace forkFor(ClubSession session, UserId user) {
    Objects.requireNonNull(session, "session");
    String role = session.roleOf(user);
    if (!ClubRoles.mayWrite(role)) {
      throw new ClubAccessDeniedException("role may not fork a workspace: " + role);
    }
    if (session.isLocked()) {
      throw new ClubAccessDeniedException("session is locked");
    }
    Workspace workspace =
        workspaces.create(user, workspaceName(session, user), DEFAULT_VCPU, DEFAULT_MEMORY_MIB, node);
    tasks.initialize(workspace.id(), session.template());
    session.setWorkspace(user, workspace.id());
    return workspace;
  }

  /**
   * Creates the optional shared demo workspace owned by the host.
   *
   * @param session the session
   * @return the demo workspace
   * @throws ClubAccessDeniedException when a demo already exists
   */
  public Workspace createDemo(ClubSession session) {
    Objects.requireNonNull(session, "session");
    if (session.isDemoEnabled()) {
      throw new ClubAccessDeniedException("demo workspace already exists");
    }
    Workspace workspace =
        workspaces.create(
            session.host(),
            "club-demo-" + session.id(),
            DEFAULT_VCPU,
            DEFAULT_MEMORY_MIB,
            node);
    tasks.initialize(workspace.id(), session.template());
    session.enableDemo(workspace.id(), session.host());
    return workspace;
  }

  /**
   * Returns the demo workspace for a follower. Observers receive the same read-only identifier.
   *
   * @param session the session
   * @param user a participant
   * @return the demo workspace identifier
   * @throws ClubAccessDeniedException when no demo exists or the user has not joined
   */
  public WorkspaceId followDemo(ClubSession session, UserId user) {
    Objects.requireNonNull(session, "session");
    if (!session.isDemoEnabled() || session.demoWorkspace() == null) {
      throw new ClubAccessDeniedException("no demo workspace is configured");
    }
    if (session.roleOf(user) == null) {
      throw new ClubAccessDeniedException("user has not joined this session");
    }
    return session.demoWorkspace();
  }

  /**
   * Asserts that a user is the single controller of the demo workspace.
   *
   * @param session the session
   * @param user the acting user
   * @throws ClubAccessDeniedException when the user is not the controller
   */
  public void controlDemo(ClubSession session, UserId user) {
    followDemo(session, user);
    if (session.demoController() == null || !session.demoController().equals(user)) {
      throw new ClubAccessDeniedException("only the demo controller may drive the demo workspace");
    }
    if (ClubRoles.OBSERVER.equals(session.roleOf(user))) {
      throw new ClubAccessDeniedException("observers cannot drive the demo workspace");
    }
  }

  /**
   * Transfers demo control. Only the current controller or the host may transfer, and only to a
   * writer who has joined.
   *
   * @param session the session
   * @param actor the acting user
   * @param next the new controller
   * @throws ClubAccessDeniedException when the transfer is not allowed
   */
  public void transferDemoControl(ClubSession session, UserId actor, UserId next) {
    Objects.requireNonNull(session, "session");
    if (!session.isDemoEnabled()) {
      throw new ClubAccessDeniedException("no demo workspace is configured");
    }
    boolean currentController =
        session.demoController() != null && session.demoController().equals(actor);
    if (!currentController && !session.host().equals(actor)) {
      throw new ClubAccessDeniedException("only the controller or host may transfer demo control");
    }
    if (!ClubRoles.mayWrite(session.roleOf(next))) {
      throw new ClubAccessDeniedException("new controller must be a writer in this session");
    }
    session.enableDemo(session.demoWorkspace(), next);
  }

  private static String workspaceName(ClubSession session, UserId user) {
    String shortId = user.toString().replace("-", "").substring(0, 8);
    return "club-" + session.id() + "-" + shortId;
  }
}
