/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import dev.jahaziel.language.api.LanguagePackService;
import dev.jahaziel.platform.Platform;
import dev.jahaziel.platform.Platforms;
import dev.jahaziel.workspace.api.WorkspaceDirectoryService;
import dev.jahaziel.workspace.api.WorkspaceProcessLifecycle;
import dev.jahaziel.workspace.api.WorkspaceProcessRegistry;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Starts only selected, pinned language servers and proxies bounded JSON-RPC over stdio. */
public final class LanguageServerProcessManager extends WorkspaceProcessLifecycle {
    private static final Logger log = LoggerFactory.getLogger(LanguageServerProcessManager.class);
    private static final int MAX_SESSIONS = 8;
    private static final int MAX_MESSAGE_BYTES = 1024 * 1024;
    private static final int MAX_FRAME_BYTES = 2 * 1024 * 1024;

    private final LanguagePackService packs;
    private final WorkspaceDirectoryService workspaces;
    private final ObjectMapper json;
    private final WorkspaceProcessRegistry processRegistry;
    private final Path storageRoot;
    private final ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID, ProcessSession> sessions = new ConcurrentHashMap<>();
    private final java.util.Set<WorkspaceId> stoppingWorkspaces = ConcurrentHashMap.newKeySet();
    private boolean closed;

