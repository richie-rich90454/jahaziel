/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.debug.api;

import dev.jahaziel.workspace.api.WorkspaceProcessLifecycle;
import dev.jahaziel.workspace.domain.WorkspaceId;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Workspace-scoped Debug Adapter Protocol sessions. */
public abstract class DebugService extends WorkspaceProcessLifecycle {
    public static final Map<String, String> DAP_ADAPTER_IDS =
            Map.of(
                    "node", "pwa-node",
                    "python", "debugpy",
                    "java", "java",
                    "cpp", "lldb",
                    "go", "go",
                    "rust", "lldb");
    public static final Set<String> LANGUAGES = DAP_ADAPTER_IDS.keySet();

    /** Starts a server-managed adapter for one launch inside the workspace. */
    public abstract Session start(WorkspaceId workspaceId, StartRequest request);

    /** Attaches a server-managed adapter to a live target issued for this workspace. */
    public abstract Session attachToTarget(WorkspaceId workspaceId, AttachRequest request);

    /** Returns the session only when it belongs to the specified workspace. */
    public abstract Session get(WorkspaceId workspaceId, UUID sessionId);

    /** Returns currently active sessions belonging to the specified workspace. */
    public abstract List<Session> list(WorkspaceId workspaceId);

    /** Stops a session only when it belongs to the specified workspace. */
    public abstract Session stop(WorkspaceId workspaceId, UUID sessionId);

    /** Attaches a reconnecting WebSocket to the DAP message stream. */
    public abstract AutoCloseable attach(
            WorkspaceId workspaceId, UUID sessionId, Consumer<String> output);

    /** Sends one JSON DAP message after validating its workspace-bound launch fields. */
    public abstract void send(WorkspaceId workspaceId, UUID sessionId, String message);

    /**
     * Launch-only configuration accepted from the browser. It has no command or executable field.
     */
    public static final class StartRequest {
        private final String languageId;
        private final String program;
        private final String mainClass;
        private final List<String> arguments;
        private final String workingDirectory;
        private final boolean stopOnEntry;

        /**
         * Creates a launch request.
         *
         * @param languageId the debug language
         * @param program the relative program path
         * @param mainClass the Java main class
         * @param arguments the launch arguments
         * @param workingDirectory the relative working directory
         * @param stopOnEntry whether to stop on entry
         */
        public StartRequest(
                String languageId,
                String program,
                String mainClass,
                List<String> arguments,
                String workingDirectory,
                boolean stopOnEntry) {
            if (languageId == null || !LANGUAGES.contains(languageId)) {
                throw new IllegalArgumentException("unsupported debug language");
            }
            arguments = arguments == null ? List.of() : List.copyOf(arguments);
            if (arguments.size() > 128) {
                throw new IllegalArgumentException("debug arguments exceed the supported limit");
            }
            for (String value : arguments) {
                if (value == null || value.length() > 4096) {
                    throw new IllegalArgumentException(
                            "debug arguments exceed the supported limit");
                }
            }
            if ("java".equals(languageId)) {
                if (!validJavaMainClass(mainClass) || program != null && !program.isBlank()) {
                    throw new IllegalArgumentException(
                            "Java debug requires a main class, not an executable");
                }
            } else {
                program = relativePath(program, "program");
                if (mainClass != null && !mainClass.isBlank()) {
                    throw new IllegalArgumentException(
                            "mainClass is supported only by Java debugging");
                }
            }
            workingDirectory =
                    workingDirectory == null || workingDirectory.isBlank()
                            ? "."
                            : relativePath(workingDirectory, "workingDirectory");
            this.languageId = languageId;
            this.program = program;
            this.mainClass = mainClass;
            this.arguments = arguments;
            this.workingDirectory = workingDirectory;
            this.stopOnEntry = stopOnEntry;
        }

        /** Returns the debug language. */
        public String languageId() {
            return languageId;
        }

        public String getLanguageId() {
            return languageId;
        }

        /** Returns the relative program path. */
        public String program() {
            return program;
        }

        public String getProgram() {
            return program;
        }

        /** Returns the Java main class. */
        public String mainClass() {
            return mainClass;
        }

        public String getMainClass() {
            return mainClass;
        }

        /** Returns the launch arguments. */
        public List<String> arguments() {
            return arguments;
        }

        public List<String> getArguments() {
            return arguments;
        }

        /** Returns the relative working directory. */
        public String workingDirectory() {
            return workingDirectory;
        }

        public String getWorkingDirectory() {
            return workingDirectory;
        }

        /** Returns whether to stop on entry. */
        public boolean stopOnEntry() {
            return stopOnEntry;
        }

        public boolean getStopOnEntry() {
            return stopOnEntry;
        }

        public boolean isStopOnEntry() {
            return stopOnEntry;
        }

        private static boolean validJavaMainClass(String value) {
            return value != null
                    && value.length() <= 512
                    && value.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
        }

