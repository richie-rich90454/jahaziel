/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/** Protocol and lifecycle branches of {@link SyncServer} missing from the baseline report. */
class SyncServerCoverageGapTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void acceptLoopExitsWhenSocketClosed(@TempDir Path root) throws Exception {
    SyncServer server = new SyncServer(0, root.resolve("state"));
    ServerSocket closed = new ServerSocket(0);
    closed.close();
    Field field = SyncServer.class.getDeclaredField("serverSocket");
    field.setAccessible(true);
    field.set(server, closed);
    Method loop = SyncServer.class.getDeclaredMethod("acceptLoop");
    loop.setAccessible(true);
    loop.invoke(server);
  }

  @Test
  void acceptLoopExitsWhenNeverStarted(@TempDir Path root) throws Exception {
    SyncServer server = new SyncServer(0, root.resolve("state"));
    Method loop = SyncServer.class.getDeclaredMethod("acceptLoop");
    loop.setAccessible(true);
    loop.invoke(server);
    server.start();
    try {
      assertTrue(server.boundPort() > 0);
    } finally {
      server.stop();
    }
  }

  @Test
  void moveAtomicallyFallsBackAcrossFilesystems(@TempDir Path root) throws Exception {
    Path staged = root.resolve("staged.bin");
    Files.writeString(staged, "atom");
    Path zip = root.resolve("fs.zip");
    try (FileSystem zfs =
        FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()), Map.of("create", "true"))) {
      Path target = zfs.getPath("atom.txt");
      Method move = SyncServer.class.getDeclaredMethod("moveAtomically", Path.class, Path.class);
      move.setAccessible(true);
      move.invoke(null, staged, target);
      assertEquals("atom", Files.readString(target));
      assertFalse(Files.exists(staged));
    }
  }

  @Test
  void pullMissingHomeServesEmptyTree(@TempDir Path root) throws Exception {
    SyncServer server = new SyncServer(0, root.resolve("state"));
    UUID workspace = UUID.randomUUID();
    server.register(workspace, root.resolve("ghost-home"), 1 << 20);
    server.start();
    try (Session session = open(server.boundPort(), workspace, server.secretFor(workspace))) {
      session.send(Map.of("op", "pull"));
      assertEquals(true, session.frame().get("ready"));
      session.blob();
      Map<String, Object> reply = session.frame();
      assertEquals(true, reply.get("ok"));
      assertEquals(0, ((Number) reply.get("files")).intValue());
    } finally {
      server.stop();
    }
  }

  @Test
  void pushSkipsEveryEscapeShape(@TempDir Path root) throws Exception {
    Path home = root.resolve("home");
    Files.createDirectories(home);
    SyncServer server = new SyncServer(0, root.resolve("state"));
    UUID workspace = UUID.randomUUID();
    server.register(workspace, home, 1 << 20);
    server.start();
    try (Session session = open(server.boundPort(), workspace, server.secretFor(workspace))) {
      session.send(Map.of("op", "push"));
      assertEquals(true, session.frame().get("ready"));
      session.blob(
          zip(Map.of("../escape.txt", "x", "/abs.txt", "y", "a\\b.txt", "z", "ok.txt", "k")));
      Map<String, Object> reply = session.frame();
      assertEquals(true, reply.get("ok"));
      assertEquals(1, ((Number) reply.get("files")).intValue());
    } finally {
      server.stop();
    }
    assertEquals("k", Files.readString(home.resolve("ok.txt")));
    assertFalse(Files.exists(root.resolve("escape.txt")));
    assertFalse(Files.exists(home.resolve("a")));
  }

  @Test
  void persistFailureWhenStateDirIsFile(@TempDir Path root) throws Exception {
    Path block = root.resolve("block");
    Files.writeString(block, "x");
    SyncServer server = new SyncServer(0, block);
    assertThrows(
        SyncException.class, () -> server.register(UUID.randomUUID(), root.resolve("home"), 1024));
  }

  @Test
  void loadFailureWhenRegistryUnreadable(@TempDir Path root) throws Exception {
    Path state = root.resolve("state");
    Path home = root.resolve("home");
    Files.createDirectories(home);
    SyncServer first = new SyncServer(0, state);
    first.register(UUID.randomUUID(), home, 1024);
    Path registry = state.resolve("registry.properties");
    assertTrue(Files.isRegularFile(registry));
    List<AclEntry> original = deny(registry, AclEntryPermission.READ_DATA);
    try {
      assertThrows(SyncException.class, () -> new SyncServer(0, state));
    } finally {
      restoreAcl(registry, original);
    }
  }

  @Test
  void resolveRejectsBadNames(@TempDir Path root) throws Exception {
    Path home = root.resolve("home");
    Files.createDirectories(home);
    Method resolve = SyncServer.class.getDeclaredMethod("resolve", Path.class, String.class);
    resolve.setAccessible(true);
    assertNull(resolve.invoke(null, home, null));
    assertNull(resolve.invoke(null, home, ""));
    assertNull(resolve.invoke(null, home, "a\\b.txt"));
    assertNull(resolve.invoke(null, home, "/abs.txt"));
    assertNull(resolve.invoke(null, home, "../escape.txt"));
    assertEquals(home.resolve("ok.txt"), resolve.invoke(null, home, "ok.txt"));
  }

  private Session open(int port, UUID workspace, String secret) throws IOException {
    Socket socket = new Socket("127.0.0.1", port);
    socket.setSoTimeout(5000);
    Session session = new Session(socket);
    session.send(Map.of("v", 1, "workspace", workspace.toString(), "secret", secret));
    assertEquals(true, session.frame().get("ok"));
    return session;
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

  private static List<AclEntry> deny(Path path, AclEntryPermission... permissions)
      throws Exception {
    AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
    assumeTrue(view != null, "ACLs unsupported on " + path);
    List<AclEntry> original = new ArrayList<>(view.getAcl());
    UserPrincipal owner = Files.getOwner(path);
    List<AclEntry> updated = new ArrayList<>(original);
    updated.add(
        0,
        AclEntry.newBuilder()
            .setType(AclEntryType.DENY)
            .setPrincipal(owner)
            .setPermissions(permissions)
            .build());
    view.setAcl(updated);
    return original;
  }

  private static void restoreAcl(Path path, List<AclEntry> original) {
    try {
      AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
      if (view != null) {
        view.setAcl(original);
      }
    } catch (Exception ignored) {
      // Best effort; the path may already be gone.
    }
  }

  /** Raw protocol session for driving the server. */
  final class Session implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

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
      byte[] payload = new byte[Math.toIntExact(size)];
      in.readFully(payload);
      return payload;
    }

    @Override
    public void close() throws IOException {
      socket.close();
    }
  }
}