    public LanguageServerProcessManager(
            LanguagePackService packs,
            WorkspaceDirectoryService workspaces,
            ObjectMapper json,
            String storageRoot,
            WorkspaceProcessRegistry processRegistry) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
        this.json = Objects.requireNonNull(json, "json");
        this.processRegistry = Objects.requireNonNull(processRegistry, "processRegistry");
        if (storageRoot == null || storageRoot.isBlank()) {
            throw new IllegalArgumentException("storageRoot must not be blank");
        }
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public Session open(WorkspaceId workspaceId, String languageId, Consumer<String> output) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(output, "output");
        if (!packs.supportsLanguageService(languageId)) {
            throw new IllegalArgumentException(
                    "language service is not available for this language");
        }
        synchronized (sessions) {
            if (closed) {
                throw new IllegalStateException("language services are shutting down");
            }
            if (stoppingWorkspaces.contains(workspaceId)) {
                throw new IllegalStateException("workspace is stopping");
            }
            if (sessions.size() >= MAX_SESSIONS) {
                throw new LanguageSessionLimitException();
            }
            Path workspaceHome = workspaces.home(workspaceId);
            Path state = stateDirectory(workspaceId, languageId);
            List<String> command = packs.launchCommandFor(workspaceId, languageId, state);
            Map<String, String> languageEnvironment =
                    packs.launchEnvironmentFor(workspaceId, languageId, workspaceHome, state);
            Map<String, Object> initializationOptions =
                    packs.initializationOptionsFor(workspaceId, languageId);
            try {
                ProcessBuilder builder =
                        new ProcessBuilder(processRegistry.dedicatedSessionCommand(command))
                                .directory(workspaceHome.toFile());
                configureEnvironment(
                        builder,
                        workspaceHome,
                        state,
                        languageId.equals("java"),
                        languageEnvironment == null ? Map.of() : languageEnvironment);
                Process process = builder.start();
                WorkspaceProcessRegistry.Registration registration = null;
                try {
                    registration =
                            processRegistry.track(
                                    workspaceId,
                                    WorkspaceProcessRegistry.Kind.LANGUAGE_SERVER,
                                    process);
                    UUID id = UUID.randomUUID();
                    ProcessSession session =
                            new ProcessSession(
                                    id,
                                    workspaceId,
                                    languageId,
                                    workspaceHome,
                                    initializationOptions,
                                    process,
                                    registration,
                                    output);
                    sessions.put(id, session);
                    readers.submit(
                            new Runnable() {
                                @Override
                                public void run() {
                                    readOutput(session);
                                }
                            });
                    readers.submit(
                            new Runnable() {
                                @Override
                                public void run() {
                                    discardStderr(session);
                                }
                            });
                    readers.submit(
                            new Runnable() {
                                @Override
                                public void run() {
                                    watchExit(session);
                                }
                            });
                    return session;
                } catch (RuntimeException failure) {
                    process.destroyForcibly();
                    if (registration != null) registration.close();
                    throw failure;
                }
            } catch (IOException failure) {
                throw new LanguageServerStartException("could not start language server", failure);
            }
        }
    }

    public void closeAll() {
        List<ProcessSession> closing;
        synchronized (sessions) {
            closed = true;
            closing = List.copyOf(sessions.values());
        }
        for (ProcessSession session : closing) {
            session.close();
        }
        readers.shutdownNow();
    }

    @Override
    public void closeByWorkspace(WorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        List<ProcessSession> closing;
        synchronized (sessions) {
            stoppingWorkspaces.add(workspaceId);
            closing = new ArrayList<>();
            for (ProcessSession session : sessions.values()) {
                if (session.workspaceId.equals(workspaceId)) {
                    closing.add(session);
                }
            }
        }
        for (ProcessSession session : closing) {
            session.closeForWorkspaceStop();
        }
    }

    @Override
    public void allowByWorkspace(WorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        synchronized (sessions) {
            if (!closed) stoppingWorkspaces.remove(workspaceId);
        }
    }

    @Override
    public void deleteWorkspace(WorkspaceId workspaceId) {
        closeByWorkspace(workspaceId);
        packs.clearWorkspace(workspaceId);
    }

    private Path stateDirectory(WorkspaceId workspaceId, String languageId) {
        Path root = storageRoot.resolve("language-packs");
        Path state =
                root.resolve("state").resolve(workspaceId.value().toString()).resolve(languageId);
        try {
            ensurePlainDirectory(root);
            ensurePlainDirectory(state);
            Path realRoot = root.toRealPath();
            Path realState = state.toRealPath();
            if (!realState.startsWith(realRoot)) {
                throw new IOException("language server state escapes the cache");
            }
            Path temporary = realState.resolve("tmp");
            ensurePlainDirectory(temporary);
            return realState;
        } catch (IOException failure) {
            throw new LanguageServerStartException("language server state is unavailable", failure);
        }
    }

    private static void ensurePlainDirectory(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        if (current == null) {
            throw new IOException("language server state path is not absolute");
        }
        for (Path component : absolute) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current)
                        || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("language server state path is unsafe");
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    private static String basePath() {
        Platform.Os os = Platforms.detect(null).os();
        if (os == Platform.Os.WINDOWS) return "C:\\Windows\\System32;C:\\Windows";
        if (os == Platform.Os.MACOS) return "/usr/bin:/bin:/usr/sbin:/sbin";
        return "/usr/local/bin:/usr/bin:/bin";
    }

    private static void configureEnvironment(
            ProcessBuilder builder,
            Path workspaceHome,
            Path state,
            boolean javaLanguageServer,
            Map<String, String> languageEnvironment) {
        Map<String, String> environment = builder.environment();
        environment.clear();
        String path = basePath();
        if (javaLanguageServer) {
            Path javaHome = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
            environment.put("JAVA_HOME", javaHome.toString());
            path = javaHome.resolve("bin") + java.io.File.pathSeparator + path;
        }
        environment.put("PATH", path);
        environment.put("HOME", workspaceHome.toString());
        environment.put("TMPDIR", state.resolve("tmp").toString());
        environment.put("LANG", "C.UTF-8");
        environment.put("LC_ALL", "C.UTF-8");
        environment.put("CARGO_HOME", workspaceHome.resolve(".cargo").toString());
        environment.put("RUSTUP_HOME", workspaceHome.resolve(".rustup").toString());
        environment.putAll(languageEnvironment);
    }

    private void readOutput(ProcessSession session) {
        try (BufferedInputStream input =
                new BufferedInputStream(session.process.getInputStream())) {
            while (!session.closed.get()) {
                int length = frameLength(input);
                if (length < 0) {
                    break;
                }
                byte[] bytes = input.readNBytes(length);
                if (bytes.length != length) {
                    break;
                }
                JsonNode parsed = json.readTree(new String(bytes, StandardCharsets.UTF_8));
                if (parsed != null && parsed.isObject()) {
                    session.output.accept(
                            json.writeValueAsString(session.rewriteServerMessage(parsed)));
                }
            }
        } catch (Exception failure) {
            if (!session.closed.get() && session.process.isAlive()) {
                log.debug(
                        "language server output ended for {} ({})", session.languageId, session.id);
            }
        } finally {
            session.close();
        }
    }

    private static int frameLength(BufferedInputStream input) throws IOException {
        int contentLength = -1;
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) >= 0) {
            if (value == '\n') {
                String header = line.toString(StandardCharsets.US_ASCII).stripTrailing();
                line.reset();
                if (header.isEmpty()) {
                    if (contentLength < 0 || contentLength > MAX_FRAME_BYTES) {
                        throw new IOException("language server sent an invalid frame length");
                    }
                    return contentLength;
                }
                if (header.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                    try {
                        contentLength = Integer.parseInt(header.substring(15).trim());
                    } catch (NumberFormatException invalid) {
                        throw new IOException(
                                "language server sent an invalid frame length", invalid);
                    }
                }
            } else if (value != '\r') {
                if (line.size() >= 4096) {
                    throw new IOException("language server frame header is too long");
                }
                line.write(value);
            }
        }
        return -1;
    }

    private static void discardStderr(ProcessSession session) {
        try (InputStream stderr = session.process.getErrorStream()) {
            stderr.transferTo(java.io.OutputStream.nullOutputStream());
        } catch (IOException ignored) {
            // Stderr is deliberately discarded to prevent unbounded secret-bearing tool logs.
        }
    }

    private void watchExit(ProcessSession session) {
        try {
            session.process.waitFor();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            sessions.remove(session.id, session);
            session.close();
        }
    }

    public abstract static class Session implements AutoCloseable {
        /** Sends one bounded JSON-RPC message. */
        public abstract void send(String jsonMessage) throws IOException;

        @Override
        public abstract void close();
    }

    private final class ProcessSession extends Session {
        private final UUID id;
        private final WorkspaceId workspaceId;
        private final String languageId;
        private final Path workspaceHome;
        private final Map<String, Object> initializationOptions;
        private final Process process;
        private final WorkspaceProcessRegistry.Registration registration;
        private final Consumer<String> output;
        private final BufferedOutputStream input;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private ProcessSession(
                UUID id,
                WorkspaceId workspaceId,
                String languageId,
                Path workspaceHome,
                Map<String, Object> initializationOptions,
                Process process,
                WorkspaceProcessRegistry.Registration registration,
                Consumer<String> output) {
            this.id = id;
            this.workspaceId = workspaceId;
            this.languageId = languageId;
            this.workspaceHome = workspaceHome;
            this.initializationOptions =
                    initializationOptions == null ? Map.of() : Map.copyOf(initializationOptions);
            this.process = process;
            this.registration = registration;
            this.output = output;
            this.input = new BufferedOutputStream(process.getOutputStream());
        }

        @Override
        public void send(String jsonMessage) throws IOException {
            if (closed.get() || !process.isAlive()) {
                throw new IOException("language server session is closed");
            }
            if (jsonMessage == null
                    || jsonMessage.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
                throw new IOException("language-server message is too large");
            }
            ObjectNode safeMessage = LanguageProtocol.parseClientMessage(jsonMessage, json);
            JsonNode methodNode = safeMessage.get("method");
            String method =
                    methodNode == null || !methodNode.isString() ? "" : methodNode.stringValue();
            enforceWorkspaceRoot(method, safeMessage);
            if (!method.equals("initialize")) rewriteClientUris(safeMessage);
            byte[] body = json.writeValueAsBytes(safeMessage);
            synchronized (input) {
                input.write(
                        ("Content-Length: " + body.length + "\r\n\r\n")
                                .getBytes(StandardCharsets.US_ASCII));
                input.write(body);
                input.flush();
            }
        }

        private void enforceWorkspaceRoot(String method, ObjectNode message) throws IOException {
            if (!method.equals("initialize")) {
                return;
            }
            JsonNode paramsNode = message.get("params");
            if (!(paramsNode instanceof ObjectNode)) {
                throw new IOException("language-server initialize params are required");
            }
            ObjectNode params = (ObjectNode) paramsNode;
            String rootUri = workspaceHome.toUri().toString();
            params.put("rootUri", rootUri);
            params.remove("rootPath");
            params.set("initializationOptions", json.valueToTree(initializationOptions));
            ArrayNode folders = json.createArrayNode();
            ObjectNode folder = json.createObjectNode();
            folder.put("uri", rootUri);
            folder.put("name", workspaceHome.getFileName().toString());
            folders.add(folder);
            params.set("workspaceFolders", folders);
        }

        private void rewriteClientUris(JsonNode node) throws IOException {
            if (node instanceof ObjectNode) {
                ObjectNode object = (ObjectNode) node;
                List<Map.Entry<String, JsonNode>> fields = List.copyOf(object.properties());
                for (Map.Entry<String, JsonNode> field : fields) {
                    JsonNode value = field.getValue();
                    if (field.getKey().equals("uri") && value.isString()) {
                        Path file =
                                LanguageDocumentUris.toFilePath(
                                        value.stringValue(), workspaceId, workspaceHome);
                        object.put(field.getKey(), file.toUri().toString());
                    } else {
                        rewriteClientUris(value);
                    }
                }
            } else if (node instanceof ArrayNode) {
                for (JsonNode value : (ArrayNode) node) {
                    rewriteClientUris(value);
                }
            }
        }

        private JsonNode rewriteServerMessage(JsonNode message) {
            JsonNode safe = message.deepCopy();
            rewriteServerUris(safe);
            return safe;
        }

        private void rewriteServerUris(JsonNode node) {
            if (node instanceof ObjectNode) {
                ObjectNode object = (ObjectNode) node;
                List<Map.Entry<String, JsonNode>> fields = List.copyOf(object.properties());
                for (Map.Entry<String, JsonNode> field : fields) {
                    JsonNode value = field.getValue();
                    if ((field.getKey().equals("uri") || field.getKey().equals("rootUri"))
                            && value.isString()) {
                        object.put(field.getKey(), safeVirtualUri(value.stringValue()));
                    } else if (field.getKey().startsWith("file:")) {
                        JsonNode removed = object.remove(field.getKey());
                        object.set(safeVirtualUri(field.getKey()), removed);
                    } else if (value.isString()) {
                        String text = redactHostPath(value.stringValue());
                        if (!text.equals(value.stringValue())) object.put(field.getKey(), text);
                    } else {
                        rewriteServerUris(value);
                    }
                }
            } else if (node instanceof ArrayNode) {
                ArrayNode array = (ArrayNode) node;
                for (int index = 0; index < array.size(); index++) {
                    JsonNode value = array.get(index);
                    if (value.isString()) {
                        array.set(index, json.valueToTree(redactHostPath(value.stringValue())));
                    } else {
                        rewriteServerUris(value);
                    }
                }
            }
        }

        private void closeForWorkspaceStop() {
            if (!closed.get()) {
                try {
                    output.accept(
                            "{\"jsonrpc\":\"2.0\",\"method\":\"jahaziel/sessionClosed\",\"params\":{\"reason\":\"workspace stopped\"}}");
                } catch (RuntimeException disconnected) {
                    log.debug("language-service client is disconnected for {}", id);
                }
            }
            close();
        }

        private String safeVirtualUri(String uri) {
            return LanguageDocumentUris.toVirtualUri(uri, workspaceId, workspaceHome);
        }

        private String redactHostPath(String text) {
            return text.replace(workspaceHome.toString(), "<workspace>")
                    .replace(
                            workspaceHome.toUri().toString(),
                            "jahaziel://workspace/" + workspaceId.value() + "/");
        }

        private static void destroyAlive(List<ProcessHandle> descendants, boolean forcibly) {
            for (ProcessHandle descendant : descendants) {
                if (descendant.isAlive()) {
                    if (forcibly) {
                        descendant.destroyForcibly();
                    } else {
                        descendant.destroy();
                    }
                }
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            sessions.remove(id, this);
            List<ProcessHandle> descendants = new ArrayList<>();
            Iterator<ProcessHandle> descendantsIterator = process.descendants().iterator();
            while (descendantsIterator.hasNext()) {
                descendants.add(descendantsIterator.next());
            }
            try {
                synchronized (input) {
                    byte[] exit =
                            "{\"jsonrpc\":\"2.0\",\"method\":\"exit\"}"
                                    .getBytes(StandardCharsets.UTF_8);
                    input.write(
                            ("Content-Length: " + exit.length + "\r\n\r\n")
                                    .getBytes(StandardCharsets.US_ASCII));
                    input.write(exit);
                    input.flush();
                    input.close();
                }
            } catch (IOException ignored) {
                // Process teardown continues even when stdin is already closed.
            }
            try {
                process.waitFor(2, TimeUnit.SECONDS);
                destroyAlive(descendants, false);
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    destroyAlive(descendants, true);
                    process.destroyForcibly();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                destroyAlive(descendants, true);
                process.destroyForcibly();
            }
            registration.close();
        }
    }
}
