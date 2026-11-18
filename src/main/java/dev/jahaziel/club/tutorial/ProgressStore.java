/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.tutorial;

import dev.jahaziel.identity.domain.UserId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * File-backed step progress under {@code data/club/users/}.
 *
 * <p>One append-only file per user stores {@code lesson|step|timestamp} lines. Reads support both
 * aggregate counts (the default view) and per-person counts (only when a session explicitly enables
 * a leaderboard).
 */
public final class ProgressStore {
  private static final String DELIMITER = "|";

  private final Path root;

  /**
   * Creates a store.
   *
   * @param root the club data root, usually {@code data/club}
   */
  public ProgressStore(Path root) {
    this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
  }

  /**
   * Records a completed step. Duplicate records are ignored.
   *
   * @param user the user
   * @param lessonId the lesson identifier
   * @param stepId the step identifier
   */
  public void markCompleted(UserId user, String lessonId, String stepId) {
    Path file = userFile(user);
    String record = lessonId + DELIMITER + stepId + DELIMITER;
    for (String line : readLines(file)) {
      if (line.startsWith(record)) {
        return;
      }
    }
    String line = record + Instant.now().toString() + "\n";
    try {
      Files.createDirectories(file.getParent());
      Files.write(
          file,
          line.getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot record club progress", failure);
    }
  }

  /**
   * Checks one completed step for one user.
   *
   * @param user the user
   * @param lessonId the lesson identifier
   * @param stepId the step identifier
   * @return true when the step was completed
   */
  public boolean isCompleted(UserId user, String lessonId, String stepId) {
    Path file = userFile(user);
    String record = lessonId + DELIMITER + stepId + DELIMITER;
    for (String line : readLines(file)) {
      if (line.startsWith(record)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Counts users who completed a step. This is the aggregate-only view.
   *
   * @param lessonId the lesson identifier
   * @param stepId the step identifier
   * @return the number of users with a completion record
   */
  public int aggregateCompleted(String lessonId, String stepId) {
    String record = lessonId + DELIMITER + stepId + DELIMITER;
    int count = 0;
    Path users = root.resolve("users");
    for (Path file : listFiles(users)) {
      for (String line : readLines(file)) {
        if (line.startsWith(record)) {
          count++;
          break;
        }
      }
    }
    return count;
  }

  /**
   * Counts completed steps for one user. Callers must only expose this when a leaderboard is
   * explicitly enabled for the session.
   *
   * @param user the user
   * @param lessonId the lesson identifier
   * @return the number of completed steps in the lesson
   */
  public int completedForUser(UserId user, String lessonId) {
    String record = lessonId + DELIMITER;
    int count = 0;
    for (String line : readLines(userFile(user))) {
      if (line.startsWith(record)) {
        count++;
      }
    }
    return count;
  }

  /** Returns the club data root. */
  public Path root() {
    return root;
  }

  private Path userFile(UserId user) {
    Objects.requireNonNull(user, "user");
    String safeId = user.toString().replace("-", "");
    return root.resolve("users").resolve(safeId + ".progress");
  }

  private static List<String> readLines(Path file) {
    if (!Files.isRegularFile(file)) {
      return new ArrayList<String>();
    }
    try {
      return new ArrayList<String>(Files.readAllLines(file, StandardCharsets.UTF_8));
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot read club progress", failure);
    }
  }

  private static List<Path> listFiles(Path dir) {
    if (!Files.isDirectory(dir)) {
      return new ArrayList<Path>();
    }
    List<Path> files = new ArrayList<Path>();
    DirectoryStream<Path> entries = null;
    try {
      entries = Files.newDirectoryStream(dir);
      for (Path candidate : entries) {
        if (Files.isRegularFile(candidate)) {
          files.add(candidate);
        }
      }
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot list club progress", failure);
    } finally {
      if (entries != null) {
        try {
          entries.close();
        } catch (IOException ignored) {
          // Close is best effort after a successful read.
        }
      }
    }
    return files;
  }
}
