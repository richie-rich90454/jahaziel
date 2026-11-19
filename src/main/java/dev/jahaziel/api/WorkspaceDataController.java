/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.datatools.CellOutput;
import dev.jahaziel.datatools.CsvReader;
import dev.jahaziel.datatools.CsvTable;
import dev.jahaziel.datatools.CsvWriter;
import dev.jahaziel.datatools.FileDiffEntry;
import dev.jahaziel.datatools.FileHistoryService;
import dev.jahaziel.datatools.FileVersion;
import dev.jahaziel.datatools.IndexHit;
import dev.jahaziel.datatools.NotebookCell;
import dev.jahaziel.datatools.NotebookExecution;
import dev.jahaziel.datatools.NotebookRunner;
import dev.jahaziel.datatools.SearchIndex;
import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** CSV, notebook, search-index and file-history endpoints for one authorized workspace. */
@RestController
@RequestMapping("/api/v1/workspaces/{id}/data")
public final class WorkspaceDataController extends ApiControllerBase {
    private final CsvReader csvReader;
    private final CsvWriter csvWriter;
    private final NotebookRunner notebooks;
    private final SearchIndex search;
    private final FileHistoryService history;
    private final String storageRoot;

    public WorkspaceDataController(
            CsvReader csvReader,
            CsvWriter csvWriter,
            NotebookRunner notebooks,
            SearchIndex search,
            FileHistoryService history,
            AuthorizationService auth,
            @Value("${jahaziel.storage.root:data}") String storageRoot) {
        super(auth);
        this.csvReader = csvReader;
        this.csvWriter = csvWriter;
        this.notebooks = notebooks;
        this.search = search;
        this.history = history;
        this.storageRoot = storageRoot;
    }

    @PostMapping("/csv/parse")
    public DataDtos.TableDto parseCsv(
            @PathVariable UUID id,
            @RequestBody(required = false) DataDtos.CsvRequest body,
            Principal principal) {
        requireWorkspace(principal, id, READ);
        DataDtos.CsvRequest request = body == null ? new DataDtos.CsvRequest() : body;
        int max = request.getMaxRows() > 0 ? request.getMaxRows() : CsvReader.MAX_ROWS;
        return view(csvReader.read(request.getContent(), max));
    }

    @PostMapping("/csv/write")
    public DataDtos.ContentDto writeCsv(
            @PathVariable UUID id,
            @RequestBody(required = false) DataDtos.CsvRequest body,
            Principal principal) {
        requireWorkspace(principal, id, READ);
        DataDtos.CsvRequest request = body == null ? new DataDtos.CsvRequest() : body;
        int max = request.getMaxRows() > 0 ? request.getMaxRows() : CsvReader.MAX_ROWS;
        return new DataDtos.ContentDto(csvWriter.write(csvReader.read(request.getContent(), max)));
    }

    @PostMapping("/csv/save")
    public DataDtos.DocumentDto saveCsv(
            @PathVariable UUID id,
            @RequestBody(required = false) DataDtos.CsvRequest body,
            Principal principal) {
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        DataDtos.CsvRequest request = body == null ? new DataDtos.CsvRequest() : body;
        requireContained(workspaceHome(storageRoot, id), request.getPath());
        return document(
                csvWriter.save(
                        workspace,
                        request.getPath(),
                        csvReader.read(request.getContent()),
                        request.getExpectedVersion()));
    }

    @PostMapping("/notebooks/{notebookId}/cells")
    public AgentDtos.StatusDto saveCells(
            @PathVariable UUID id,
            @PathVariable String notebookId,
            @RequestBody(required = false) DataDtos.CellsRequest body,
            Principal principal) {
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        requireContained(workspaceHome(storageRoot, id), notebookId);
        DataDtos.CellsRequest request = body == null ? new DataDtos.CellsRequest() : body;
        List<NotebookCell> cells = new ArrayList<NotebookCell>();
        List<DataDtos.CellDto> values =
                request.getCells() == null ? new ArrayList<DataDtos.CellDto>() : request.getCells();
        for (int index = 0; index < values.size(); index++) {
            cells.add(new NotebookCell(values.get(index).getId(), values.get(index).getSource()));
        }
        notebooks.save(workspace, notebookId, cells);
        return new AgentDtos.StatusDto("saved");
    }

    @GetMapping("/notebooks/{notebookId}/cells")
    public DataDtos.NotebookDto loadCells(
            @PathVariable UUID id, @PathVariable String notebookId, Principal principal) {
        WorkspaceId workspace = requireWorkspace(principal, id, READ);
        requireContained(workspaceHome(storageRoot, id), notebookId);
        List<NotebookCell> found = notebooks.load(workspace, notebookId);
        List<DataDtos.CellDto> cells = new ArrayList<DataDtos.CellDto>();
        for (int index = 0; index < found.size(); index++) {
            DataDtos.CellDto dto = new DataDtos.CellDto();
            dto.setId(found.get(index).id());
            dto.setSource(found.get(index).source());
            cells.add(dto);
        }
        DataDtos.NotebookDto result = new DataDtos.NotebookDto();
        result.setNotebookId(notebookId);
        result.setCells(cells);
        return result;
    }

