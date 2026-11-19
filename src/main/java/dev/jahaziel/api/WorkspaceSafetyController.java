/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.snapshot.diff.WorkspaceDiff;
import dev.jahaziel.snapshot.diff.WorkspaceDiffService;
import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.safety.BulkFileService;
import dev.jahaziel.workspace.safety.BulkResult;
import dev.jahaziel.workspace.safety.FileWatchService;
import dev.jahaziel.workspace.safety.OperationHistory;
import dev.jahaziel.workspace.safety.OperationRecord;
import dev.jahaziel.workspace.safety.TrashEntry;
import dev.jahaziel.workspace.safety.TrashService;
import dev.jahaziel.workspace.safety.UploadService;
import dev.jahaziel.workspace.safety.WatchChange;
import dev.jahaziel.workspace.safety.WorkspaceCloneResult;
import dev.jahaziel.workspace.safety.WorkspaceCloneService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Workspace history, trash, diff, clone, watch, bulk, and upload endpoints. */
@RestController
@RequestMapping("/api/v1/workspaces/{id}")
public final class WorkspaceSafetyController extends ApiControllerBase {
    private static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    private final OperationHistory history;
    private final TrashService trash;
    private final WorkspaceDiffService diff;
    private final WorkspaceCloneService clone;
    private final FileWatchService watch;
    private final BulkFileService bulk;
    private final UploadService upload;

    public WorkspaceSafetyController(
            OperationHistory history,
            TrashService trash,
            WorkspaceDiffService diff,
            WorkspaceCloneService clone,
            FileWatchService watch,
            BulkFileService bulk,
            UploadService upload,
            AuthorizationService auth) {
        super(auth);
        this.history = history;
        this.trash = trash;
        this.diff = diff;
        this.clone = clone;
        this.watch = watch;
        this.bulk = bulk;
        this.upload = upload;
    }

    @GetMapping("/history")
    public List<OperationRecord> history(@PathVariable UUID id, Principal principal) {
        return history.list(requireWorkspace(principal, id, READ));
    }

    @PostMapping("/history/undo")
    public List<OperationRecord> undo(@PathVariable UUID id, Principal principal) {
        return history.undo(requireWorkspace(principal, id, WRITE));
    }

    @PostMapping("/history/redo")
    public List<OperationRecord> redo(@PathVariable UUID id, Principal principal) {
        return history.redo(requireWorkspace(principal, id, WRITE));
    }

    @GetMapping("/trash")
    public List<TrashEntry> trashList(@PathVariable UUID id, Principal principal) {
        return trash.list(requireWorkspace(principal, id, READ));
    }

    @PostMapping("/trash")
    public TrashEntry trashFile(
            @PathVariable UUID id, Principal principal, @RequestBody Map<String, Object> body) {
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        String action = text(body, "action");
        if ("restore".equals(action)) {
            return trash.restore(workspace, text(body, "itemId"));
        }
        return trash.trash(workspace, text(body, "path"), actor(principal));
    }

    @DeleteMapping("/trash")
    public void trashDelete(
            @PathVariable UUID id,
            Principal principal,
            @RequestParam(required = false) String itemId,
            @RequestParam(required = false) String path,
            @RequestBody(required = false) Map<String, Object> body) {
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        String item = itemId == null ? text(body, "itemId") : itemId;
        String original = path == null ? text(body, "path") : path;
        if (item != null && !item.isBlank()) {
            trash.purge(workspace, item);
            return;
        }
        if (original != null && !original.isBlank()) {
            List<TrashEntry> entries = trash.list(workspace);
            for (int index = 0; index < entries.size(); index++) {
                if (entries.get(index).getPath().equals(original)) {
                    trash.purge(workspace, entries.get(index).getId());
                    return;
                }
            }
            throw ApiFailureException.badRequest("trash item not found");
        }
        trash.empty(workspace);
    }

    @GetMapping("/diff")
    public WorkspaceDiff diff(
            @PathVariable UUID id, @RequestParam String snapshotId, Principal principal) {
        return diff.diff(requireWorkspace(principal, id, READ), snapshotId);
    }

