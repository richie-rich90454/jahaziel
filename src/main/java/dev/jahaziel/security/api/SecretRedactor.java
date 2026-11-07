/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** Redacts common credentials before process output is stored or streamed to the workbench. */
public final class SecretRedactor {
  private static final Pattern ASSIGNED_SECRET =
      Pattern.compile(
          "(?i)(\\b(?:token|password|passwd|secret|api[_-]?key|client[_-]?secret|authorization)\\b\\s*[:=]\\s*)(?:Bearer\\s+[^\\s,;]+|\"[^\"]*\"|'[^']*'|[^\\s,;]+)");
  private static final Pattern BEARER_TOKEN =
      Pattern.compile("(?i)(\\bBearer\\s+)[A-Za-z0-9._~+/-]+=*");
  private static final Pattern JWT =
      Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b");
  private static final Pattern INCOMPLETE_ASSIGNMENT =
      Pattern.compile(
          "(?i)\\b(?:token|password|passwd|secret|api[_-]?key|client[_-]?secret|authorization)\\b"
              + "\\s*[:=]\\s*(?:Bearer\\s+)?(?:\"[^\"]*|'[^']*|[^\\s,;\\\"']*)\\z");
  private static final Pattern ASSIGNMENT_LABEL_AT_END =
      Pattern.compile(
          "(?i)\\b(?:token|password|passwd|secret|api[_-]?key|client[_-]?secret|authorization)\\b\\s*\\z");
  private static final Pattern INCOMPLETE_BEARER =
      Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]*=*\\z");
  private static final Pattern INCOMPLETE_JWT =
      Pattern.compile("\\beyJ[A-Za-z0-9_-]*(?:\\.[A-Za-z0-9_-]*){0,2}\\z");
  private static final String[] SENSITIVE_PREFIXES = {
    "authorization", "client_secret", "client-secret", "password", "passwd", "token",
    "api_key", "api-key", "apikey", "secret", "bearer", "eyJ"
  };
  private static final int MAX_PENDING_CHARS = 64 * 1024;
  private static final String OVERSIZED_SECRET_NOTICE =
      "\r\n[jahaziel] terminal output suppressed after an oversized secret-like value\r\n";

  private SecretRedactor() {}

  public static String redact(String text) {
    Objects.requireNonNull(text, "text");
    String assignmentSafe = ASSIGNED_SECRET.matcher(text).replaceAll("$1[redacted]");
    String bearerSafe = BEARER_TOKEN.matcher(assignmentSafe).replaceAll("$1[redacted]");
    return JWT.matcher(bearerSafe).replaceAll("[redacted token]");
  }

  /** Stateful redaction for streams whose read boundaries can split credentials. */
  public static final class Stream {
    private final StringBuilder pending = new StringBuilder();
    private boolean suppressed;

    /** Adds raw process output and returns only text that is safe to publish now. */
    public synchronized String accept(String text) {
      Objects.requireNonNull(text, "text");
      if (suppressed || text.isEmpty()) return "";
      pending.append(text);
      int sensitiveStart = incompleteSensitiveStart(pending);
      int safeEnd = sensitiveStart;
      if (sensitiveStart == pending.length()) {
        safeEnd = partialSensitivePrefixStart(pending);
      }
      if (pending.length() - safeEnd > MAX_PENDING_CHARS) {
        String safePrefix = redact(pending.substring(0, safeEnd));
        pending.setLength(0);
        suppressed = true;
        return safePrefix + OVERSIZED_SECRET_NOTICE;
      }
      if (safeEnd <= 0) return "";
      String safe = redact(pending.substring(0, safeEnd));
      pending.delete(0, safeEnd);
      return safe;
    }

    /** Flushes the final read, conservatively masking an unfinished credential. */
    public synchronized String finish() {
      if (suppressed || pending.isEmpty()) return "";
      int sensitiveStart = incompleteSensitiveStart(pending);
      String safe;
      if (sensitiveStart < pending.length()) {
        safe = redact(pending.substring(0, sensitiveStart)) + "[redacted]";
      } else {
        safe = redact(pending.toString());
      }
      pending.setLength(0);
      return safe;
    }

    private static int incompleteSensitiveStart(CharSequence value) {
      String text = value.toString();
      int start = text.length();
      for (Pattern pattern :
          new Pattern[] {INCOMPLETE_ASSIGNMENT, INCOMPLETE_BEARER, INCOMPLETE_JWT}) {
        var matcher = pattern.matcher(text);
        if (matcher.find()) start = Math.min(start, matcher.start());
      }
      return start;
    }

    private static int partialSensitivePrefixStart(CharSequence value) {
      String text = value.toString();
      int start = text.length();
      for (String prefix : SENSITIVE_PREFIXES) {
        int max = Math.min(prefix.length() - 1, text.length());
        for (int length = 1; length <= max; length++) {
          if (text.regionMatches(true, text.length() - length, prefix, 0, length)) {
            start = Math.min(start, text.length() - length);
          }
        }
      }
      var assignmentLabel = ASSIGNMENT_LABEL_AT_END.matcher(text);
      if (assignmentLabel.find()) start = Math.min(start, assignmentLabel.start());
      return start;
    }
  }
}
