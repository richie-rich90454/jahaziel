/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.datatools.DataTable;
import dev.jahaziel.datatools.SqliteBrowser;
import java.security.Principal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only SQLite browsing. Every caller path is confined before it reaches the browser service. */
@RestController
@RequestMapping("/api/v1/workspaces/{id}/data/sqlite")
public final class SqliteController extends ApiControllerBase {
    private final SqliteBrowser browser;
    private final String storageRoot;

    public SqliteController(
            SqliteBrowser browser,
            AuthorizationService auth,
            @Value("${jahaziel.storage.root:data}") String storageRoot) {
        super(auth);
        this.browser = browser;
        this.storageRoot = storageRoot;
    }

    @GetMapping("/tables")
    public java.util.List<String> tables(
            @PathVariable UUID id, @RequestParam String path, Principal principal) {
        requireWorkspace(principal, id, READ);
        requireContained(workspaceHome(storageRoot, id), path);
        return browser.tables(new dev.jahaziel.workspace.domain.WorkspaceId(id), path);
    }

    @GetMapping("/schema")
    public DataDtos.TableDto schema(
            @PathVariable UUID id, @RequestParam String path, Principal principal) {
        requireWorkspace(principal, id, READ);
        requireContained(workspaceHome(storageRoot, id), path);
        return view(browser.schema(new dev.jahaziel.workspace.domain.WorkspaceId(id), path));
    }

    @GetMapping("/rows")
    public DataDtos.TableDto rows(
            @PathVariable UUID id,
            @RequestParam String path,
            @RequestParam String table,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit,
            Principal principal) {
        requireWorkspace(principal, id, READ);
        requireContained(workspaceHome(storageRoot, id), path);
        return view(browser.rows(new dev.jahaziel.workspace.domain.WorkspaceId(id), path, table, offset, limit));
    }

    @PostMapping("/query")
    public DataDtos.TableDto query(
            @PathVariable UUID id,
            @RequestBody(required = false) DataDtos.QueryRequest body,
            Principal principal) {
        requireWorkspace(principal, id, READ);
        DataDtos.QueryRequest request = body == null ? new DataDtos.QueryRequest() : body;
        requireContained(workspaceHome(storageRoot, id), request.getPath());
        return view(browser.query(
            new dev.jahaziel.workspace.domain.WorkspaceId(id), request.getPath(), request.getSql()));
    }

    private static DataDtos.TableDto view(DataTable table) {
        DataDtos.TableDto result = new DataDtos.TableDto();
        result.setColumns(table.columns());
        result.setRows(table.rows());
        return result;
    }
}
