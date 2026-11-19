/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import java.util.List;

/** Plain request and response bodies for the workspace data-tool endpoints. */
public final class DataDtos {
    private DataDtos() {}

    /** A single workspace-relative path. */
    public static final class PathRequest {
        private String path;

        public String getPath() {
            return path;
        }

        public void setPath(String value) {
            this.path = value;
        }
    }

    /** A read-only SQL query against a workspace database. */
    public static final class QueryRequest {
        private String path;
        private String sql;

        public String getPath() {
            return path;
        }

        public void setPath(String value) {
            this.path = value;
        }

        public String getSql() {
            return sql;
        }

        public void setSql(String value) {
            this.sql = value;
        }
    }

    /** Bounded table paging. */
    public static final class RowsRequest {
        private String path;
        private String table;
        private int offset;
        private int limit;

        public String getPath() {
            return path;
        }

        public void setPath(String value) {
            this.path = value;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String value) {
            this.table = value;
        }

        public int getOffset() {
            return offset;
        }

        public void setOffset(int value) {
            this.offset = value;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int value) {
            this.limit = value;
        }
    }

    /** CSV text to parse or save. */
    public static final class CsvRequest {
        private String content;
        private int maxRows;
        private String path;
        private String expectedVersion;

        public String getContent() {
            return content;
        }

        public void setContent(String value) {
            this.content = value;
        }

        public int getMaxRows() {
            return maxRows;
        }

        public void setMaxRows(int value) {
            this.maxRows = value;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String value) {
            this.path = value;
        }

        public String getExpectedVersion() {
            return expectedVersion;
        }

        public void setExpectedVersion(String value) {
            this.expectedVersion = value;
        }
    }

    /** A bounded tabular result. */
    public static final class TableDto {
        private List<String> columns;
        private List<List<String>> rows;

        public List<String> getColumns() {
            return columns;
        }

        public void setColumns(List<String> value) {
            this.columns = value;
        }

        public List<List<String>> getRows() {
            return rows;
        }

        public void setRows(List<List<String>> value) {
            this.rows = value;
        }
    }

    /** One notebook cell. */
    public static final class CellDto {
        private String id;
        private String source;

        public String getId() {
            return id;
        }

        public void setId(String value) {
            this.id = value;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String value) {
            this.source = value;
        }
    }

    /** Ordered notebook cells. */
    public static final class CellsRequest {
        private List<CellDto> cells;

        public List<CellDto> getCells() {
            return cells;
        }

        public void setCells(List<CellDto> value) {
            this.cells = value;
        }
    }

    /** A declared workspace task id. */
    public static final class NotebookRunRequest {
        private String taskId;

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String value) {
            this.taskId = value;
        }
    }

    /** Notebook cells read back from storage. */
    public static final class NotebookDto {
        private String notebookId;
        private List<CellDto> cells;

        public String getNotebookId() {
            return notebookId;
        }

        public void setNotebookId(String value) {
            this.notebookId = value;
        }

        public List<CellDto> getCells() {
            return cells;
        }

        public void setCells(List<CellDto> value) {
            this.cells = value;
        }
    }

    /** Rendered CSV text. */
    public static final class ContentDto {
        private String content;

        public ContentDto() {}

        public ContentDto(String value) {
            this.content = value;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String value) {
            this.content = value;
        }
    }

    /** A saved document version. */
    public static final class DocumentDto {
        private String version;

        public String getVersion() {
            return version;
        }

        public void setVersion(String value) {
            this.version = value;
        }
    }

    /** One bounded cell output. */
    public static final class OutputDto {
        private String type;
        private String data;

        public String getType() {
            return type;
        }

        public void setType(String value) {
            this.type = value;
        }

        public String getData() {
            return data;
        }

        public void setData(String value) {
            this.data = value;
        }
    }

    /** Result of one notebook run. */
    public static final class ExecutionDto {
        private String notebookId;
        private String runId;
        private List<OutputDto> outputs;

        public String getNotebookId() {
            return notebookId;
        }

        public void setNotebookId(String value) {
            this.notebookId = value;
        }

        public String getRunId() {
            return runId;
        }

        public void setRunId(String value) {
            this.runId = value;
        }

        public List<OutputDto> getOutputs() {
            return outputs;
        }

        public void setOutputs(List<OutputDto> value) {
            this.outputs = value;
        }
    }

    /** Search query. */
    public static final class SearchRequest {
        private String query;
        private int limit;

        public String getQuery() {
            return query;
        }

        public void setQuery(String value) {
            this.query = value;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int value) {
            this.limit = value;
        }
    }

    /** One indexed hit. */
    public static final class HitDto {
        private String path;
        private String excerpt;

        public String getPath() {
            return path;
        }

        public void setPath(String value) {
            this.path = value;
        }

        public String getExcerpt() {
            return excerpt;
        }

        public void setExcerpt(String value) {
            this.excerpt = value;
        }
    }

    /** Generic count body. */
    public static final class CountDto {
        private int count;

        public CountDto() {}

        public CountDto(int value) {
            this.count = value;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int value) {
            this.count = value;
        }
    }

    /** Snapshot pair for a file-history diff. */
    public static final class DiffRequest {
        private String fromId;
        private String toId;

        public String getFromId() {
            return fromId;
        }

        public void setFromId(String value) {
            this.fromId = value;
        }

        public String getToId() {
            return toId;
        }

        public void setToId(String value) {
            this.toId = value;
        }
    }

    /** One file restore request. */
    public static final class RestoreRequest {
        private String snapshotId;
        private String path;
        private String expectedVersion;

        public String getSnapshotId() {
            return snapshotId;
        }

        public void setSnapshotId(String value) {
            this.snapshotId = value;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String value) {
            this.path = value;
        }

        public String getExpectedVersion() {
            return expectedVersion;
        }

        public void setExpectedVersion(String value) {
            this.expectedVersion = value;
        }
    }

    /** One file version view. */
    public static final class VersionDto {
        private String id;
        private String tag;
        private String createdAt;

        public String getId() {
            return id;
        }

        public void setId(String value) {
            this.id = value;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String value) {
            this.tag = value;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String value) {
            this.createdAt = value;
        }
    }

    /** One file change between two versions. */
    public static final class FileDiffDto {
        private String path;
        private String change;

        public String getPath() {
            return path;
        }

        public void setPath(String value) {
            this.path = value;
        }

        public String getChange() {
            return change;
        }

        public void setChange(String value) {
            this.change = value;
        }
    }
}
