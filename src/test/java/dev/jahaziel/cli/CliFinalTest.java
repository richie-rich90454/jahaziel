/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Final gap tests: jar versions, restore magic bytes, ports, and malformed lines. */
class CliFinalTest {
  @TempDir private Path tmp;

  @Test
  void versionOfCoversBothSides() {
    assertEquals("0.1.0-SNAPSHOT (development)", Cli.versionOf(null));
    Package pkg = mock(Package.class);
    when(pkg.getImplementationVersion()).thenReturn(null);
    assertEquals("0.1.0-SNAPSHOT (development)", Cli.versionOf(pkg));
    when(pkg.getImplementationVersion()).thenReturn("9.9.9");
    assertEquals("9.9.9", Cli.versionOf(pkg));
    assertEquals("0.1.0-SNAPSHOT (development)", Cli.version());
  }

  @Test
  void jarVersionUnknownWithoutManifestEntry() throws Exception {
    Path jar = tmp.resolve("plain.jar");
    CliTestSupport.makeJar(jar, null);
    assertEquals("unknown", Cli.jarVersion(jar));
  }

  @Test
  void jarVersionUnknownWhenUnreadable() {
    assertEquals("unknown", Cli.jarVersion(tmp.resolve("missing.jar")));
    assertEquals("unknown", Cli.jarVersion(tmp));
  }

  @Test
  void restoreRejectsShortAndBadMagic() throws Exception {
    Path one = tmp.resolve("one.bin");
    Files.write(one, new byte[] {0x1f});
    var out = CliTestSupport.dispatch("restore", one.toString());
    assertEquals(1, out.code());
    assertTrue(out.err().contains("not a gzip archive"));

    Path bad = tmp.resolve("bad.bin");
    Files.write(bad, new byte[] {0x1f, 0x00});
    var out2 = CliTestSupport.dispatch("restore", bad.toString());
    assertEquals(1, out2.code());
    assertTrue(out2.err().contains("not a gzip archive"));
  }

  @Test
  void restoreRefusesWhenTarListingFails() throws Exception {
    Path fake = tmp.resolve("fake.tar.gz");
    CliTestSupport.writeGzip(fake, "not a tar at all".getBytes(StandardCharsets.UTF_8));
    var out = CliTestSupport.dispatch("restore", fake.toString());
    assertEquals(1, out.code());
  }

  @Test
  void portFallsBackOnOutOfRangeExplicit() {
    assertEquals(Cli.DEFAULT_PORT, Cli.port(new String[] {"--port=0"}, Map.of()));
    assertEquals(1234, Cli.port(new String[] {"--port=70000"}, Map.of("PORT", "1234")));
    assertEquals(4321, Cli.port(new String[] {}, Map.of("PORT", "4321")));
    assertEquals(Cli.DEFAULT_PORT, Cli.port(new String[] {}, Map.of("PORT", "bogus")));
    assertEquals(Cli.DEFAULT_PORT, Cli.port(new String[] {}, Map.of("PORT", "0")));
  }

  @Test
  void malformedLinesFlagsEveryShape() throws Exception {
    Path env = tmp.resolve(".env");
    Files.writeString(
        env,
        String.join(
            "\n",
            "GOOD=1",
            "export ALSO_GOOD=2",
            "export BAD LINE",
            "NOEQUALS",
            "KEY WITH SPACE=x",
            "TAB\tKEY=x",
            "=empty-key",
            " =blank-key",
            "# comment",
            "",
            "export QUOTED_OK=\"a b\""),
        StandardCharsets.UTF_8);
    List<String> bad = Cli.malformedLines(env);
    assertTrue(bad.contains("export BAD LINE"), bad.toString());
    assertTrue(bad.contains("NOEQUALS"), bad.toString());
    assertTrue(bad.contains("KEY WITH SPACE=x"), bad.toString());
    assertTrue(bad.contains("TAB\tKEY=x"), bad.toString());
    assertTrue(bad.contains("=empty-key"), bad.toString());
    assertTrue(bad.contains("=blank-key"), bad.toString());
    assertEquals(6, bad.size());
  }
}
