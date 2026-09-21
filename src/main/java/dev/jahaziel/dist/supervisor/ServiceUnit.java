/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist.supervisor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One supervised process, parsed from our own service file format.
 *
 * <p>The format is the systemd-INI subset the platform actually used, so the existing unit bodies
 * keep working unchanged: {@code [Unit]} ({@code Description}, {@code After}, {@code Wants}),
 * {@code [Service]} ({@code Type}, {@code User}, {@code Group}, {@code EnvironmentFile}, {@code
 * ExecStart}, {@code ExecStartPre}, {@code ExecStop}, {@code Restart}, {@code RestartSec}, {@code
 * LimitNOFILE}, {@code WorkingDirectory}, {@code NoNewPrivileges}, {@code ProtectSystem}, {@code
 * ProtectHome}, {@code PrivateTmp}, {@code ReadWritePaths}), {@code [Timer]} ({@code
 * OnUnitActiveSec}, {@code OnBootSec}) and {@code [Install]} ({@code WantedBy}).
 */
public final class ServiceUnit {

  /** Restart policy. */
  public enum Restart {
    /** Never restart. */
    NO,
    /** Restart only on abnormal exit. */
    ON_FAILURE,
    /** Restart on every exit. */
    ALWAYS
  }

  private static final Pattern NAMED_SECONDS = Pattern.compile("(\\d+)\\s*([smhd]?)");
  private static final Map<String, Long> UNIT_MULTIPLIER =
      Map.of("", 1L, "s", 1L, "m", 60L, "h", 3600L, "d", 86400L);

  private final String name;
  private final String description;
  private final List<String> after;
  private final List<String> wants;
  private final String user;
  private final String group;
  private final Path environmentFile;
  private final List<String> execStart;
  private final List<String> execStartPre;
  private final List<String> execStop;
  private final Restart restart;
  private final long restartSec;
  private final long limitNofile;
  private final Path workingDirectory;
  private final boolean noNewPrivileges;
  private final boolean protectSystem;
  private final boolean protectHome;
  private final boolean privateTmp;
  private final List<Path> readWritePaths;
  private final long timerSeconds;

  private ServiceUnit(Builder builder) {
    this.name = builder.name;
    this.description = builder.description;
    this.after = List.copyOf(builder.after);
    this.wants = List.copyOf(builder.wants);
    this.user = builder.user;
    this.group = builder.group;
    this.environmentFile = builder.environmentFile;
    this.execStart = List.copyOf(builder.execStart);
    this.execStartPre = List.copyOf(builder.execStartPre);
    this.execStop = List.copyOf(builder.execStop);
    this.restart = builder.restart;
    this.restartSec = builder.restartSec;
    this.limitNofile = builder.limitNofile;
    this.workingDirectory = builder.workingDirectory;
    this.noNewPrivileges = builder.noNewPrivileges;
    this.protectSystem = builder.protectSystem;
    this.protectHome = builder.protectHome;
    this.privateTmp = builder.privateTmp;
    this.readWritePaths = List.copyOf(builder.readWritePaths);
    this.timerSeconds = builder.timerSeconds;
  }

  private ServiceUnit(ServiceUnit other, long timerSeconds) {
    this.name = other.name;
    this.description = other.description;
    this.after = other.after;
    this.wants = other.wants;
    this.user = other.user;
    this.group = other.group;
    this.environmentFile = other.environmentFile;
    this.execStart = other.execStart;
    this.execStartPre = other.execStartPre;
    this.execStop = other.execStop;
    this.restart = other.restart;
    this.restartSec = other.restartSec;
    this.limitNofile = other.limitNofile;
    this.workingDirectory = other.workingDirectory;
    this.noNewPrivileges = other.noNewPrivileges;
    this.protectSystem = other.protectSystem;
    this.protectHome = other.protectHome;
    this.privateTmp = other.privateTmp;
    this.readWritePaths = other.readWritePaths;
    this.timerSeconds = timerSeconds;
  }

  /** Service name, taken from the file name without the {@code .service} suffix. */
  public String name() {
    return name;
  }

  /** Human readable description. */
  public String description() {
    return description;
  }

  /** Units that must be started before this one. */
  public List<String> after() {
    return after;
  }

