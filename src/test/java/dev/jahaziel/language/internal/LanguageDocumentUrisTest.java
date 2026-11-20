/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jahaziel.workspace.domain.WorkspaceId;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

class LanguageDocumentUrisTest {
    @TempDir Path temporary;

    @Test
    void mapsEncodedRelativeWorkspaceUriToConfinedPath() throws Exception {
        WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
        Path root = Files.createDirectories(temporary.resolve("workspace"));
        Path folder = Files.createDirectories(root.resolve("src"));
        Path file = Files.createFile(folder.resolve("Main File.rs"));
        String uri = "jahaziel://workspace/" + workspace.value() + "/src/Main%20File.rs";

        Path canonical = file.toRealPath();
        assertEquals(canonical, LanguageDocumentUris.toFilePath(uri, workspace, root));
        assertEquals(
                "jahaziel://workspace/" + workspace.value() + "/src/Main%20File.rs",
                LanguageDocumentUris.toVirtualUri(canonical.toUri().toString(), workspace, root));
    }

    @Test
    void rejectsTraversalAndOtherWorkspaceVirtualUris() throws Exception {
        WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
        Path root = Files.createDirectories(temporary.resolve("workspace"));

        assertThrows(
                IOException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        LanguageDocumentUris.toFilePath(
                                "jahaziel://workspace/" + workspace.value() + "/%2e%2e/secret",
                                workspace,
                                root);
                    }
                });
        assertThrows(
                IOException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        LanguageDocumentUris.toFilePath(
                                "jahaziel://workspace/" + UUID.randomUUID() + "/src/Main.rs",
                                workspace,
                                root);
                    }
                });
        assertThrows(
                IOException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        LanguageDocumentUris.toFilePath(
                                root.resolve("src/Main.rs").toUri().toString(), workspace, root);
                    }
                });
    }

    @Test
    void rejectsSymlinkEscape() throws Exception {
        Assumptions.assumeTrue(
                System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "symlink security case runs on the Linux server target");
        WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
        Path root = Files.createDirectories(temporary.resolve("workspace"));
        Path outside = Files.createDirectories(temporary.resolve("outside"));
        Files.createFile(outside.resolve("secret.rs"));
        Files.createSymbolicLink(root.resolve("linked"), outside);

        assertThrows(
                IOException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        LanguageDocumentUris.toFilePath(
                                "jahaziel://workspace/" + workspace.value() + "/linked/secret.rs",
                                workspace,
                                root);
                    }
                });
    }
}
