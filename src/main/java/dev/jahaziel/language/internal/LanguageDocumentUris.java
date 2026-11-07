/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Maps browser-visible virtual document URIs to confined server paths. */
final class LanguageDocumentUris {
  private static final String VIRTUAL_SCHEME = "jahaziel";
  private static final String VIRTUAL_HOST = "workspace";
  private static final String OUTSIDE_WORKSPACE_URI = "jahaziel://outside-workspace/";

  private LanguageDocumentUris() {}

  static Path toFilePath(String value, WorkspaceId workspaceId, Path workspaceHome)
      throws IOException {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(workspaceId, "workspaceId");
    Path root = workspaceHome.toRealPath();
    try {
      URI uri = URI.create(value);
      if (!VIRTUAL_SCHEME.equals(uri.getScheme())
          || !VIRTUAL_HOST.equals(uri.getHost())
          || uri.getPort() != -1
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || uri.getFragment() != null) {
        throw new IOException("language services accept only virtual workspace URIs");
      }
      String uriPath = uri.getPath();
      String prefix = "/" + workspaceId.value() + "/";
      if (uriPath == null || !uriPath.startsWith(prefix)) {
        throw new IOException("language-server document is outside the authorized workspace");
      }
      String relative = uriPath.substring(prefix.length());
      if (relative.isBlank() || relative.indexOf('\\') >= 0 || relative.indexOf('\0') >= 0) {
        throw new IOException("language-server document path is invalid");
      }
      Path relativePath = Path.of(relative).normalize();
      if (relativePath.isAbsolute()
          || relativePath.startsWith("..")
          || !relativePath.toString().replace('\\', '/').equals(relative)) {
        throw new IOException("language-server document path is invalid");
      }
      Path target = root.resolve(relativePath).normalize();
      if (!target.startsWith(root)) {
        throw new IOException("language-server document escapes the workspace");
      }
      rejectSymlinks(root, target);
      return target;
    } catch (IllegalArgumentException invalid) {
      throw new IOException("language-server document URI is invalid", invalid);
    }
  }

  static String toVirtualUri(String value, WorkspaceId workspaceId, Path workspaceHome) {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(workspaceId, "workspaceId");
    try {
      URI uri = URI.create(value);
      if (!"file".equals(uri.getScheme())) {
        return value;
      }
      Path root = workspaceHome.toRealPath();
      Path file = Path.of(uri).toAbsolutePath().normalize();
      if (!file.startsWith(root)) {
        return OUTSIDE_WORKSPACE_URI;
      }
      rejectSymlinks(root, file);
      String relative = root.relativize(file).toString().replace('\\', '/');
      if (relative.isEmpty()) {
        return "jahaziel://workspace/" + workspaceId.value() + "/";
      }
      StringBuilder encoded = new StringBuilder();
      for (String segment : relative.split("/", -1)) {
        if (encoded.length() > 0) encoded.append('/');
        encoded.append(new URI(null, null, segment, null).getRawPath());
      }
      return "jahaziel://workspace/" + workspaceId.value() + "/" + encoded;
    } catch (Exception invalidOrOutside) {
      return OUTSIDE_WORKSPACE_URI;
    }
  }

  private static void rejectSymlinks(Path root, Path target) throws IOException {
    if (!target.startsWith(root)) {
      throw new IOException("language-server document escapes the workspace");
    }
    Path current = root;
    for (Path segment : root.relativize(target)) {
      current = current.resolve(segment);
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isSymbolicLink(current)) {
          throw new IOException("language-server document path contains a symbolic link");
        }
        if (!current.toRealPath().startsWith(root)) {
          throw new IOException("language-server document escapes the workspace");
        }
      }
    }
  }
}