  /** Units that should be running with this one when they exist locally. */
  public List<String> wants() {
    return wants;
  }

  /** Account to drop to, or {@code null} to keep the current one. */
  public String user() {
    return user;
  }

  /** Group to drop to, or {@code null}. */
  public String group() {
    return group;
  }

  /** Environment file sourced before launch, or {@code null}. */
  public Path environmentFile() {
    return environmentFile;
  }

  /** Command line of the long running process. */
  public List<String> execStart() {
    return execStart;
  }

  /** Commands run once before {@link #execStart()}. */
  public List<String> execStartPre() {
    return execStartPre;
  }

  /** Commands run once after the process exits. */
  public List<String> execStop() {
    return execStop;
  }

  /** Restart policy. */
  public Restart restart() {
    return restart;
  }

  /** Delay before a restart, in seconds. */
  public long restartSec() {
    return restartSec;
  }

  /** {@code LimitNOFILE} value, or {@code 0} when unset. */
  public long limitNofile() {
    return limitNofile;
  }

  /** Working directory, or {@code null}. */
  public Path workingDirectory() {
    return workingDirectory;
  }

  /** Whether to set {@code PR_SET_NO_NEW_PRIVS} on the child. */
  public boolean noNewPrivileges() {
    return noNewPrivileges;
  }

  /** Whether {@code ProtectSystem=strict} was requested. */
  public boolean protectSystem() {
    return protectSystem;
  }

  /** Whether {@code ProtectHome} was requested. */
  public boolean protectHome() {
    return protectHome;
  }

  /** Whether {@code PrivateTmp} was requested. */
  public boolean privateTmp() {
    return privateTmp;
  }

  /** Paths that stay writable inside the sandbox. */
  public List<Path> readWritePaths() {
    return readWritePaths;
  }

  /** Period between runs for timer backed services, or {@code 0} for a plain service. */
  public long timerSeconds() {
    return timerSeconds;
  }

  /** Whether this unit runs on an interval. */
  public boolean isTimer() {
    return timerSeconds > 0;
  }

  /**
   * Returns this unit scheduled on an interval, as declared by its companion {@code .timer} file.
   *
   * @param seconds interval between runs, ignored when zero
   * @return the scheduled unit
   */
  public ServiceUnit withTimerSeconds(long seconds) {
    return seconds <= 0 || seconds == timerSeconds ? this : new ServiceUnit(this, seconds);
  }

  /**
   * Reads the interval out of a companion {@code .timer} file.
   *
   * @param timerFile the {@code .timer} file
   * @return the interval in seconds, or {@code 0} when the file declares none
   */
  public static long timerIntervalSeconds(Path timerFile) {
    Map<String, String> values;
    try {
      values = readValues(Files.readString(timerFile, StandardCharsets.UTF_8));
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot read timer file " + timerFile, failure);
    }
    return seconds(timerInterval(values));
  }

  /**
   * Parses a service file.
   *
   * @param file path to the {@code .service} file
   * @return the parsed unit
   */
  public static ServiceUnit parse(Path file) {
    Objects.requireNonNull(file, "file");
    String fileName = file.getFileName().toString();
    String name =
        fileName.endsWith(".service")
            ? fileName.substring(0, fileName.length() - ".service".length())
            : fileName;
    Map<String, String> values;
    try {
      values = readValues(Files.readString(file, StandardCharsets.UTF_8));
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot read service file " + file, failure);
    }
    Builder builder = new Builder(name);
    builder.description = values.getOrDefault("Description", name);
    builder.after = splitList(values.get("After"));
    builder.wants = splitList(values.get("Wants"));
    builder.user = blankToNull(values.get("User"));
    builder.group = blankToNull(values.get("Group"));
    builder.environmentFile = pathOrNull(values.get("EnvironmentFile"));
    builder.execStart = splitCommand(values.get("ExecStart"));
    builder.execStartPre = splitCommand(values.get("ExecStartPre"));
    builder.execStop = splitCommand(values.get("ExecStop"));
    builder.restart = restart(values.get("Restart"));
    builder.restartSec = seconds(values.getOrDefault("RestartSec", "0"));
    builder.limitNofile = parseLong(values.get("LimitNOFILE"));
    builder.workingDirectory = pathOrNull(values.get("WorkingDirectory"));
    builder.noNewPrivileges = truthy(values.get("NoNewPrivileges"));
    builder.protectSystem = strictFlag(values.get("ProtectSystem"));
    builder.protectHome = truthy(values.get("ProtectHome"));
    builder.privateTmp = truthy(values.get("PrivateTmp"));
    builder.readWritePaths = splitPaths(values.get("ReadWritePaths"));
    builder.timerSeconds = seconds(timerInterval(values));
    return builder.build();
  }

