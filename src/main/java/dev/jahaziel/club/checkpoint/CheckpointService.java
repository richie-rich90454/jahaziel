/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.checkpoint;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.club.api.ClubAccessDeniedException;
import dev.jahaziel.club.domain.ClubSession;
import dev.jahaziel.club.showcase.ShowcaseGallery;
import dev.jahaziel.club.showcase.ShowcaseGallery.Project;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Checkpoint submissions, instructor feedback, and showcase promotion.
 *
 * <p>A submission takes a real snapshot through {@link SnapshotService}. Instructors may read a
 * bounded line diff and leave feedback. Promotion into the {@link ShowcaseGallery} requires both
 * session staff and the platform administrator role, and publishes preview metadata rather than the
 * source itself.
 */
public final class CheckpointService {
  private final SnapshotService snapshots;
  private final ShowcaseGallery gallery;
  private final AuthorizationService authorization;
  private final Map<String, Submission> submissions = new HashMap<String, Submission>();

  /**
   * Creates the service.
   *
   * @param snapshots the existing snapshot service
   * @param gallery the curated showcase
   * @param authorization the platform authorization service
   */
  public CheckpointService(
      SnapshotService snapshots, ShowcaseGallery gallery, AuthorizationService authorization) {
    this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    this.gallery = Objects.requireNonNull(gallery, "gallery");
    this.authorization = Objects.requireNonNull(authorization, "authorization");
  }

  /**
   * Submits a checkpoint snapshot for one attendee.
   *
   * @param session the session
   * @param user the submitting attendee
   * @param workspace the attendee's isolated workspace
   * @param tag the snapshot tag
   * @param note the submitter's note
   * @param content the submitted text, used only for bounded instructor diffs
   * @return the stored submission
   */
  public Submission submit(
      ClubSession session,
      UserId user,
      WorkspaceId workspace,
      String tag,
      String note,
      String content) {
    Objects.requireNonNull(session, "session");
    if (!session.mayWrite(user)) {
      throw new ClubAccessDeniedException("user may not submit in this session");
    }
    if (tag == null || tag.trim().isEmpty()) {
      throw new IllegalArgumentException("snapshot tag must not be blank");
    }
    if (!workspace.equals(session.workspaceOf(user))) {
      throw new ClubAccessDeniedException("workspace is not the attendee's isolated fork");
    }
    String createdBy = user.value().toString();
    snapshots.create(workspace, tag, SnapshotKind.MANUAL, createdBy);
    Submission submission =
        new Submission(
            UUID.randomUUID().toString(),
            session.id(),
            user,
            workspace,
            tag,
            note,
            content == null ? "" : content);
    submissions.put(submission.id(), submission);
    return submission;
  }

  /**
   * Returns one submission.
   *
   * @param id the submission identifier
   * @return the submission
   * @throws IllegalArgumentException when the identifier is unknown
   */
  public Submission get(String id) {
    Submission submission = submissions.get(id);
    if (submission == null) {
      throw new IllegalArgumentException("unknown checkpoint submission: " + id);
    }
    return submission;
  }

  /**
   * Builds a bounded line diff for an instructor.
   *
   * @param session the session
   * @param actor the instructor or assistant
   * @param submissionId the submission identifier
   * @param baseline the reference text to compare against
   * @return the diff text
   */
  public String diff(ClubSession session, UserId actor, String submissionId, String baseline) {
    requireStaff(session, actor);
    return Diff.lines(baseline == null ? "" : baseline, get(submissionId).content());
  }

  /**
   * Records instructor feedback.
   *
   * @param session the session
   * @param actor the instructor or assistant
   * @param submissionId the submission identifier
   * @param feedback the feedback text
   */
  public void feedback(
      ClubSession session, UserId actor, String submissionId, String feedback) {
    requireStaff(session, actor);
    if (feedback == null || feedback.trim().isEmpty()) {
      throw new IllegalArgumentException("feedback must not be blank");
    }
    get(submissionId).setFeedback(feedback);
  }

  /**
   * Promotes a submission into the admin-curated showcase.
   *
   * @param session the session
   * @param actor the promoting administrator and staff member
   * @param submissionId the submission identifier
   * @param title the showcase title
   * @param tags the showcase tags
   * @param screenshots screenshot references
   * @return the published preview
   */
  public Project promote(
      ClubSession session,
      UserId actor,
      String submissionId,
      String title,
      List<String> tags,
      List<String> screenshots) {
    requireStaff(session, actor);
    if (!authorization.isAdmin(actor)) {
      throw new ClubAccessDeniedException("only administrators may promote into the showcase");
    }
    Submission submission = get(submissionId);
    Project project =
        gallery.curate(
            actor,
            title,
            submission.note(),
            tags,
            screenshots,
            "checkpoint " + submission.snapshotTag());
    submission.setPromotedTo(project.id());
    return project;
  }

