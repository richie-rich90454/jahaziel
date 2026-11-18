/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.tutorial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A markdown lesson parsed into ordered, verifiable steps. */
public final class TutorialLesson {
  private static final int MAX_STEPS = 64;

  private final String id;
  private final String title;
  private final String objective;
  private final List<Step> steps;

  /**
   * Creates a lesson.
   *
   * @param id the file-safe lesson identifier
   * @param title the human title
   * @param objective the lesson goal
   * @param steps the ordered steps
   */
  public TutorialLesson(String id, String title, String objective, List<Step> steps) {
    if (id == null || id.trim().isEmpty()) {
      throw new IllegalArgumentException("lesson id must not be blank");
    }
    this.id = id;
    this.title = title == null ? "" : title;
    this.objective = objective == null ? "" : objective;
    if (steps == null || steps.isEmpty() || steps.size() > MAX_STEPS) {
      throw new IllegalArgumentException("lesson must have between 1 and " + MAX_STEPS + " steps");
    }
    this.steps = Collections.unmodifiableList(new ArrayList<Step>(steps));
  }

  /** Returns the lesson identifier. */
  public String id() {
    return id;
  }

  /** Returns the lesson title. */
  public String title() {
    return title;
  }

  /** Returns the lesson objective. */
  public String objective() {
    return objective;
  }

  /** Returns the ordered steps. */
  public List<Step> steps() {
    return steps;
  }

  /**
   * Returns one step by identifier, or null when absent.
   *
   * @param stepId the step identifier
   * @return the step or null
   */
  public Step step(String stepId) {
    for (int index = 0; index < steps.size(); index++) {
      if (steps.get(index).id().equals(stepId)) {
        return steps.get(index);
      }
    }
    return null;
  }

  /** One verifiable step of a lesson. */
  public static final class Step {
    private final String id;
    private final String title;
    private final String objective;
    private final String hint;
    private final String solutionDiff;
    private final String validationCommand;
    private final String expectedOutput;

    /**
     * Creates a step.
     *
     * @param id the step identifier
     * @param title the step title
     * @param objective the step objective
     * @param hint the hint shown on request
     * @param solutionDiff the reference solution diff
     * @param validationCommand the declared workspace task id, prefixed with {@code task:}
     * @param expectedOutput optional text that must appear in task output
     */
    public Step(
        String id,
        String title,
        String objective,
        String hint,
        String solutionDiff,
        String validationCommand,
        String expectedOutput) {
      if (id == null || id.trim().isEmpty()) {
        throw new IllegalArgumentException("step id must not be blank");
      }
      this.id = id;
      this.title = text(title);
      this.objective = text(objective);
      this.hint = text(hint);
      this.solutionDiff = text(solutionDiff);
      this.validationCommand = text(validationCommand);
      this.expectedOutput = text(expectedOutput);
    }

    private static String text(String value) {
      return value == null ? "" : value;
    }

    /** Returns the step identifier. */
    public String id() {
      return id;
    }

    /** Returns the step title. */
    public String title() {
      return title;
    }

    /** Returns the step objective. */
    public String objective() {
      return objective;
    }

    /** Returns the optional hint. */
    public String hint() {
      return hint;
    }

    /** Returns the optional reference solution diff, never applied automatically. */
    public String solutionDiff() {
      return solutionDiff;
    }

    /** Returns the validation command. */
    public String validationCommand() {
      return validationCommand;
    }

    /** Returns the optional expected output fragment. */
    public String expectedOutput() {
      return expectedOutput;
    }

    /**
     * Returns the declared task identifier referenced by this step.
     *
     * @return the task id after the {@code task:} prefix
     * @throws IllegalArgumentException when the validation command is not a declared task
     */
    public String taskId() {
      String command = validationCommand.trim();
      if (!command.startsWith("task:")) {
        throw new IllegalArgumentException("validation command must start with task:");
      }
      String taskId = command.substring("task:".length()).trim();
      if (taskId.isEmpty()) {
        throw new IllegalArgumentException("validation command has no task id");
      }
      return taskId;
    }

    /**
     * Checks that this step carries the fields verification needs.
     *
     * @return true when a validation task is declared
     */
    public boolean verifiable() {
      return !validationCommand.trim().isEmpty() && validationCommand.trim().startsWith("task:");
    }
  }
}