    @PostMapping("/notebooks/{notebookId}/run")
    public DataDtos.ExecutionDto runNotebook(
            @PathVariable UUID id,
            @PathVariable String notebookId,
            @RequestBody(required = false) DataDtos.NotebookRunRequest body,
            Principal principal) {
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        requireContained(workspaceHome(storageRoot, id), notebookId);
        String taskId = body == null ? null : body.getTaskId();
        return view(notebooks.run(workspace, notebookId, taskId));
    }

    @PostMapping("/search/index")
    public DataDtos.CountDto index(@PathVariable UUID id, Principal principal) {
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        return new DataDtos.CountDto(search.index(workspace));
    }

    @PostMapping("/search/rebuild")
    public DataDtos.CountDto rebuild(@PathVariable UUID id, Principal principal) {
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        return new DataDtos.CountDto(search.rebuild(workspace));
    }

    @PostMapping("/search/query")
    public List<DataDtos.HitDto> query(
            @PathVariable UUID id,
            @RequestBody(required = false) DataDtos.SearchRequest body,
            Principal principal) {
        WorkspaceId workspace = requireWorkspace(principal, id, READ);
        DataDtos.SearchRequest request = body == null ? new DataDtos.SearchRequest() : body;
        int limit = request.getLimit() > 0 ? request.getLimit() : SearchIndex.MAX_RESULTS;
        List<IndexHit> found = search.search(workspace, request.getQuery(), limit);
        List<DataDtos.HitDto> result = new ArrayList<DataDtos.HitDto>();
        for (int index = 0; index < found.size(); index++) {
            DataDtos.HitDto dto = new DataDtos.HitDto();
            dto.setPath(found.get(index).path());
            dto.setExcerpt(found.get(index).snippet());
            result.add(dto);
        }
        return result;
    }

    @GetMapping("/history/versions")
    public List<DataDtos.VersionDto> versions(@PathVariable UUID id, Principal principal) {
        WorkspaceId workspace = requireWorkspace(principal, id, READ);
        List<FileVersion> found = history.versions(workspace);
        List<DataDtos.VersionDto> result = new ArrayList<DataDtos.VersionDto>();
        for (int index = 0; index < found.size(); index++) {
            DataDtos.VersionDto dto = new DataDtos.VersionDto();
            dto.setId(found.get(index).id());
            dto.setTag(found.get(index).tag());
            dto.setCreatedAt(found.get(index).createdAt());
            result.add(dto);
        }
        return result;
    }

    @PostMapping("/history/diff")
    public List<DataDtos.FileDiffDto> diff(
            @PathVariable UUID id,
            @RequestBody(required = false) DataDtos.DiffRequest body,
            Principal principal) {
        WorkspaceId workspace = requireWorkspace(principal, id, READ);
        DataDtos.DiffRequest request = body == null ? new DataDtos.DiffRequest() : body;
        List<FileDiffEntry> found = history.diff(workspace, request.getFromId(), request.getToId());
        List<DataDtos.FileDiffDto> result = new ArrayList<DataDtos.FileDiffDto>();
        for (int index = 0; index < found.size(); index++) {
            DataDtos.FileDiffDto dto = new DataDtos.FileDiffDto();
            dto.setPath(found.get(index).path());
            dto.setChange(found.get(index).change());
            result.add(dto);
        }
        return result;
    }

    @PostMapping("/history/restore")
    public DataDtos.DocumentDto restore(
            @PathVariable UUID id,
            @RequestBody(required = false) DataDtos.RestoreRequest body,
            Principal principal) {
        WorkspaceId workspace = requireWorkspace(principal, id, WRITE);
        DataDtos.RestoreRequest request = body == null ? new DataDtos.RestoreRequest() : body;
        requireContained(workspaceHome(storageRoot, id), request.getPath());
        return document(
                history.restoreFile(
                        workspace,
                        request.getSnapshotId(),
                        request.getPath(),
                        request.getExpectedVersion()));
    }

    private static DataDtos.TableDto view(CsvTable table) {
        DataDtos.TableDto result = new DataDtos.TableDto();
        result.setColumns(table.headers());
        result.setRows(table.rows());
        return result;
    }

    private static DataDtos.DocumentDto document(WorkspaceFiles.Document saved) {
        DataDtos.DocumentDto result = new DataDtos.DocumentDto();
        result.setVersion(saved.version());
        return result;
    }

    private static DataDtos.ExecutionDto view(NotebookExecution execution) {
        DataDtos.ExecutionDto result = new DataDtos.ExecutionDto();
        result.setNotebookId(execution.notebookId());
        result.setRunId(execution.runId());
        List<DataDtos.OutputDto> outputs = new ArrayList<DataDtos.OutputDto>();
        List<CellOutput> found = execution.outputs();
        for (int index = 0; index < found.size(); index++) {
            DataDtos.OutputDto dto = new DataDtos.OutputDto();
            dto.setType(found.get(index).type());
            dto.setData(found.get(index).data());
            outputs.add(dto);
        }
        result.setOutputs(outputs);
        return result;
    }
}
