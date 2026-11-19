/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Clones a workspace or a template into a new workspace with independent process state. */
@Component
public final class WorkspaceCloneService {
    private final WorkspaceService workspaces;
    private final String storageRoot;
    private final String nodeId;

    public WorkspaceCloneService(
            WorkspaceService workspaces,
            @Value("${jahaziel.storage.root:data}") String storageRoot,
            @Value("${jahaziel.node.id:local}") String nodeId) {
        if (workspaces == null) {
            throw new IllegalArgumentException("workspaces is required");
        }
        this.workspaces = workspaces;
        this.storageRoot = SafetyPaths.root(storageRoot).toString();
        this.nodeId = nodeId == null || nodeId.isBlank() ? "local" : nodeId;
    }

    /** Clones another workspace of the same owner, optionally taking only named files. */
    public WorkspaceCloneResult clone(
            UserId owner, WorkspaceId source, String name, List<String> subset) {
        validateName(name);
        Path sourceHome = SafetyPaths.home(storageRoot, source);
        return createAndCopy(owner, name, sourceHome, subset);
    }

    /** Clones a template from the data template library. */
    public WorkspaceCloneResult cloneFromTemplate(
            UserId owner, String template, String name, List<String> subset) {
        validateName(name);
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("template is required");
        }
        if (template.indexOf('/') >= 0
                || template.indexOf('\\') >= 0
                || template.equals(".")
                || template.equals("..")) {
            throw new IllegalArgumentException("template name is invalid");
        }
        Path source = SafetyPaths.templates(storageRoot).resolve(template);
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("template not found");
        }
        return createAndCopy(owner, name, source, subset);
    }

    private WorkspaceCloneResult createAndCopy(
            UserId owner, String name, Path source, List<String> subset) {
        Workspace created = workspaces.create(owner, name, 1, 512L, new NodeId(nodeId));
        WorkspaceId target = created.id();
        Path home = SafetyPaths.home(storageRoot, target);
        List<String> wanted = normalize(subset);
        final int[] copied = new int[1];
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.walkFileTree(
                        source,
                        new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult preVisitDirectory(
                                    Path directory, BasicFileAttributes attributes) {
                                if (!directory.equals(source)
                                        && ignored(directory.getFileName().toString())) {
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(
                                    Path file, BasicFileAttributes attributes) throws IOException {
                                String relative =
                                        source.relativize(file).toString().replace('\\', '/');
                                if (!wanted.isEmpty() && !wanted.contains(relative)) {
                                    return FileVisitResult.CONTINUE;
                                }
                                Path destination = SafetyPaths.resolveInside(home, relative);
                                Path parent = destination.getParent();
                                if (parent != null) {
                                    Files.createDirectories(parent);
                                }
                                Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
                                copied[0]++;
                                return FileVisitResult.CONTINUE;
                            }
                        });
            } catch (IOException failure) {
                throw new UncheckedIOException("cannot copy workspace files", failure);
            }
        }
        return new WorkspaceCloneResult(target.value().toString(), name, copied[0]);
    }

    private static List<String> normalize(List<String> subset) {
        List<String> result = new ArrayList<String>();
        if (subset == null) {
            return result;
        }
        for (int index = 0; index < subset.size(); index++) {
            String value = subset.get(index);
            if (value == null || value.isBlank()) {
                continue;
            }
            result.add(value.replace('\\', '/'));
        }
        return result;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 128) {
            throw new IllegalArgumentException("name must be 1 to 128 characters");
        }
        if (name.contains("/") || name.contains("\\") || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("name contains invalid characters");
        }
    }

    private static boolean ignored(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals(".git") || lower.equals("node_modules") || lower.equals(".cache");
    }
}
