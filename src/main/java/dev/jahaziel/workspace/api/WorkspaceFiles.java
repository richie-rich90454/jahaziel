/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.workspace.domain.WorkspaceId;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Workspace-rooted file operations used by the browser workbench. */
public abstract class WorkspaceFiles {
    public abstract Operations forWorkspace(WorkspaceId workspaceId);

    /** Operations scoped to a single workspace. */
    public abstract static class Operations {
        public abstract List<Entry> list(String relativeDirectory);

        public abstract Document read(String relativePath);

        public abstract Download download(String relativePath);

        public abstract List<SearchResult> search(String query, int limit);

        public abstract Document save(
                String relativePath, String content, String expectedVersion, boolean createOnly);

        public abstract void createDirectories(String relativePath);

        public abstract void rename(String relativePath, String newRelativePath);

        public abstract void delete(String relativePath);
    }

    /** One visible file or directory. */
    public static final class Entry {
        private final String name;
        private final String path;
        private final boolean directory;
        private final long size;
        private final long modifiedAt;

        public Entry(String name, String path, boolean directory, long size, long modifiedAt) {
            this.name = name;
            this.path = path;
            this.directory = directory;
            this.size = size;
            this.modifiedAt = modifiedAt;
        }

        public String name() {
            return name;
        }

        public String getName() {
            return name;
        }

        public String path() {
            return path;
        }

        public String getPath() {
            return path;
        }

        public boolean directory() {
            return directory;
        }

        public boolean getDirectory() {
            return directory;
        }

        public boolean isDirectory() {
            return directory;
        }

        public long size() {
            return size;
        }

        public long getSize() {
            return size;
        }

        public long modifiedAt() {
            return modifiedAt;
        }

        public long getModifiedAt() {
            return modifiedAt;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry other = (Entry) o;
            return Objects.equals(name, other.name)
                    && Objects.equals(path, other.path)
                    && directory == other.directory
                    && size == other.size
                    && modifiedAt == other.modifiedAt;
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(path);
            result = 31 * result + (directory ? 1231 : 1237);
            result = 31 * result + (int) (size ^ (size >>> 32));
            result = 31 * result + (int) (modifiedAt ^ (modifiedAt >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "Entry{"
                    + "name="
                    + name
                    + ", path="
                    + path
                    + ", directory="
                    + directory
                    + ", size="
                    + size
                    + ", modifiedAt="
                    + modifiedAt
                    + '}';
        }
    }

    /** UTF-8 document and its content version. */
    public static final class Document {
        private final String path;
        private final String content;
        private final String version;

        public Document(String path, String content, String version) {
            this.path = path;
            this.content = content;
            this.version = version;
        }

        public String path() {
            return path;
        }

        public String getPath() {
            return path;
        }

        public String content() {
            return content;
        }

        public String getContent() {
            return content;
        }

        public String version() {
            return version;
        }

        public String getVersion() {
            return version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Document other = (Document) o;
            return Objects.equals(path, other.path)
                    && Objects.equals(content, other.content)
                    && Objects.equals(version, other.version);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(path);
            result = 31 * result + Objects.hashCode(content);
            result = 31 * result + Objects.hashCode(version);
            return result;
        }

        @Override
        public String toString() {
            return "Document{"
                    + "path="
                    + path
                    + ", content="
                    + content
                    + ", version="
                    + version
                    + '}';
        }
    }

    /** A confined file path for a streamed attachment response. */
    public static final class Download {
        private final Path file;
        private final String fileName;
        private final long size;

        public Download(Path file, String fileName, long size) {
            this.file = file;
            this.fileName = fileName;
            this.size = size;
        }

        public Path file() {
            return file;
        }

        public Path getFile() {
            return file;
        }

        public String fileName() {
            return fileName;
        }

        public String getFileName() {
            return fileName;
        }

        public long size() {
            return size;
        }

        public long getSize() {
            return size;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Download other = (Download) o;
            return Objects.equals(file, other.file)
                    && Objects.equals(fileName, other.fileName)
                    && size == other.size;
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(file);
            result = 31 * result + Objects.hashCode(fileName);
            result = 31 * result + (int) (size ^ (size >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "Download{" + "file=" + file + ", fileName=" + fileName + ", size=" + size + '}';
        }
    }

    /** One bounded workspace search result. Line zero means the file name matched. */
    public static final class SearchResult {
        private final String path;
        private final int line;
        private final String preview;

        public SearchResult(String path, int line, String preview) {
            this.path = path;
            this.line = line;
            this.preview = preview;
        }

        public String path() {
            return path;
        }

        public String getPath() {
            return path;
        }

        public int line() {
            return line;
        }

        public int getLine() {
            return line;
        }

        public String preview() {
            return preview;
        }

        public String getPreview() {
            return preview;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SearchResult other = (SearchResult) o;
            return Objects.equals(path, other.path)
                    && line == other.line
                    && Objects.equals(preview, other.preview);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(path);
            result = 31 * result + (int) line;
            result = 31 * result + Objects.hashCode(preview);
            return result;
        }

        @Override
        public String toString() {
            return "SearchResult{"
                    + "path="
                    + path
                    + ", line="
                    + line
                    + ", preview="
                    + preview
                    + '}';
        }
    }
}
