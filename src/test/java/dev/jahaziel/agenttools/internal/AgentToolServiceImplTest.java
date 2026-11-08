/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agenttools.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentToolServiceImplTest {
  @TempDir Path temporary;

  @Test
  void extractsOnlyTheExpectedExecutableFromThePinnedArchiveShape() throws Exception {
    byte[] executable = new byte[] {0x7f, 'E', 'L', 'F', 1, 2, 3};
    Path archive = archive(entry("README.txt", "documentation"), entry("opencode", executable));
    Path destination = temporary.resolve("opencode");

    AgentToolServiceImpl.extractExecutable(archive, destination);

    assertArrayEquals(executable, Files.readAllBytes(destination));
  }

  @Test
  void rejectsSymbolicLinksInTheArchive() throws Exception {
    Path archive = archive(linkEntry("opencode"));

    assertThrows(
        IOException.class,
        () -> AgentToolServiceImpl.extractExecutable(archive, temporary.resolve("opencode")));
  }

  @Test
  void rejectsDuplicateExecutableEntries() throws Exception {
    Path archive = archive(entry("opencode", new byte[] {1}), entry("./opencode", new byte[] {2}));

    assertThrows(
        IOException.class,
        () -> AgentToolServiceImpl.extractExecutable(archive, temporary.resolve("opencode")));
  }

  private Path archive(TarEntry... entries) throws IOException {
    ByteArrayOutputStream tar = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(tar)) {
      for (TarEntry entry : entries) {
        byte[] header = new byte[512];
        writeString(header, 0, 100, entry.name());
        writeOctal(header, 100, 8, 0644);
        writeOctal(header, 108, 8, 0);
        writeOctal(header, 116, 8, 0);
        writeOctal(header, 124, 12, entry.data().length);
        writeOctal(header, 136, 12, 0);
        header[156] = entry.type();
        writeString(header, 257, 6, "ustar");
        output.write(header);
        output.write(entry.data());
        int padding = (512 - entry.data().length % 512) % 512;
        output.write(new byte[padding]);
      }
      output.write(new byte[1024]);
    }

    Path path = temporary.resolve("opencode.tar.gz");
    try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(path))) {
      tar.writeTo(gzip);
    }
    return path;
  }

  private static TarEntry entry(String name, String value) {
    return entry(name, value.getBytes(StandardCharsets.UTF_8));
  }

  private static TarEntry entry(String name, byte[] data) {
    return new TarEntry(name, (byte) '0', data);
  }

  private static TarEntry linkEntry(String name) {
    return new TarEntry(name, (byte) '2', new byte[0]);
  }

  private static void writeString(byte[] header, int offset, int length, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    System.arraycopy(bytes, 0, header, offset, Math.min(length, bytes.length));
  }

  private static void writeOctal(byte[] header, int offset, int length, long value) {
    String octal = Long.toOctalString(value);
    byte[] bytes = octal.getBytes(StandardCharsets.US_ASCII);
    java.util.Arrays.fill(header, offset, offset + length - 1, (byte) '0');
    int start = offset + length - bytes.length - 1;
    System.arraycopy(bytes, 0, header, start, bytes.length);
    header[offset + length - 1] = 0;
  }

  private record TarEntry(String name, byte type, byte[] data) {}
}
