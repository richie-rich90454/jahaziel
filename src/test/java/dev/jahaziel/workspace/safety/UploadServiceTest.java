/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.workspace.api.WorkspaceFileConflictException;
import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.internal.WorkspaceFilesServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Uploads reject oversized, wrongly typed, and stale writes. */
final class UploadServiceTest {
    @Test
    void uploadsWithEtag(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final UploadService service =
                new UploadService(
                        new WorkspaceFilesServiceImpl(data), new OperationHistory(data), data);
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        WorkspaceFiles.Document first =
                service.upload(workspace, "hello.txt", "hello".getBytes(StandardCharsets.UTF_8), null, "user");
        assertEquals("hello.txt", first.path());
        assertTrue(!first.version().isEmpty());
        Path file = SafetyPaths.home(data, workspace).resolve("hello.txt");
        assertEquals("hello", SafetyFixtures.read(file));

        WorkspaceFiles.Document second =
                service.upload(
                        workspace, "hello.txt", "again".getBytes(StandardCharsets.UTF_8), first.version(), "user");
        assertEquals("again", SafetyFixtures.read(file));
        assertTrue(!first.version().equals(second.version()));
    }

    @Test
    void rejectsOversizedUpload(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final UploadService service =
                new UploadService(
                        new WorkspaceFilesServiceImpl(data), new OperationHistory(data), data);
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        final byte[] big = new byte[UploadService.MAX_FILE_BYTES + 1];
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.upload(workspace, "big.txt", big, null, "user");
                    }
                });
    }

    @Test
    void rejectsUnsupportedExtension(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final UploadService service =
                new UploadService(
                        new WorkspaceFilesServiceImpl(data), new OperationHistory(data), data);
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.upload(workspace, "bad.exe", new byte[] {1}, null, "user");
                    }
                });
    }

    @Test
    void rejectsStaleEtag(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final UploadService service =
                new UploadService(
                        new WorkspaceFilesServiceImpl(data), new OperationHistory(data), data);
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        service.upload(workspace, "stale.txt", "one".getBytes(StandardCharsets.UTF_8), null, "user");
        assertThrows(
                WorkspaceFileConflictException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.upload(
                                workspace,
                                "stale.txt",
                                "two".getBytes(StandardCharsets.UTF_8),
                                "deadbeef",
                                "user");
                    }
                });
    }
}
