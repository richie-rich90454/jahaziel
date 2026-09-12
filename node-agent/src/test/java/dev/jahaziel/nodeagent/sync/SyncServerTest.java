/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Behavior of {@link SyncServer} against real loopback sockets. */
class SyncServerTest {
  private Path root;
  private Path home;
  private SyncServer server;
  private int port;
  private UUID workspaceId;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws Exception {
    root = Files.createTempDirectory("sync-test");
    home = root.resolve("home");
    Files.createDirectories(home);
    server = new SyncServer(0, root.resolve("state"));
    server.start();
    port = server.boundPort();
    workspaceId = UUID.randomUUID();
    server.register(workspaceId, home, 1 << 20);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.stop();
    try (Stream<Path> walk = Files.walk(root)) {
      walk.sorted(Comparator.reverseOrder()).forEach(SyncServerTest::deleteQuietly);
    }
  }

  @Test
  void registerPersistsSecret() {
    String secret = server.secretFor(workspaceId);
    assertEquals(64, secret.length());
    SyncServer reloaded = new SyncServer(0, root.resolve("state"));
    assertEquals(secret, reloaded.secretFor(workspaceId));
    server.deregister(workspaceId);
    assertThrows(IllegalArgumentException.class, () -> server.secretFor(workspaceId));
  }

  @Test
  void secretForUnknownThrows() {
    assertThrows(IllegalArgumentException.class, () -> server.secretFor(UUID.randomUUID()));
  }

  @Test
  void rejectsBadHandshake() throws Exception {
    try (Session denied = open(workspaceId, "wrong")) {
      assertEquals(false, denied.hello.get("ok"));
    }
    try (Session denied = open(UUID.randomUUID(), server.secretFor(workspaceId))) {
      assertEquals(false, denied.hello.get("ok"));
    }
  }

  @Test
  void pullRoundTrip() throws Exception {
    write(home.resolve("src/main.py"), "print(1)\n");
    write(home.resolve("notes.txt"), "hello\n");
    try (Session session = open(workspaceId, server.secretFor(workspaceId))) {
      assertEquals(true, session.hello.get("ok"));
      session.send(Map.of("op", "pull"));
      Map<String, String> pulled = unzip(session.blob(), root.resolve("guest"));
      assertEquals(Map.of("src/main.py", "print(1)\n", "notes.txt", "hello\n"), pulled);
      assertEquals(true, session.frame().get("ok"));
      session.send(Map.of("op", "bye"));
      assertEquals(true, session.frame().get("ok"));
    }
  }

  @Test
  void pullEmptyHome() throws Exception {
    try (Session session = open(workspaceId, server.secretFor(workspaceId))) {
      session.send(Map.of("op", "pull"));
      assertTrue(unzip(session.blob(), root.resolve("guest")).isEmpty());
      assertEquals(true, session.frame().get("ok"));
    }
  }

  @Test
  void pushMirrors() throws Exception {
    write(home.resolve("keep.txt"), "old\n");
    write(home.resolve("drop.txt"), "gone\n");
    try (Session session = open(workspaceId, server.secretFor(workspaceId))) {
      session.send(Map.of("op", "push"));
      assertEquals(true, session.frame().get("ready"));
      session.blob(zip(Map.of("keep.txt", "new\n", "added.txt", "added\n")));
      Map<String, Object> reply = session.frame();
      assertEquals(true, reply.get("ok"));
      assertEquals(2, ((Number) reply.get("files")).intValue());
    }
    assertEquals("new\n", read(home.resolve("keep.txt")));
    assertEquals("added\n", read(home.resolve("added.txt")));
    assertFalse(Files.exists(home.resolve("drop.txt")));
  }

  @Test
  void pushRejectsTraversal() throws Exception {
    try (Session session = open(workspaceId, server.secretFor(workspaceId))) {
      session.send(Map.of("op", "push"));
      assertEquals(true, session.frame().get("ready"));
      session.blob(zip(Map.of("../../evil.txt", "evil", "/abs.txt", "abs", "ok.txt", "ok")));
      assertEquals(true, session.frame().get("ok"));
    }
    assertFalse(Files.exists(root.resolve("evil.txt")));
    assertEquals("ok", read(home.resolve("ok.txt")));
  }