    @PostMapping("/clone")
    public WorkspaceCloneResult clone(
            @PathVariable UUID id, Principal principal, @RequestBody Map<String, Object> body) {
        UserId user = requireUser(principal);
        WorkspaceId source = requireWorkspaceFor(user, new WorkspaceId(id), WRITE);
        String name = text(body, "name");
        List<String> subset = strings(body, "files");
        String template = text(body, "template");
        if (template != null && !template.isBlank()) {
            return clone.cloneFromTemplate(user, template, name, subset);
        }
        return clone.clone(user, source, name, subset);
    }

    @GetMapping("/watch")
    public List<WatchChange> watch(
            @PathVariable UUID id, @RequestParam long since, Principal principal) {
        return watch.changesSince(requireWorkspace(principal, id, READ), since);
    }

    @PostMapping("/bulk")
    public BulkResult bulk(
            @PathVariable UUID id, Principal principal, @RequestBody Map<String, Object> body) {
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        return bulk.bulk(
                workspace,
                text(body, "operation"),
                strings(body, "paths"),
                text(body, "destination"),
                actor(principal));
    }

    @PostMapping("/upload")
    public ResponseEntity<WorkspaceFiles.Document> upload(
            @PathVariable UUID id, Principal principal, @RequestBody Map<String, Object> body) {
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        String content = text(body, "content");
        if (content == null) {
            throw ApiFailureException.badRequest("content is required");
        }
        byte[] bytes;
        if ("base64".equals(text(body, "encoding"))) {
            try {
                bytes = Base64.getDecoder().decode(content);
            } catch (IllegalArgumentException invalid) {
                throw ApiFailureException.badRequest("content is not valid base64");
            }
        } else {
            bytes = content.getBytes(StandardCharsets.UTF_8);
        }
        WorkspaceFiles.Document saved =
                upload.upload(
                        workspace,
                        text(body, "path"),
                        bytes,
                        text(body, "ifMatch"),
                        actor(principal));
        return ResponseEntity.ok().eTag(saved.version()).body(saved);
    }

    @ExceptionHandler(ApiFailureException.class)
    public ResponseEntity<ApiProblem> apiFailure(ApiFailureException failure) {
        return problem(failure.status(), failure.title(), failure.detail());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiProblem> invalid(IllegalArgumentException failure) {
        return problem(400, "Bad Request", "The request contains an invalid value");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiProblem> conflict(IllegalStateException failure) {
        return problem(409, "Conflict", "The current state does not allow this action");
    }

    @ExceptionHandler(java.io.UncheckedIOException.class)
    public ResponseEntity<ApiProblem> ioFailure(java.io.UncheckedIOException failure) {
        return problem(500, "Internal Server Error", "The operation could not be completed");
    }

    @ExceptionHandler(dev.jahaziel.workspace.api.WorkspaceFileConflictException.class)
    public ResponseEntity<ApiProblem> fileConflict(
            dev.jahaziel.workspace.api.WorkspaceFileConflictException failure) {
        return problem(412, "Precondition Failed", "The file changed since it was read");
    }

    @ExceptionHandler(dev.jahaziel.workspace.api.WorkspaceNotFoundException.class)
    public ResponseEntity<ApiProblem> workspaceMissing(
            dev.jahaziel.workspace.api.WorkspaceNotFoundException failure) {
        return problem(404, "Not Found", "The workspace does not exist");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiProblem> unexpected(Exception failure) {
        return problem(500, "Internal Server Error", "The operation could not be completed");
    }

    private static ResponseEntity<ApiProblem> problem(int status, String title, String detail) {
        ApiProblem body = new ApiProblem(title, status, detail, "");
        return ResponseEntity.status(status).contentType(PROBLEM_JSON).body(body);
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }

    private static String text(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return null;
        }
        return String.valueOf(body.get(key));
    }

    private static List<String> strings(Map<String, Object> body, String key) {
        List<String> result = new ArrayList<String>();
        if (body == null || !(body.get(key) instanceof List<?>)) {
            return result;
        }
        List<?> values = (List<?>) body.get(key);
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index) != null) {
                result.add(String.valueOf(values.get(index)));
            }
        }
        return result;
    }
}
