/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code .env} parsing: CRLF/LF, comments, quotes, and real-env-wins. */
class DotEnvTest {
  @TempDir private Path tmp;

  @Test
  void parsesLfAndCrlfWithCommentsAndQuotes() throws Exception {
    Path file = tmp.resolve(".env");
    String content =
        "# leading comment\r\n"
            + "BASIS_AUTH_ISSUER=https://idp.example.com\r\n"
            + "BASIS_CLIENT_SECRET=\"s3cret # not a comment\"\n"
            + "BASIS_CLIENT_ID='jahaziel'\n"
            + "EMPTY=\n"
            + "export BASIS_RESOURCE=urn:basis:api:jahaziel # trailing comment\n";
    Files.writeString(file, content, StandardCharsets.UTF_8);
    Map<String, String> values = DotEnv.parse(file);
    assertEquals("https://idp.example.com", values.get("BASIS_AUTH_ISSUER"));
    assertEquals("s3cret # not a comment", values.get("BASIS_CLIENT_SECRET"));
    assertEquals("jahaziel", values.get("BASIS_CLIENT_ID"));
    assertEquals("", values.get("EMPTY"));
    assertEquals("urn:basis:api:jahaziel", values.get("BASIS_RESOURCE"));
  }

  @Test
  void missingFileIsEmpty() {
    assertTrue(DotEnv.parse(tmp.resolve("no-such.env")).isEmpty());
  }

  @Test
  void realEnvWinsOverFile() {
    Map<String, String> file = Map.of("BASIS_CLIENT_ID", "from-file", "ONLY_FILE", "yes");
    Map<String, String> real = Map.of("BASIS_CLIENT_ID", "from-env");
    Map<String, String> merged = DotEnv.withRealEnvWins(file, real);
    assertEquals("from-env", merged.get("BASIS_CLIENT_ID"));
    assertEquals("yes", merged.get("ONLY_FILE"));
  }

  @Test
  void missingBasisVarsListsGaps() {
    Map<String, String> partial =
        Map.of("BASIS_AUTH_ISSUER", "https://idp.example.com", "BASIS_CLIENT_ID", "jahaziel");
    List<String> missing = DotEnv.missingBasisVars(partial);
    assertTrue(missing.contains("BASIS_REDIRECT_URI"));
    assertTrue(missing.contains("BASIS_CLIENT_SECRET"));
    assertTrue(missing.contains("BASIS_RESOURCE"));
    assertEquals(5, DotEnv.missingBasisVars(Map.of()).size());
    assertTrue(DotEnv.missingBasisVars(full()).isEmpty());
  }

  private static Map<String, String> full() {
    return Map.of(
        "BASIS_AUTH_ISSUER", "https://idp.example.com",
        "BASIS_REDIRECT_URI", "https://code.example.com/api/oauth/callback",
        "BASIS_CLIENT_ID", "jahaziel",
        "BASIS_CLIENT_SECRET", "s3cret",
        "BASIS_RESOURCE", "urn:basis:api:jahaziel");
  }
}