        private static String relativePath(String value, String name) {
            if (value == null
                    || value.isBlank()
                    || value.length() > 4096
                    || value.indexOf('\0') >= 0) {
                throw new IllegalArgumentException(name + " must be a relative workspace path");
            }
            String portable = value.replace('\\', '/');
            Path path = Path.of(portable);
            if (path.isAbsolute()
                    || portable.matches("^[A-Za-z]:.*")
                    || portable.startsWith("//")
                    || path.normalize().startsWith("..")) {
                throw new IllegalArgumentException(name + " must stay inside the workspace");
            }
            return path.normalize().toString().replace('\\', '/');
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StartRequest)) {
                return false;
            }
            StartRequest that = (StartRequest) other;
            return stopOnEntry == that.stopOnEntry
                    && languageId.equals(that.languageId)
                    && Objects.equals(program, that.program)
                    && Objects.equals(mainClass, that.mainClass)
                    && arguments.equals(that.arguments)
                    && workingDirectory.equals(that.workingDirectory);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    languageId, program, mainClass, arguments, workingDirectory, stopOnEntry);
        }

        @Override
        public String toString() {
            return "StartRequest[languageId="
                    + languageId
                    + ", program="
                    + program
                    + ", mainClass="
                    + mainClass
                    + ", arguments="
                    + arguments
                    + ", workingDirectory="
                    + workingDirectory
                    + ", stopOnEntry="
                    + stopOnEntry
                    + "]";
        }
    }

    /** The browser supplies only an opaque target id, never a process id or endpoint. */
    public static final class AttachRequest {
        private final UUID targetId;

        /**
         * Creates an attach request.
         *
         * @param targetId the opaque target id
         */
        public AttachRequest(UUID targetId) {
            if (targetId == null) {
                throw new IllegalArgumentException("debug target is required");
            }
            this.targetId = targetId;
        }

        /** Returns the opaque target id. */
        public UUID targetId() {
            return targetId;
        }

        public UUID getTargetId() {
            return targetId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof AttachRequest
                    && targetId.equals(((AttachRequest) other).targetId);
        }

        @Override
        public int hashCode() {
            return targetId.hashCode();
        }

        @Override
        public String toString() {
            return "AttachRequest[targetId=" + targetId + "]";
        }
    }

    /** Public status that does not expose adapter command paths or process identifiers. */
    public static final class Session {
        private final UUID id;
        private final UUID workspaceId;
        private final String languageId;
        private final String adapterId;
        private final String state;
        private final String adapterVersion;
        private final Instant startedAt;
        private final String error;

        /**
         * Creates a session view.
         *
         * @param id the session id
         * @param workspaceId the workspace id
         * @param languageId the debug language
         * @param adapterId the adapter id
         * @param state the session state
         * @param adapterVersion the adapter version
         * @param startedAt when the session started
         * @param error the failure message, or null
         */
        public Session(
                UUID id,
                UUID workspaceId,
                String languageId,
                String adapterId,
                String state,
                String adapterVersion,
                Instant startedAt,
                String error) {
            this.id = id;
            this.workspaceId = workspaceId;
            this.languageId = languageId;
            this.adapterId = adapterId;
            this.state = state;
            this.adapterVersion = adapterVersion;
            this.startedAt = startedAt;
            this.error = error;
        }

        /** Returns the session id. */
        public UUID id() {
            return id;
        }

        public UUID getId() {
            return id;
        }

        /** Returns the workspace id. */
        public UUID workspaceId() {
            return workspaceId;
        }

        public UUID getWorkspaceId() {
            return workspaceId;
        }

        /** Returns the debug language. */
        public String languageId() {
            return languageId;
        }

        public String getLanguageId() {
            return languageId;
        }

        /** Returns the adapter id. */
        public String adapterId() {
            return adapterId;
        }

        public String getAdapterId() {
            return adapterId;
        }

        /** Returns the session state. */
        public String state() {
            return state;
        }

        public String getState() {
            return state;
        }

        /** Returns the adapter version. */
        public String adapterVersion() {
            return adapterVersion;
        }

        public String getAdapterVersion() {
            return adapterVersion;
        }

        /** Returns when the session started. */
        public Instant startedAt() {
            return startedAt;
        }

        public Instant getStartedAt() {
            return startedAt;
        }

        /** Returns the failure message, or null. */
        public String error() {
            return error;
        }

        public String getError() {
            return error;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Session)) {
                return false;
            }
            Session that = (Session) other;
            return id.equals(that.id)
                    && workspaceId.equals(that.workspaceId)
                    && languageId.equals(that.languageId)
                    && adapterId.equals(that.adapterId)
                    && state.equals(that.state)
                    && adapterVersion.equals(that.adapterVersion)
                    && startedAt.equals(that.startedAt)
                    && Objects.equals(error, that.error);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    id,
                    workspaceId,
                    languageId,
                    adapterId,
                    state,
                    adapterVersion,
                    startedAt,
                    error);
        }

        @Override
        public String toString() {
            return "Session[id="
                    + id
                    + ", workspaceId="
                    + workspaceId
                    + ", languageId="
                    + languageId
                    + ", adapterId="
                    + adapterId
                    + ", state="
                    + state
                    + ", adapterVersion="
                    + adapterVersion
                    + ", startedAt="
                    + startedAt
                    + ", error="
                    + error
                    + "]";
        }
    }
}
