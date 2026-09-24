/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.GZIPOutputStream;

final class CliTestSupport {
  private CliTestSupport() {}

  record Output(int code, String out, String err) {}

  static Output dispatch(String... args) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code =
        Cli.dispatch(
            args,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
    return new Output(
        code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  static int freePort() throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  static void makeJar(Path jar, String version) throws Exception {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    if (version != null) {
      manifest.getMainAttributes().putValue("Implementation-Version", version);
    }
    Files.createDirectories(jar.toAbsolutePath().normalize().getParent());
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
      out.putNextEntry(new JarEntry("marker.txt"));
      out.write("marker".getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
  }

  static void writeEnv(Path dir, String body) throws Exception {
    Files.createDirectories(dir);
    Files.writeString(dir.resolve(".env"), body, StandardCharsets.UTF_8);
  }

  static String validEnv() {
    return """
        BASIS_AUTH_ISSUER=https://idp.example.com
        BASIS_REDIRECT_URI=https://code.example.com/api/oauth/callback
        BASIS_CLIENT_ID=jahaziel
        BASIS_CLIENT_SECRET=s3cret
        BASIS_RESOURCE=urn:basis:api:jahaziel
        PORT=11451
        """;
  }

  static void runTar(List<String> args, Path work) throws Exception {
    Process process =
        new ProcessBuilder(args).directory(work.toFile()).redirectErrorStream(true).start();
    String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exit = process.waitFor();
    if (exit != 0) {
      throw new IllegalStateException("tar setup failed: " + text);
    }
  }

  static String zipEntry(Path zip, String name) throws Exception {
    try (var file = new java.util.zip.ZipFile(zip.toFile())) {
      var entry = file.getEntry(name);
      if (entry == null) {
        return null;
      }
      return new String(file.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  static boolean zipHas(Path zip, String name) throws Exception {
    try (var file = new java.util.zip.ZipFile(zip.toFile())) {
      return file.getEntry(name) != null;
    }
  }

  static void writeGzip(Path file, byte[] raw) throws Exception {
    try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
      out.write(raw);
    }
  }

  static void writeSymlinkTarGz(Path archive) throws Exception {
    writeGzip(archive, tarEntry("data/link", new byte[0], '2', "target.txt"));
  }

  static byte[] tarEntry(String name, byte[] body, char type, String link) {
    ByteArrayOutputStream tar = new ByteArrayOutputStream();
    byte[] head = new byte[512];
    byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
    System.arraycopy(nameBytes, 0, head, 0, Math.min(nameBytes.length, 100));
    System.arraycopy("0000644\0".getBytes(StandardCharsets.UTF_8), 0, head, 100, 8);
    byte[] size = String.format("%011o\0", body.length).getBytes(StandardCharsets.UTF_8);
    System.arraycopy(size, 0, head, 124, 12);
    head[156] = (byte) type;
    byte[] linkBytes = link.getBytes(StandardCharsets.UTF_8);
    System.arraycopy(linkBytes, 0, head, 157, Math.min(linkBytes.length, 100));
    System.arraycopy("ustar \0".getBytes(StandardCharsets.UTF_8), 0, head, 257, 7);
    for (int i = 148; i < 156; i++) {
      head[i] = ' ';
    }
    long sum = 0;
    for (byte b : head) {
      sum += b & 0xFF;
    }
    byte[] check = String.format("%06o\0 ", sum).getBytes(StandardCharsets.UTF_8);
    System.arraycopy(check, 0, head, 148, 8);
    tar.write(head, 0, 512);
    tar.write(body, 0, body.length);
    int pad = 512 - (body.length % 512);
    if (pad < 512) {
      tar.write(new byte[pad], 0, pad);
    }
    tar.write(new byte[1024], 0, 1024);
    return tar.toByteArray();
  }

  static Doctor.Check find(List<Doctor.Check> checks, String name) {
    return checks.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
  }

  static void deleteBestEffort(Path root) {
    try (var walk = Files.walk(root)) {
      walk.sorted(java.util.Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (Exception ignored) {
                  // Best-effort test cleanup.
                }
              });
    } catch (Exception ignored) {
      // Best-effort test cleanup.
    }
  }
}
