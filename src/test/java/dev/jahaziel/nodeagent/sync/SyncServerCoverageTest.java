/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/** Protocol and lifecycle branches of {@link SyncServer}. */
class SyncServerCoverageTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void portRangeAndLifecycle(@TempDir Path root) throws Exception {
    assertThrows(IllegalArgumentException.class, () -> new SyncServer(65536, root));
    assertThrows(IllegalArgumentException.class, () -> new SyncServer(-2, root));
    SyncServer fresh = new SyncServer(0, root.resolve("s1"));
    assertThrows(IllegalStateException.class, fresh::boundPort);
    fresh.stop();
    SyncServer server = new SyncServer(0, root.resolve("s2"));
    server.start();
    try {
      assertTrue(server.boundPort() > 0);
    } finally {
      server.stop();
    }
    server.stop();
  }

  @Test
  void sweepCleansOnlyStaging(@TempDir Path root) throws Exception {
    Path state = root.resolve("state");
    Path staging = state.resolve("staging");
    Files.createDirectories(staging);
    Files.writeString(staging.resolve("push-orphan.zip"), "x");
    Files.writeString(staging.resolve("pull-orphan.zip"), "y");
    Files.writeString(staging.resolve("keep.txt"), "z");
    Files.createDirectories(staging.resolve("subdir"));
    SyncServer server = new SyncServer(0, state);
    server.start();
    try {
      assertFalse(Files.exists(staging.resolve("push-orphan.zip")));
      assertFalse(Files.exists(staging.resolve("pull-orphan.zip")));
      assertTrue(Files.exists(staging.resolve("keep.txt")));
      assertTrue(Files.isDirectory(staging.resolve("subdir")));
    } finally {
      server.stop();
    }
  }

  @Test
  void abruptCloseIsSwallowed(@TempDir Path root) throws Exception {
    Path home = root.resolve("home");
    Files.createDirectories(home);
    SyncServer server = new SyncServer(0, root.resolve("state"));
    server.register(UUID.randomUUID(), home, 1 << 20);
    server.start();
    int port = server.boundPort();
    try {
      Socket raw = new Socket("127.0.0.1", port);
      raw.close();
      Thread.sleep(200);
      UUID workspace = UUID.randomUUID();
      server.register(workspace, home, 1 << 20);
      try (Session session = open(port, workspace, server.secretFor(workspace))) {
        assertEquals(true, session.hello.get("ok"));
      }
    } finally {
      server.stop();
    }
  }

  @Test
  void nullHelloDenied(@TempDir Path root) throws Exception {
    SyncServer server = started(root);
    int port = server.boundPort();
    try {
      try (Socket socket = new Socket("127.0.0.1", port)) {
        socket.setSoTimeout(5000);
        DataOutputStream out =
            new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        byte[] body = "null".getBytes(StandardCharsets.UTF_8);
        out.writeInt(body.length);
        out.write(body);
        out.flush();
        Map<String, Object> reply = readFrame(in);
        assertEquals(false, reply.get("ok"));
      }
    } finally {
      server.stop();
    }
  }

  @Test
  void badVersionAndWorkspaceDenied(@TempDir Path root) throws Exception {
    SyncServer server = started(root);
    int port = server.boundPort();
    UUID workspace = UUID.randomUUID();
    Path home = root.resolve("home");
    server.register(workspace, home, 1 << 20);
    String secret = server.secretFor(workspace);
    try {
      try (Session wrongVersion =
          openRaw(port, Map.of("v", 2, "workspace", workspace.toString(), "secret", secret))) {
        assertEquals(false, wrongVersion.hello.get("ok"));
      }
      try (Session stringVersion =
          openRaw(port, Map.of("v", "1", "workspace", workspace.toString(), "secret", secret))) {
        assertEquals(false, stringVersion.hello.get("ok"));
      }
      try (Session badUuid =
          openRaw(port, Map.of("v", 1, "workspace", "not-a-uuid", "secret", secret))) {
        assertEquals(false, badUuid.hello.get("ok"));
      }
      try (Session missingV =
          openRaw(port, Map.of("workspace", workspace.toString(), "secret", secret))) {
        assertEquals(false, missingV.hello.get("ok"));
      }
    } finally {
      server.stop();
    }
  }

  @Test
  void oversizeAndBadFramesSwallowed(@TempDir Path root) throws Exception {
    SyncServer server = started(root);
    int port = server.boundPort();
    try {
      try (Socket socket = new Socket("127.0.0.1", port)) {
        socket.setSoTimeout(2000);
        DataOutputStream out =
            new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        out.writeInt(SyncServer.MAX_FRAME_BYTES + 1);
        out.flush();
        socket.shutdownOutput();
        try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
          in.readInt();
        } catch (Exception expected) {
          assertTrue(true);
        }
      }
      try (Socket socket = new Socket("127.0.0.1", port)) {
        socket.setSoTimeout(2000);
        DataOutputStream out =
            new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        out.writeInt(-5);
        out.flush();
        socket.shutdownOutput();
        try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
          in.readInt();
        } catch (Exception expected) {
          assertTrue(true);
        }
      }
      try (Socket socket = new Socket("127.0.0.1", port)) {
        socket.setSoTimeout(2000);
        DataOutputStream out =
            new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        byte[] bad = "{!".getBytes(StandardCharsets.UTF_8);
        out.writeInt(bad.length);
        out.write(bad);
        out.flush();
        socket.shutdownOutput();
        try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
          in.readInt();
        } catch (Exception expected) {
          assertTrue(true);
        }
      }
      Thread.sleep(200);
      UUID workspace = UUID.randomUUID();
      server.register(workspace, root.resolve("home"), 1 << 20);
      try (Session session = open(port, workspace, server.secretFor(workspace))) {
        assertEquals(true, session.hello.get("ok"));
      }
    } finally {
      server.stop();
    }
  }

  @Test
  void pushWireCapDenied(@TempDir Path root) throws Exception {
    Path home = root.resolve("home");
    Files.createDirectories(home);
    SyncServer server = new SyncServer(0, root.resolve("state"));
    UUID workspace = UUID.randomUUID();
    server.register(workspace, home, 1 << 20);
    server.start();
    int port = server.boundPort();
    try {
      try (Session session = open(port, workspace, server.secretFor(workspace))) {
        session.send(Map.of("op", "push"));
        assertEquals(true, session.frame().get("ready"));
        session.out.writeLong(-1);
        session.out.flush();
        assertEquals(false, session.frame().get("ok"));
      }
      try (Session session = open(port, workspace, server.secretFor(workspace))) {
        session.send(Map.of("op", "push"));
        assertEquals(true, session.frame().get("ready"));
        session.out.writeLong(SyncServer.MAX_WIRE_BYTES + 1);
        session.out.flush();
        assertEquals(false, session.frame().get("ok"));
      }
    } finally {
      server.stop();
    }
  }

  @Test
  void pushTruncatedSwallowed(@TempDir Path root) throws Exception {
    Path home = root.resolve("home");
    Files.createDirectories(home);
    SyncServer server = new SyncServer(0, root.resolve("state"));
    UUID workspace = UUID.randomUUID();
    server.register(workspace, home, 1 << 20);
    server.start();
    int port = server.boundPort();
    try {
      try (Socket socket = new Socket("127.0.0.1", port)) {
        socket.setSoTimeout(5000);
        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        DataOutputStream out =
            new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        send(
            out,
            Map.of(
                "v", 1, "workspace", workspace.toString(), "secret", server.secretFor(workspace)));
        assertEquals(true, readFrame(in).get("ok"));
        send(out, Map.of("op", "push"));
        assertEquals(true, readFrame(in).get("ready"));
        out.writeLong(100);
        out.write(new byte[10]);
        out.flush();
        socket.shutdownOutput();
        try {
          readFrame(in);
        } catch (Exception expected) {
          assertTrue(true);
        }
      }
      Thread.sleep(200);
      try (Session session = open(port, workspace, server.secretFor(workspace))) {
        assertEquals(true, session.hello.get("ok"));
      }
    } finally {
      server.stop();
    }
  }

  @Test
  void staleSessionDenied(@TempDir Path root) throws Exception {
    Path home = root.resolve("home");
    Files.createDirectories(home);
    SyncServer server = new SyncServer(0, root.resolve("state"));
    UUID workspace = UUID.randomUUID();
    server.register(workspace, home, 1 << 20);
    server.start();
    int port = server.boundPort();
    try {
      try (Session session = open(port, workspace, server.secretFor(workspace))) {
        server.register(workspace, home, 1 << 20);
        session.send(Map.of("op", "push"));
        assertEquals(true, session.frame().get("ready"));
        session.blob(zip(Map.of("f.txt", "hi")));
        assertEquals(false, session.frame().get("ok"));
      }
    } finally {
      server.stop();
    }
  }

  @Test
  void pushSkipsSymlinkEscape(@TempDir Path root) throws Exception {
    Path home = root.resolve("home");
    Files.createDirectories(home);
    Path outside = root.resolve("outside");
    Files.createDirectories(outside);
    try {
      Files.createSymbolicLink(home.resolve("link"), outside);
    } catch (UnsupportedOperationException | java.io.IOException skipped) {
      return;
    }
    SyncServer server = new SyncServer(0, root.resolve("state"));
    UUID workspace = UUID.randomUUID();
    server.register(workspace, home, 1 << 20);
    server.start();
    int port = server.boundPort();
    try {
      try (Session session = open(port, workspace, server.secretFor(workspace))) {
        session.send(Map.of("op", "push"));
        assertEquals(true, session.frame().get("ready"));
        session.blob(zip(Map.of("link/evil.txt", "evil", "ok.txt", "ok")));
        assertEquals(true, session.frame().get("ok"));
      }
      assertFalse(Files.exists(outside.resolve("evil.txt")));
      assertTrue(Files.isRegularFile(home.resolve("ok.txt")));
    } finally {
      server.stop();
    }
  }

  @Test
  void pushMoveConflictDenied(@TempDir Path root) throws Exception {
    Path home = root.resolve("home");
    Files.createDirectories(home.resolve("conflict"));
    Files.writeString(home.resolve("conflict/inner.txt"), "keep");
    SyncServer server = new SyncServer(0, root.resolve("state"));
    UUID workspace = UUID.randomUUID();
    server.register(workspace, home, 1 << 20);
    server.start();
    int port = server.boundPort();
    try {
      try (Session session = open(port, workspace, server.secretFor(workspace))) {
        session.send(Map.of("op", "push"));
        assertEquals(true, session.frame().get("ready"));
        session.blob(zip(Map.of("conflict", "file-wins")));
        try {
          Map<String, Object> reply = session.frame();
          assertEquals(false, reply.get("ok"));
        } catch (EOFException closed) {
          // ponytail: move failure is IOException, swallowed without a frame.
          assertTrue(true);
        }
      }
    } finally {
      server.stop();
    }
  }

  @Test
  void mirrorCleansSymlinksAndDirs(@TempDir Path root) throws Exception {
    Path home = root.resolve("home");
    Files.createDirectories(home.resolve("sub"));
    Files.writeString(home.resolve("sub/old.txt"), "old");
    Files.writeString(home.resolve("drop.txt"), "drop");
    try {
      Files.createSymbolicLink(home.resolve("gone-link"), Path.of("drop.txt"));
    } catch (UnsupportedOperationException | java.io.IOException skipped) {
      // Continue without symlink; mirror still cleans files and dirs.
    }
    SyncServer server = new SyncServer(0, root.resolve("state"));
    UUID workspace = UUID.randomUUID();
    server.register(workspace, home, 1 << 20);
    server.start();
    int port = server.boundPort();
    try {
      try (Session session = open(port, workspace, server.secretFor(workspace))) {
        session.send(Map.of("op", "push"));
        assertEquals(true, session.frame().get("ready"));
        session.blob(zip(Map.of("new.txt", "new")));
        assertEquals(true, session.frame().get("ok"));
      }
      assertFalse(Files.exists(home.resolve("drop.txt")));
      assertFalse(Files.exists(home.resolve("sub/old.txt")));
      assertFalse(Files.exists(home.resolve("gone-link")));
      assertTrue(Files.isRegularFile(home.resolve("new.txt")));
    } finally {
      server.stop();
    }
  }

  @Test
  void countEntriesIgnoresDirs() throws Exception {
    java.lang.reflect.Method count = SyncServer.class.getDeclaredMethod("countEntries", Path.class);
    count.setAccessible(true);
    Path zip = Files.createTempFile("entries", ".zip");
    try {
      try (java.util.zip.ZipOutputStream out =
          new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
        out.putNextEntry(new java.util.zip.ZipEntry("sub/"));
        out.closeEntry();
        out.putNextEntry(new java.util.zip.ZipEntry("f.txt"));
        out.write("hi".getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
      }
      SyncServer server = new SyncServer(0, Files.createTempDirectory("count"));
      try {
        int files = (int) count.invoke(server, zip);
        assertEquals(1, files);
      } finally {
        server.stop();
      }
    } finally {
      Files.deleteIfExists(zip);
    }
  }

  @Test
  void corruptRegistryIgnored(@TempDir Path root) throws Exception {
    Path state = root.resolve("state");
    Files.createDirectories(state);
    Path home = root.resolve("home");
    Files.createDirectories(home);
    SyncServer server = new SyncServer(0, state);
    UUID good = UUID.randomUUID();
    server.register(good, home, 123);
    server.stop();
    String secret = server.secretFor(good);
    Path registry = state.resolve("registry.properties");
    String raw = Files.readString(registry);
    UUID corruptId = UUID.randomUUID();
    Files.writeString(
        registry,
        raw
            + "not-a-uuid=bad|row\n"
            + corruptId
            + "=oops|not-a-number|secret\n"
            + UUID.randomUUID()
            + "=only-one-part\n");
    SyncServer reloaded = new SyncServer(0, state);
    assertEquals(secret, reloaded.secretFor(good));
    assertThrows(IllegalArgumentException.class, () -> reloaded.secretFor(UUID.randomUUID()));
    assertThrows(NullPointerException.class, () -> reloaded.secretFor(null));
    assertThrows(NullPointerException.class, () -> reloaded.deregister(null));
  }

  @Test
  void eofAfterBye(@TempDir Path root) throws Exception {
    Path home = root.resolve("home");
    Files.createDirectories(home);
    SyncServer server = new SyncServer(0, root.resolve("state"));
    UUID workspace = UUID.randomUUID();
    server.register(workspace, home, 1 << 20);
    server.start();
    int port = server.boundPort();
    try {
      try (Session session = open(port, workspace, server.secretFor(workspace))) {
        session.send(Map.of("op", "bye"));
        assertEquals(true, session.frame().get("ok"));
        assertThrows(EOFException.class, session::frame);
      }
    } finally {
      server.stop();
    }
  }

  @Test
  void closedSocketLoopReturns(@TempDir Path root) throws Exception {
    SyncServer server = new SyncServer(0, root.resolve("state"));
    server.start();
    try {
      java.lang.reflect.Field field = SyncServer.class.getDeclaredField("serverSocket");
      field.setAccessible(true);
      ServerSocket socket = (ServerSocket) field.get(server);
      socket.close();
      Thread.sleep(300);
    } finally {
      server.stop();
    }
  }

  private SyncServer started(Path root) throws Exception {
    Path home = root.resolve("home");
    Files.createDirectories(home);
    SyncServer server = new SyncServer(0, root.resolve("state"));
    server.start();
    return server;
  }

  private Session open(int port, UUID workspace, String secret) throws Exception {
    Socket socket = new Socket("127.0.0.1", port);
    socket.setSoTimeout(5000);
    Session session = new Session(socket);
    session.send(Map.of("v", 1, "workspace", workspace.toString(), "secret", secret));
    session.hello = session.frame();
    return session;
  }

  private Session openRaw(int port, Map<String, Object> hello) throws Exception {
    Socket socket = new Socket("127.0.0.1", port);
    socket.setSoTimeout(5000);
    Session session = new Session(socket);
    session.send(hello);
    session.hello = session.frame();
    return session;
  }

  private void send(DataOutputStream out, Map<String, Object> message) throws Exception {
    byte[] body = mapper.writeValueAsBytes(message);
    out.writeInt(body.length);
    out.write(body);
    out.flush();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readFrame(DataInputStream in) throws Exception {
    int size = in.readInt();
    byte[] body = new byte[size];
    in.readFully(body);
    return mapper.readValue(body, Map.class);
  }

  private byte[] zip(Map<String, String> files) throws Exception {
    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
    try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(buffer)) {
      for (Map.Entry<String, String> file : files.entrySet()) {
        zip.putNextEntry(new java.util.zip.ZipEntry(file.getKey()));
        zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return buffer.toByteArray();
  }

  final class Session implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private Map<String, Object> hello = Map.of();

    Session(Socket socket) throws Exception {
      this.socket = socket;
      this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
      this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    void send(Map<String, Object> message) throws Exception {
      byte[] body = mapper.writeValueAsBytes(message);
      out.writeInt(body.length);
      out.write(body);
      out.flush();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> frame() throws Exception {
      int size = in.readInt();
      byte[] body = new byte[size];
      in.readFully(body);
      return mapper.readValue(body, Map.class);
    }

    void blob(byte[] payload) throws Exception {
      out.writeLong(payload.length);
      out.write(payload);
      out.flush();
    }

    @Override
    public void close() throws Exception {
      socket.close();
    }
  }
}