  private static String timerInterval(Map<String, String> values) {
    String active = values.get("OnUnitActiveSec");
    if (active != null && !active.isBlank()) {
      return active;
    }
    return values.getOrDefault("OnBootSec", "0");
  }

  private static Map<String, String> readValues(String content) {
    Map<String, String> values = new LinkedHashMap<>();
    for (String rawLine : content.split("\r?\n")) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("#") || line.startsWith(";") || line.startsWith("[")) {
        continue;
      }
      int equals = line.indexOf('=');
      if (equals > 0) {
        String key = line.substring(0, equals).trim();
        String value = line.substring(equals + 1).trim();
        values.putIfAbsent(key, unquote(value));
      }
    }
    return values;
  }

  private static String unquote(String value) {
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static List<String> splitList(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return List.of(value.trim().split("\\s+"));
  }

  private static List<Path> splitPaths(String value) {
    List<Path> paths = new ArrayList<>();
    for (String part : splitList(value)) {
      if (part.startsWith("-")) {
        part = part.substring(1);
      }
      paths.add(Path.of(part));
    }
    return paths;
  }

  private static List<String> splitCommand(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return List.of(value.trim().split("\\s+"));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static Path pathOrNull(String value) {
    String trimmed = blankToNull(value);
    return trimmed == null ? null : Path.of(trimmed);
  }

  private static boolean truthy(String value) {
    return value != null && (value.trim().equalsIgnoreCase("yes") || value.trim().equals("1"));
  }

  private static boolean strictFlag(String value) {
    if (value == null) {
      return false;
    }
    String trimmed = value.trim();
    return trimmed.equalsIgnoreCase("strict") || trimmed.equalsIgnoreCase("yes");
  }

  private static Restart restart(String value) {
    if (value == null) {
      return Restart.NO;
    }
    return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "always" -> Restart.ALWAYS;
      case "on-failure", "on-abnormal" -> Restart.ON_FAILURE;
      default -> Restart.NO;
    };
  }

  private static long parseLong(String value) {
    if (value == null || value.isBlank()) {
      return 0L;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException notANumber) {
      return 0L;
    }
  }

  private static long seconds(String value) {
    if (value == null || value.isBlank()) {
      return 0L;
    }
    Matcher matcher = NAMED_SECONDS.matcher(value.trim());
    if (!matcher.matches()) {
      return parseLong(value);
    }
    long amount = Long.parseLong(matcher.group(1));
    long multiplier = UNIT_MULTIPLIER.getOrDefault(matcher.group(2), 1L);
    return amount * multiplier;
  }

  @Override
  public String toString() {
    return name + " (" + String.join(" ", execStart) + ")";
  }

  private static final class Builder {
    private final String name;
    private String description = "";
    private List<String> after = List.of();
    private List<String> wants = List.of();
    private String user;
    private String group;
    private Path environmentFile;
    private List<String> execStart = List.of();
    private List<String> execStartPre = List.of();
    private List<String> execStop = List.of();
    private Restart restart = Restart.NO;
    private long restartSec;
    private long limitNofile;
    private Path workingDirectory;
    private boolean noNewPrivileges;
    private boolean protectSystem;
    private boolean protectHome;
    private boolean privateTmp;
    private List<Path> readWritePaths = List.of();
    private long timerSeconds;

    private Builder(String name) {
      this.name = name;
    }

    private ServiceUnit build() {
      if (execStart.isEmpty()) {
        throw new IllegalArgumentException("service " + name + " has no ExecStart");
      }
      return new ServiceUnit(this);
    }
  }
}
