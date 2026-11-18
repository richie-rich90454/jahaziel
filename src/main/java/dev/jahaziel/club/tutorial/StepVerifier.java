/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.tutorial;

import dev.jahaziel.club.tutorial.TutorialLesson.Step;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.Objects;

/**
 * Verifies a lesson step by running a declared workspace task and checking its exit status and
 * output. Only safe task labels and bounded output from the workspace task service are used.
 */
public final class StepVerifier {
  private static final String SUCCEEDED = "SUCCEEDED";

  private final WorkspaceTaskService tasks;

  /**
   * Creates the verifier.
   *
   * @param tasks the existing workspace task service
   */
  public StepVerifier(WorkspaceTaskService tasks) {
    this.tasks = Objects.requireNonNull(tasks, "tasks");
  }

  /**
   * Runs the validation task for a step.
   *
   * @param workspace the attendee's isolated workspace
   * @param step the lesson step
   * @return the bounded verification result
   */
  public Verification verify(WorkspaceId workspace, Step step) {
    Objects.requireNonNull(workspace, "workspace");
    Objects.requireNonNull(step, "step");
    if (!step.verifiable()) {
      return new Verification(false, "step has no declared validation task");
    }
    String taskId = step.taskId();
    WorkspaceTaskService.TaskRun run = tasks.startTask(workspace, taskId);
    if (run == null) {
      return new Verification(false, "validation task returned no run");
    }
    String status = run.status();
    String output = run.output() == null ? "" : run.output();
    Integer exitCode = run.exitCode();
    if ("RUNNING".equals(status)) {
      WorkspaceTaskService.TaskRun finished = tasks.taskRun(run.id());
      if (finished != null) {
        status = finished.status();
        output = finished.output() == null ? "" : finished.output();
        exitCode = finished.exitCode();
      }
    }
    boolean exitOk = exitCode != null && exitCode.intValue() == 0;
    boolean outputOk = step.expectedOutput().isEmpty() || output.contains(step.expectedOutput());
    boolean passed = SUCCEEDED.equals(status) && exitOk && outputOk;
    StringBuilder detail = new StringBuilder();
    detail.append("status=").append(status == null ? "none" : status);
    detail.append(" exit=").append(exitCode == null ? "none" : exitCode.toString());
    if (!outputOk) {
      detail.append(" missing expected output");
    }
    return new Verification(passed, detail.toString());
  }

  /** Bounded result of one step verification. */
  public static final class Verification {
    private final boolean passed;
    private final String detail;

    /**
     * Creates a result.
     *
     * @param passed whether the step passed
     * @param detail a short, bounded reason
     */
    public Verification(boolean passed, String detail) {
      this.passed = passed;
      this.detail = detail == null ? "" : detail;
    }

    /** Returns whether the step passed. */
    public boolean passed() {
      return passed;
    }

    /** Returns the short reason. */
    public String detail() {
      return detail;
    }
  }
}
