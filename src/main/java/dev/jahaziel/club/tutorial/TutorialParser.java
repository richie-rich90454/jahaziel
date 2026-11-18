/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.tutorial;

import dev.jahaziel.club.tutorial.TutorialLesson.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal parser for club lesson markdown.
 *
 * <p>Lesson metadata lives in a leading {@code ---} frontmatter block. Each step is a {@code ##
 * <id>} heading followed by one-line frontmatter fields: {@code title}, {@code objective}, {@code
 * hint}, {@code solution_diff}, {@code validation_command}, and optional {@code expected_output}.
 * Parsing is deliberately line based and dependency free; unknown keys and prose lines are ignored.
 */
public final class TutorialParser {

  /**
   * Parses a lesson.
   *
   * @param markdown the lesson markdown
   * @return the parsed lesson
   * @throws IllegalArgumentException when no step is present
   */
  public TutorialLesson parse(String markdown) {
    if (markdown == null) {
      throw new IllegalArgumentException("markdown must not be null");
    }
    String[] lines = markdown.split("\n", -1);
    String lessonId = "lesson";
    String lessonTitle = "";
    String lessonObjective = "";
    boolean frontOpen = false;
    boolean frontSeen = false;

    String stepId = null;
    String stepTitle = "";
    String stepObjective = "";
    String stepHint = "";
    String stepDiff = "";
    String stepCommand = "";
    String stepExpected = "";
    List<Step> steps = new ArrayList<Step>();

    for (int index = 0; index < lines.length; index++) {
      String line = lines[index];
      if (line.endsWith("\r")) {
        line = line.substring(0, line.length() - 1);
      }
      String trimmed = line.trim();
      if (trimmed.equals("---")) {
        if (stepId != null) {
          steps.add(
              new Step(
                  stepId,
                  stepTitle,
                  stepObjective,
                  stepHint,
                  stepDiff,
                  stepCommand,
                  stepExpected));
          stepId = null;
          stepTitle = "";
          stepObjective = "";
          stepHint = "";
          stepDiff = "";
          stepCommand = "";
          stepExpected = "";
        }
        if (!frontSeen) {
          frontOpen = !frontOpen;
          if (!frontOpen) {
            frontSeen = true;
          }
        }
        continue;
      }
      if (trimmed.startsWith("## ")) {
        if (stepId != null) {
          steps.add(
              new Step(
                  stepId,
                  stepTitle,
                  stepObjective,
                  stepHint,
                  stepDiff,
                  stepCommand,
                  stepExpected));
        }
        stepId = trimmed.substring(3).trim();
        stepTitle = "";
        stepObjective = "";
        stepHint = "";
        stepDiff = "";
        stepCommand = "";
        stepExpected = "";
        continue;
      }
      if (trimmed.indexOf(':') < 0 || trimmed.startsWith("#")) {
        continue;
      }
      String key = keyOf(trimmed);
      String value = valueOf(trimmed);
      if (stepId != null) {
        if ("title".equals(key)) {
          stepTitle = value;
        } else if ("objective".equals(key)) {
          stepObjective = value;
        } else if ("hint".equals(key)) {
          stepHint = value;
        } else if ("solution_diff".equals(key)) {
          stepDiff = value;
        } else if ("validation_command".equals(key)) {
          stepCommand = value;
        } else if ("expected_output".equals(key)) {
          stepExpected = value;
        }
      } else if (frontOpen) {
        if ("id".equals(key)) {
          lessonId = value;
        } else if ("title".equals(key)) {
          lessonTitle = value;
        } else if ("objective".equals(key)) {
          lessonObjective = value;
        }
      }
    }
    if (stepId != null) {
      steps.add(
          new Step(stepId, stepTitle, stepObjective, stepHint, stepDiff, stepCommand, stepExpected));
    }
    if (steps.isEmpty()) {
      throw new IllegalArgumentException("lesson has no steps");
    }
    return new TutorialLesson(lessonId, lessonTitle, lessonObjective, steps);
  }

  private static String keyOf(String line) {
    return line.substring(0, line.indexOf(':')).trim().toLowerCase(Locale.ROOT);
  }

  private static String valueOf(String line) {
    return line.substring(line.indexOf(':') + 1).trim();
  }
}
