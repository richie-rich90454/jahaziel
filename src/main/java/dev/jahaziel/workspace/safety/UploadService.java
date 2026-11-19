/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bounded, extension-checked, ETag-safe uploads through the existing workspace file API. */
@Component
public final class UploadService {
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;

    private final WorkspaceFiles files;
    private final OperationHistory history;
    private final String storageRoot;
    private final List<String> allowed = new ArrayList<String>();

    public UploadService(
            WorkspaceFiles files,
            OperationHistory history,
            @Value("${jahaziel.storage.root:data}") String storageRoot) {
        if (files == null) {
            throw new IllegalArgumentException("files is required");
        }
        if (history == null) {
            throw new IllegalArgumentException("history is required");
        }
        this.files = files;
        this.history = history;
        this.storageRoot = SafetyPaths.root(storageRoot).toString();
        String[] extensions = {
            "txt", "md", "java", "json", "js", "jsx", "ts", "tsx", "css", "scss",
            "html", "htm", "xml", "yml", "yaml", "py", "sh", "sql", "csv", "c",
            "h", "cpp", "go", "rs", "rb", "php", "properties", "env", "log", "toml"
        };
        for (int index = 0; index < extensions.length; index++) {
            allowed.add(extensions[index]);
        }
    }

    /** Writes one bounded upload, verifying the ETag on updates. */
    public synchronized WorkspaceFiles.Document upload(
            WorkspaceId workspace,
            String relativePath,
            byte[] content,
            String expectedVersion,
            String actor) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        if (SafetyPaths.isGitPath(relativePath)) {
            throw new IllegalArgumentException("git metadata cannot be uploaded");
        }
        requireExtension(relativePath);
        if (content == null) {
            throw new IllegalArgumentException("content is required");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("upload exceeds the two megabyte limit");
        }
        String text = decodeUtf8(content);
        Path home = SafetyPaths.home(storageRoot, workspace);
        Path resolved = SafetyPaths.resolveInside(home, relativePath);
        boolean exists = Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS);
        String oldText = null;
        if (exists) {
            oldText = files.forWorkspace(workspace).read(relativePath).content();
        }
        WorkspaceFiles.Document saved =
                files.forWorkspace(workspace).save(relativePath, text, expectedVersion, !exists);
        byte[] oldBytes = oldText == null ? null : oldText.getBytes(StandardCharsets.UTF_8);
        history.recordWrite(workspace, relativePath, oldBytes, content, actor);
        return saved;
    }

    private void requireExtension(String relativePath) {
        int slash = Math.max(relativePath.lastIndexOf('/'), relativePath.lastIndexOf('\\'));
        String name = slash < 0 ? relativePath : relativePath.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new IllegalArgumentException("upload needs a supported file extension");
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!allowed.contains(extension)) {
            throw new IllegalArgumentException("file extension is not allowed");
        }
    }

    private static String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException("upload must be valid UTF-8 text");
        }
    }
}
