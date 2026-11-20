/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.domain.WorkspaceId;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

/** Authorized file operations for the browser workbench. */
@RestController
@RequestMapping("/api/v1/workspaces/{id}/files")
public class WorkspaceFileController {

    private final WorkspaceFiles files;

    private final AuthorizationService auth;

    public WorkspaceFileController(WorkspaceFiles files, AuthorizationService auth) {
        this.files = files;
        this.auth = auth;
    }

    @GetMapping
    public List<WorkspaceFiles.Entry> list(
            @PathVariable UUID id,
            @RequestParam(defaultValue = ".") String path,
            Principal principal) {
        WorkspaceId workspace = require(principal, id, "read");
        return files.forWorkspace(workspace).list(path);
    }

    @GetMapping("/document")
    public ResponseEntity<WorkspaceFiles.Document> read(
            @PathVariable UUID id, @RequestParam String path, Principal principal) {
        WorkspaceId workspace = require(principal, id, "read");
        WorkspaceFiles.Document document = files.forWorkspace(workspace).read(path);
        return ResponseEntity.ok().eTag(document.version()).body(document);
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(
            @PathVariable UUID id, @RequestParam String path, Principal principal) {
        WorkspaceFiles.Download download =
                files.forWorkspace(require(principal, id, "read")).download(path);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.size())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(new FileSystemResource(download.file()));
    }

    @GetMapping("/search")
    public List<WorkspaceFiles.SearchResult> search(
            @PathVariable UUID id,
            @RequestParam String q,
            @RequestParam(defaultValue = "100") int limit,
            Principal principal) {
        return files.forWorkspace(require(principal, id, "read")).search(q, limit);
    }

    @PutMapping("/document")
    public ResponseEntity<WorkspaceFiles.Document> save(
            @PathVariable UUID id,
            @RequestParam String path,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            @Valid @RequestBody SaveRequest request,
            Principal principal) {
        WorkspaceId workspace = require(principal, id, "write");
        boolean createOnly = "*".equals(ifNoneMatch);
        WorkspaceFiles.Document document =
                files.forWorkspace(workspace).save(path, request.content(), ifMatch, createOnly);
        return ResponseEntity.ok().eTag(document.version()).body(document);
    }

    @PostMapping("/directory")
    @ResponseStatus(HttpStatus.CREATED)
    public void createDirectory(
            @PathVariable UUID id, @RequestBody PathRequest request, Principal principal) {
        files.forWorkspace(require(principal, id, "write")).createDirectories(request.path());
    }

    @PostMapping("/rename")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rename(
            @PathVariable UUID id, @RequestBody RenameRequest request, Principal principal) {
        files.forWorkspace(require(principal, id, "write"))
                .rename(request.path(), request.newPath());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam String path, Principal principal) {
        files.forWorkspace(require(principal, id, "write")).delete(path);
    }

    private WorkspaceId require(Principal principal, UUID id, String permission) {
        UserId user = userId(principal);
        WorkspaceId workspace = new WorkspaceId(id);
        if (!auth.isAllowed(user, permission, workspace)) {
            throw new AccessDeniedException(user, permission);
        }
        return workspace;
    }

    private static UserId userId(Principal principal) {
        try {
            return new UserId(UUID.fromString(principal.getName()));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("principal is not a user id");
        }
    }

    public static final class SaveRequest {

        @NotNull
        @Size(max = 2 * 1024 * 1024)
        private final String content;

        public SaveRequest(@NotNull @Size(max = 2 * 1024 * 1024) String content) {
            this.content = content;
        }

        public String content() {
            return this.content;
        }

        public String getContent() {
            return this.content;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SaveRequest)) return false;
            SaveRequest other = (SaveRequest) o;
            return (java.util.Objects.equals(this.content, other.content));
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(this.content);
        }

        @Override
        public String toString() {
            return "SaveRequest[" + "content=" + this.content + "]";
        }
    }

    public static final class PathRequest {

        private final String path;

        public PathRequest(String path) {
            this.path = path;
        }

        public String path() {
            return this.path;
        }

        public String getPath() {
            return this.path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PathRequest)) return false;
            PathRequest other = (PathRequest) o;
            return (java.util.Objects.equals(this.path, other.path));
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(this.path);
        }

        @Override
        public String toString() {
            return "PathRequest[" + "path=" + this.path + "]";
        }
    }

    public static final class RenameRequest {

        private final String path;

        private final String newPath;

        public RenameRequest(String path, String newPath) {
            this.path = path;
            this.newPath = newPath;
        }

        public String path() {
            return this.path;
        }

        public String getPath() {
            return this.path;
        }

        public String newPath() {
            return this.newPath;
        }

        public String getNewPath() {
            return this.newPath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RenameRequest)) return false;
            RenameRequest other = (RenameRequest) o;
            return (java.util.Objects.equals(this.path, other.path))
                    && java.util.Objects.equals(this.newPath, other.newPath);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(this.path, this.newPath);
        }

        @Override
        public String toString() {
            return "RenameRequest[" + "path=" + this.path + ", " + "newPath=" + this.newPath + "]";
        }
    }
}
