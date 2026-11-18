/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.showcase;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.club.api.ClubAccessDeniedException;
import dev.jahaziel.identity.domain.UserId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-only, admin-curated showcase of project previews.
 *
 * <p>Entries hold descriptions, tags, and screenshot references only. The gallery deliberately has
 * no download, archive, or source-bytes API: the source of truth stays in git. Only administrators
 * may curate or promote entries.
 */
public final class ShowcaseGallery {
  private final AuthorizationService authorization;
  private final List<Project> projects = new ArrayList<Project>();

  /**
   * Creates the gallery.
   *
   * @param authorization the platform authorization service
   */
  public ShowcaseGallery(AuthorizationService authorization) {
    this.authorization = Objects.requireNonNull(authorization, "authorization");
  }

  /**
   * Curates a curated preview. Administrator role is required.
   *
   * @param admin the acting administrator
   * @param title the project title
   * @param description the project description
   * @param tags the project tags
   * @param screenshots screenshot references, never bytes
   * @param sourceLabel a human git reference, never a download
   * @return the new project
   */
  public Project curate(
      UserId admin,
      String title,
      String description,
      List<String> tags,
      List<String> screenshots,
      String sourceLabel) {
    if (!authorization.isAdmin(admin)) {
      throw new ClubAccessDeniedException("only administrators may curate the showcase");
    }
    Project project =
        new Project(UUID.randomUUID().toString(), title, description, tags, screenshots, sourceLabel);
    projects.add(project);
    return project;
  }

  /** Returns an unmodifiable snapshot of the curated projects. */
  public List<Project> list() {
    return Collections.unmodifiableList(new ArrayList<Project>(projects));
  }

  /**
   * Returns one project by identifier, or null when absent.
   *
   * @param id the project identifier
   * @return the project or null
   */
  public Project find(String id) {
    for (int index = 0; index < projects.size(); index++) {
      if (projects.get(index).id().equals(id)) {
        return projects.get(index);
      }
    }
    return null;
  }

  /** Returns the number of curated projects. */
  public int size() {
    return projects.size();
  }

  /** Immutable, read-only project preview. */
  public static final class Project {
    private final String id;
    private final String title;
    private final String description;
    private final List<String> tags;
    private final List<String> screenshots;
    private final String sourceLabel;

    /**
     * Creates a preview. Input lists are copied so callers cannot mutate the gallery.
     *
     * @param id the project identifier
     * @param title the project title
     * @param description the project description
     * @param tags the tags
     * @param screenshots screenshot references
     * @param sourceLabel a human git reference
     */
    public Project(
        String id,
        String title,
        String description,
        List<String> tags,
        List<String> screenshots,
        String sourceLabel) {
      if (id == null || id.trim().isEmpty() || title == null || title.trim().isEmpty()) {
        throw new IllegalArgumentException("id and title must not be blank");
      }
      this.id = id;
      this.title = title;
      this.description = description == null ? "" : description;
      this.tags = copy(tags);
      this.screenshots = copy(screenshots);
      this.sourceLabel = sourceLabel == null ? "" : sourceLabel;
    }

    private static List<String> copy(List<String> values) {
      if (values == null) {
        return Collections.emptyList();
      }
      return Collections.unmodifiableList(new ArrayList<String>(values));
    }

    /** Returns the project identifier. */
    public String id() {
      return id;
    }

    /** Returns the project title. */
    public String title() {
      return title;
    }

    /** Returns the project description. */
    public String description() {
      return description;
    }

    /** Returns the immutable tags. */
    public List<String> tags() {
      return tags;
    }

    /** Returns the immutable screenshot references. */
    public List<String> screenshots() {
      return screenshots;
    }

    /** Returns the human git reference. */
    public String sourceLabel() {
      return sourceLabel;
    }
  }
}
