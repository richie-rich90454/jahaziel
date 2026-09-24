/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.platform.Platforms;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliParsingTest {
  @TempDir private Path tmp;

  @Test
  void normalizeCopies() {
    String[] args = {"--demo", "--port=1"};
    String[] copy = Cli.normalize(args);
    assertEquals(List.of(args), List.of(copy));
    assertNotSame(args, copy);
    assertEquals(0, Cli.normalize(new String[0]).length);
  }

  @Test
  void missingVars() {
    assertEquals(5, Cli.missingVars(Map.of()).size());
    assertTrue(Cli.missingVars(Map.of()).containsAll(Cli.BASIS_VARS));
    assertTrue(Cli.missingVars(fullBasis()).isEmpty());
    assertTrue(Cli.missingVars(Map.of("BASIS_AUTH_ISSUER", "x")).contains("BASIS_REDIRECT_URI"));
    assertTrue(Cli.missingVars(Map.of("BASIS_AUTH_ISSUER", "   ")).contains("BASIS_AUTH_ISSUER"));
    assertTrue(Cli.missingVars(Map.of("BASIS_AUTH_ISSUER", "")).contains("BASIS_AUTH_ISSUER"));
  }

  @Test
  void mergeRealEnvWins() {
    Map<String, String> merged = Cli.merge(Map.of("A", "file", "B", "file"), Map.of("A", "env"));
    assertEquals("env", merged.get("A"));
    assertEquals("file", merged.get("B"));
  }

  @Test
  void portResolution() {
    assertEquals(1234, Cli.port(new String[] {"--port=1234"}, Map.of()));
    assertEquals(1234, Cli.port(new String[] {"--port", "1234"}, Map.of()));
    assertEquals(11451, Cli.port(new String[] {"--port=abc"}, Map.of()));
    assertEquals(11451, Cli.port(new String[] {"--port=99999"}, Map.of()));
    assertEquals(4321, Cli.port(new String[] {"--port=abc"}, Map.of("PORT", "4321")));
    assertEquals(4321, Cli.port(new String[] {}, Map.of("PORT", "4321")));
    assertEquals(4321, Cli.port(new String[] {}, Map.of("PORT", " 4321 ")));
    assertEquals(Cli.DEFAULT_PORT, Cli.port(new String[] {}, Map.of()));
    assertEquals(Cli.DEFAULT_PORT, Cli.port(new String[] {}, Map.of("PORT", "bogus")));
    assertEquals(Cli.DEFAULT_PORT, Cli.port(new String[] {}, Map.of("PORT", "")));
    assertEquals(Cli.DEFAULT_PORT, Cli.port(new String[] {}, Map.of("PORT", "0")));
    assertEquals(Cli.DEFAULT_PORT, Cli.port(new String[] {}, Map.of("PORT", "70000")));
  }

  @Test
  void backendDefaultsToLocal() {
    assertEquals("local", Cli.backend(Map.of()));
    assertEquals("local", Cli.backend(Map.of("JAHAZIEL_BACKEND", "")));
    assertEquals("local", Cli.backend(Map.of("JAHAZIEL_BACKEND", "   ")));
    assertEquals("custom", Cli.backend(Map.of("JAHAZIEL_BACKEND", " custom ")));
  }

  @Test
  void dataDirResolution() {
    Path jar = tmp.resolve("jar");
    assertEquals(jar.resolve("data"), Cli.dataDir(new String[] {}, jar));
    assertEquals(Path.of("/x/data"), Cli.dataDir(new String[] {"--data-dir=/x/data"}, jar));
    assertEquals(Path.of("/x/data"), Cli.dataDir(new String[] {"--data-dir", "/x/data"}, jar));
    assertEquals(jar.resolve("data"), Cli.dataDir(new String[] {"--data-dir="}, jar));
    assertEquals(jar.resolve("data"), Cli.dataDir(new String[] {"--data-dir", "  "}, jar));
  }

  @Test
  void flagForms() {
    assertEquals("1", Cli.flag(new String[] {"--port=1"}, "--port"));
    assertEquals("1", Cli.flag(new String[] {"--port", "1"}, "--port"));
    assertNull(Cli.flag(new String[] {"--port"}, "--port"));
    assertNull(Cli.flag(new String[] {"--demo"}, "--port"));
    assertNull(Cli.flag(new String[] {"--portrait=1"}, "--port"));
    assertNull(Cli.flag(new String[] {}, "--port"));
  }

  @Test
  void parseDotEnvFullMix() throws Exception {
    Path file = tmp.resolve(".env");
    String body =
        "# leading\r\n"
            + "BASIS_AUTH_ISSUER=https://idp.example.com\r\n"
            + "export BASIS_CLIENT_ID=jahaziel\n"
            + "BASIS_CLIENT_SECRET=\"s3cret # kept\"\n"
            + "SINGLE='one # kept'\n"
            + "EMPTY=\n"
            + "TRAILING=value # stripped\n"
            + "INNER=a=b=c\n"
            + "SPACED =trimmed\n"
            + "UNMATCHED=\"abc\n"
            + "ONECHAR=\"\"\n"
            + "\n"
            + "# tail\n"
            + "NOEQUALS\n"
            + "=nokey\n"
            + "MY KEY=skipped\n"
            + "HAS\tTAB=skipped\n";
    Files.writeString(file, body, StandardCharsets.UTF_8);
    Map<String, String> values = Cli.parseDotEnv(file);
    assertEquals("https://idp.example.com", values.get("BASIS_AUTH_ISSUER"));
    assertEquals("jahaziel", values.get("BASIS_CLIENT_ID"));
    assertEquals("s3cret # kept", values.get("BASIS_CLIENT_SECRET"));
    assertEquals("one # kept", values.get("SINGLE"));
    assertEquals("", values.get("EMPTY"));
    assertEquals("value", values.get("TRAILING"));
    assertEquals("a=b=c", values.get("INNER"));
    assertEquals("trimmed", values.get("SPACED"));
    assertEquals("\"abc", values.get("UNMATCHED"));
    assertEquals("", values.get("ONECHAR"));
    assertTrue(!values.containsKey("NOEQUALS"));
    assertTrue(!values.containsKey(""));
    assertTrue(!values.containsKey("MY KEY"));
    assertTrue(!values.containsKey("HAS\tTAB"));
  }

  @Test
  void parseDotEnvMissingAndDirectory() {
    assertTrue(Cli.parseDotEnv(tmp.resolve("absent.env")).isEmpty());
    assertTrue(Cli.parseDotEnv(tmp).isEmpty());
  }

  @Test
  void parseDotEnvMalformedBytesAreEmpty() throws Exception {
    Path file = tmp.resolve("bad.env");
    Files.write(file, new byte[] {(byte) 0xFF, (byte) 0xFE, 65});
    assertTrue(Cli.parseDotEnv(file).isEmpty());
  }

  @Test
  void malformedLines() throws Exception {
    Path clean = tmp.resolve("clean.env");
    Files.writeString(clean, "# c\nA=1\nexport B=2\nEMPTY=\n", StandardCharsets.UTF_8);
    assertTrue(Cli.malformedLines(clean).isEmpty());
    Path dirty = tmp.resolve("dirty.env");
    Files.writeString(
        dirty,
        "OK=1\nNOT VALID\n=nokey\nA B=C\nexport X Y=Z\nA\tB=C\n# c\n\n",
        StandardCharsets.UTF_8);
    List<String> bad = Cli.malformedLines(dirty);
    assertEquals(5, bad.size());
    assertTrue(bad.contains("NOT VALID"));
    assertEquals(1, Cli.malformedLines(tmp.resolve("absent.env")).size());
    assertEquals(1, Cli.malformedLines(tmp).size());
  }

  @Test
  void redactSecrets() {
    Map<String, String> file =
        Map.of(
            "BASIS_CLIENT_SECRET", "s",
            "MY_PASSWORD", "p",
            "MY_TOKEN", "t",
            "OTHER_SECRET", "x",
            "basis_resource", "urn",
            "NORMAL", "keep",
            "PORT", "11451");
    String redacted = Cli.redact(file);
    assertTrue(redacted.startsWith("# redacted by support-bundle\n"));
    assertTrue(redacted.contains("BASIS_CLIENT_SECRET=***\n"));
    assertTrue(redacted.contains("MY_PASSWORD=***\n"));
    assertTrue(redacted.contains("MY_TOKEN=***\n"));
    assertTrue(redacted.contains("OTHER_SECRET=***\n"));
    assertTrue(redacted.contains("basis_resource=***\n"));
    assertTrue(redacted.contains("NORMAL=keep\n"));
    assertTrue(redacted.contains("PORT=11451\n"));
    assertEquals("# redacted by support-bundle\n", Cli.redact(Map.of()));
  }

  @Test
  void sha256KnownVector() throws Exception {
    Path file = tmp.resolve("abc.txt");
    Files.write(file, "abc".getBytes(StandardCharsets.UTF_8));
    assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Cli.sha256(file));
    assertThrows(java.io.IOException.class, () -> Cli.sha256(tmp.resolve("absent")));
  }

  @Test
  void jarVersionBranches() throws Exception {
    Path with = tmp.resolve("with.jar");
    CliTestSupport.makeJar(with, "9.9.9");
    assertEquals("9.9.9", Cli.jarVersion(with));
    Path bare = tmp.resolve("bare.jar");
    CliTestSupport.makeJar(bare, null);
    assertEquals("unknown", Cli.jarVersion(bare));
    Path text = tmp.resolve("note.txt");
    Files.writeString(text, "not a jar", StandardCharsets.UTF_8);
    assertEquals("unknown", Cli.jarVersion(text));
    assertEquals("unknown", Cli.jarVersion(tmp.resolve("absent.jar")));
    assertEquals("unknown", Cli.jarVersion(tmp));
  }

  @Test
  void versionFallsBackInTests() {
    assertEquals("0.1.0-SNAPSHOT (development)", Cli.version());
  }

  @Test
  void rehSlugMatchesPlatforms() {
    assertEquals(Platforms.hostRehVariant(), Cli.rehSlug());
  }

  @Test
  void usageMentionsVerbs() {
    String usage = Cli.usage();
    for (String verb :
        List.of("init", "doctor", "upgrade", "backup", "restore", "logs", "support-bundle")) {
      assertTrue(usage.contains(verb), verb);
    }
    assertTrue(usage.contains("--demo"));
    assertTrue(usage.contains("--port"));
    assertTrue(usage.contains("--data-dir"));
  }

  @Test
  void jarFileAndDirFollowUserDir() {
    String previous = System.getProperty("user.dir");
    System.setProperty("user.dir", tmp.toString());
    try {
      Path file = Cli.jarFile();
      assertEquals(tmp.resolve("jahaziel.jar").toAbsolutePath().normalize(), file);
      assertEquals(tmp.toAbsolutePath().normalize(), Cli.jarDir());
    } finally {
      System.setProperty("user.dir", previous);
    }
  }

  @Test
  void bannerVariants() {
    assertBanner(true);
    assertBanner(false);
  }

  private void assertBanner(boolean demo) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    Path data = tmp.resolve("banner-" + demo).resolve("data");
    Cli.printBanner(out, tmp, data, 11451, "local", demo);
    String text = buffer.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains("jahaziel " + Cli.version()));
    assertTrue(text.contains("  host: "));
    assertTrue(text.contains("  java: "));
    assertTrue(text.contains("  data: " + data.toAbsolutePath().normalize()));
    assertTrue(text.contains("  port: 11451"));
    assertTrue(text.contains("  backend: local"));
    assertTrue(text.contains("  ide: " + Cli.rehSlug() + " 1.137.0"));
    assertTrue(text.contains(demo ? "(demo: stub IdP, BASIS_* ignored)" : "(basis-auth)"));
    assertTrue(text.contains(demo ? "starting (demo" : "starting (basis-auth)"));
  }

  private static Map<String, String> fullBasis() {
    return Map.of(
        "BASIS_AUTH_ISSUER", "https://idp.example.com",
        "BASIS_REDIRECT_URI", "https://code.example.com/api/oauth/callback",
        "BASIS_CLIENT_ID", "jahaziel",
        "BASIS_CLIENT_SECRET", "s3cret",
        "BASIS_RESOURCE", "urn:basis:api:jahaziel");
  }
}
