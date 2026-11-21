/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/** Restore cleanup must remove read-only snapshot trees the way backup archives preserve them. */
class OpsFilesDeleteTreeTest {
    @TempDir Path temp;

    @Test
    void deleteTreeRemovesReadOnlySnapshotTree() throws Exception {
        Path staging = Files.createDirectory(temp.resolve("restore-verify-1"));
        Path snap = staging.resolve("data/workspaces/w-id/snaps/acceptance-1");
        Files.createDirectories(snap);
        Files.writeString(snap.resolve("package.json"), "{}");
        Files.writeString(snap.getParent().resolve("manifest.json"), "{}");

        readOnly(snap);
        readOnly(snap.resolve("package.json"));

        OpsFiles.deleteTree(staging, staging);

        assertFalse(Files.exists(staging));
    }

    private static void readOnly(Path path) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Set<PosixFilePermission> perms =
                    EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_EXECUTE,
                            PosixFilePermission.GROUP_READ,
                            PosixFilePermission.GROUP_EXECUTE,
                            PosixFilePermission.OTHERS_READ,
                            PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(path, perms);
        } else {
            path.toFile().setWritable(false, false);
        }
    }
}