  /** Returns the curated showcase gallery used for promotion. */
  public ShowcaseGallery gallery() {
    return gallery;
  }

  /** Returns all submissions for a session in submission order. */
  public List<Submission> bySession(String sessionId) {
    List<Submission> found = new ArrayList<Submission>();
    for (Map.Entry<String, Submission> entry : submissions.entrySet()) {
      if (entry.getValue().sessionId().equals(sessionId)) {
        found.add(entry.getValue());
      }
    }
    return Collections.unmodifiableList(found);
  }

  private static void requireStaff(ClubSession session, UserId actor) {
    if (!session.isStaff(actor)) {
      throw new ClubAccessDeniedException("only session staff may review checkpoints");
    }
  }

  /** Immutable checkpoint submission. */
  public static final class Submission {
    private final String id;
    private final String sessionId;
    private final UserId user;
    private final WorkspaceId workspace;
    private final String snapshotTag;
    private final String note;
    private final String content;
    private final Instant submittedAt;
    private String feedback;
    private String promotedTo;

    /**
     * Creates a submission.
     *
     * @param id the submission identifier
     * @param sessionId the session identifier
     * @param user the submitter
     * @param workspace the source workspace
     * @param snapshotTag the snapshot tag
     * @param note the submitter's note
     * @param content the submitted text
     */
    public Submission(
        String id,
        String sessionId,
        UserId user,
        WorkspaceId workspace,
        String snapshotTag,
        String note,
        String content) {
      this.id = Objects.requireNonNull(id, "id");
      this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
      this.user = Objects.requireNonNull(user, "user");
      this.workspace = Objects.requireNonNull(workspace, "workspace");
      this.snapshotTag = Objects.requireNonNull(snapshotTag, "snapshotTag");
      this.note = note == null ? "" : note;
      this.content = content;
      this.submittedAt = Instant.now();
    }

    /** Returns the submission identifier. */
    public String id() {
      return id;
    }

    /** Returns the session identifier. */
    public String sessionId() {
      return sessionId;
    }

    /** Returns the submitting user. */
    public UserId user() {
      return user;
    }

    /** Returns the source workspace. */
    public WorkspaceId workspace() {
      return workspace;
    }

    /** Returns the snapshot tag. */
    public String snapshotTag() {
      return snapshotTag;
    }

    /** Returns the submitter's note. */
    public String note() {
      return note;
    }

    /** Returns the submitted text. */
    public String content() {
      return content;
    }

    /** Returns the submission timestamp. */
    public Instant submittedAt() {
      return submittedAt;
    }

    /** Returns the instructor feedback, or null before review. */
    public String feedback() {
      return feedback;
    }

    /** Returns the showcase project id this submission was promoted to, or null. */
    public String promotedTo() {
      return promotedTo;
    }

    /** Sets instructor feedback. */
    public void setFeedback(String feedback) {
      this.feedback = feedback;
    }

    /** Marks the submission as promoted. */
    public void setPromotedTo(String promotedTo) {
      this.promotedTo = promotedTo;
    }
  }

  /** Small bounded line diff used for instructor review. */
  static final class Diff {
    private static final int MAX_LINES = 200;

    private Diff() {}

    /**
     * Produces a bounded line-oriented diff.
     *
     * @param baseline the reference text
     * @param submitted the submitted text
     * @return the diff text
     */
    static String lines(String baseline, String submitted) {
      String[] before = baseline.split("\n", -1);
      String[] after = submitted.split("\n", -1);
      StringBuilder result = new StringBuilder();
      result.append("--- baseline\n");
      result.append("+++ submission\n");
      int shared = Math.min(before.length, after.length);
      int emitted = 0;
      for (int index = 0; index < shared && emitted < MAX_LINES; index++) {
        if (!before[index].equals(after[index])) {
          result.append("-").append(before[index]).append('\n');
          result.append("+").append(after[index]).append('\n');
          emitted += 2;
        }
      }
      for (int index = shared; index < after.length && emitted < MAX_LINES; index++) {
        result.append("+").append(after[index]).append('\n');
        emitted++;
      }
      return result.toString();
    }
  }
}
