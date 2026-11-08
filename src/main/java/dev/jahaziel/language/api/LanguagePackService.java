/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.api;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Shared, opt-in language-tool cache and workspace selections. */
public interface LanguagePackService {
  /** Returns the pinned server-owned pack catalog for the current host. */
  List<Pack> catalog();

  /** Returns catalog entries annotated with this workspace's selected state. */
  List<Pack> forWorkspace(WorkspaceId workspaceId);

  /** Installs a catalog pack if needed and selects it for the workspace. */
  Pack select(WorkspaceId workspaceId, String languageId);

  /** Removes a workspace selection without deleting the shared cached tool. */
  void deselect(WorkspaceId workspaceId, String languageId);

  /** Clears all selected-pack references after a workspace is deleted. */
  void clearWorkspace(WorkspaceId workspaceId);

  /** Resolves an installed, selected launch artifact for an authorized language session. */
  Path artifactFor(WorkspaceId workspaceId, String languageId);

  /** Reports whether this server has a fixed, server-owned launch contract for this language. */
  boolean supportsLanguageService(String languageId);

  /** Builds the fixed server-owned launch command for an authorized language-service session. */
  List<String> launchCommandFor(WorkspaceId workspaceId, String languageId, Path stateDirectory);

  /** Returns a fixed server-owned environment for a language service. */
  default Map<String, String> launchEnvironmentFor(
      WorkspaceId workspaceId, String languageId, Path workspaceHome, Path stateDirectory) {
    return Map.of();
  }

  /** Returns server-owned initialization options; clients cannot select executable paths. */
  default Map<String, Object> initializationOptionsFor(WorkspaceId workspaceId, String languageId) {
    return Map.of();
  }

  /** Reports cache usage without changing files. */
  CacheReport report();

  /** Lists reclaimable versions, or removes them when dryRun is false. */
  PruneReport prune(boolean dryRun);

  /** One immutable, pinned language-pack release. */
  record Pack(
      String id,
      String name,
      String version,
      long downloadBytes,
      String sha256,
      List<String> checksums,
      List<String> supportedArchitectures,
      boolean installed,
      boolean selected,
      String availability,
      boolean supportedByTemplate,
      String installationNote) {
    /** Returns this pack with its availability for the workspace template attached. */
    public Pack withTemplateSupport(boolean supported) {
      return new Pack(
          id,
          name,
          version,
          downloadBytes,
          sha256,
          checksums,
          supportedArchitectures,
          installed,
          selected,
          availability,
          supported,
          installationNote);
    }
  }

  /** Current cache usage. */
  record CacheReport(long bytes, List<Pack> packs) {}

  /** Dry-run or completed prune result. */
  record PruneReport(boolean dryRun, long bytesReclaimed, List<String> versions) {}
}