  @Test
  void pushEnforcesQuota() throws Exception {
    server.register(workspaceId, home, 64);
    try (Session session = open(workspaceId, server.secretFor(workspaceId))) {
      session.send(Map.of("op", "push"));
      assertEquals(true, session.frame().get("ready"));
      session.blob(zip(Map.of("big.txt", "x".repeat(4096))));
      Map<String, Object> reply = session.frame();
      assertEquals(false, reply.get("ok"));
    }
  }

  @Test
  void unknownOpDenied() throws Exception {
    try (Session session = open(workspaceId, server.secretFor(workspaceId))) {
      session.send(Map.of("op", "frobnicate"));
      assertEquals(false, session.frame().get("ok"));
      assertThrows(EOFException.class, session::frame);
    }
  }

  @Test
  void registerValidates() {
    assertThrows(NullPointerException.class, () -> server.register(null, home, 1));
    assertThrows(NullPointerException.class, () -> server.register(workspaceId, null, 1));
    assertThrows(IllegalArgumentException.class, () -> server.register(workspaceId, home, -1));
    assertThrows(IllegalArgumentException.class, () -> new SyncServer(-1, root));
    assertThrows(NullPointerException.class, () -> new SyncServer(0, null));
  }

  private static void write(Path path, String content) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(path, content, StandardCharsets.UTF_8);
  }

  private static String read(Path path) throws IOException {
    return Files.readString(path, StandardCharsets.UTF_8);
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException busy) {
      // Best effort test cleanup on Windows file locks.
    }
  }

  private byte[] zip(Map<String, String> files) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
      for (Map.Entry<String, String> file : files.entrySet()) {
        zip.putNextEntry(new ZipEntry(file.getKey()));
        zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return buffer.toByteArray();
  }

  private Map<String, String> unzip(byte[] payload, Path target) throws IOException {
    HashMap<String, String> files = new HashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(payload))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          Path file = target.resolve(entry.getName().replace('/', File.separatorChar));
          Files.createDirectories(file.getParent());
          try (OutputStream content = Files.newOutputStream(file)) {
            zip.transferTo(content);
          }
          files.put(entry.getName(), Files.readString(file, StandardCharsets.UTF_8));
        }
        zip.closeEntry();
      }
    }
    return files;
  }

  private Session open(UUID workspace, String secret) throws IOException {
    Socket socket = new Socket("127.0.0.1", port);
    Session session = new Session(socket);
    session.send(
        Map.of("v", 1, "op", "hello", "workspace", workspace.toString(), "secret", secret));
    session.hello = session.frame();
    return session;
  }

  /** Raw protocol session for driving the server. */
  final class Session implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private Map<String, Object> hello = Map.of();

    Session(Socket socket) throws IOException {
      this.socket = socket;
      this.in = new DataInputStream(socket.getInputStream());
      this.out = new DataOutputStream(socket.getOutputStream());
    }

    void send(Map<String, Object> message) throws IOException {
      byte[] body;
      try {
        body = mapper.writeValueAsBytes(message);
      } catch (tools.jackson.core.JacksonException failure) {
        throw new IOException(failure);
      }
      out.writeInt(body.length);
      out.write(body);
      out.flush();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> frame() throws IOException {
      int size = in.readInt();
      byte[] body = new byte[size];
      in.readFully(body);
      try {
        return mapper.readValue(body, Map.class);
      } catch (tools.jackson.core.JacksonException failure) {
        throw new IOException(failure);
      }
    }

    void blob(byte[] payload) throws IOException {
      out.writeLong(payload.length);
      out.write(payload);
      out.flush();
    }

    byte[] blob() throws IOException {
      long size = in.readLong();
      if (size < 0 || size > 1 << 26) {
        throw new IOException("absurd blob: " + size);
      }
      byte[] payload = new byte[(int) size];
      in.readFully(payload);
      return payload;
    }

    @Override
    public void close() throws IOException {
      socket.close();
    }
  }
}
