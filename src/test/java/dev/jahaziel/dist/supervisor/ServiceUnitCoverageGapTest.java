/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist.supervisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Closes the JaCoCo gaps in {@link ServiceUnit}. */
class ServiceUnitCoverageGapTest {
  private static Path write(Path dir, String name, String content) throws IOException {
    Path file = dir.resolve(name);
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }

  @Test
  void withTimerSecondsBranches(@TempDir Path dir) throws IOException {
    ServiceUnit unit =
        ServiceUnit.parse(write(dir, "a.service", "[Service]\nExecStart=/bin/true\n"));
    assertTrue(unit.withTimerSeconds(0) == unit);
    assertTrue(unit.withTimerSeconds(-5) == unit);
    assertTrue(unit.withTimerSeconds(60).timerSeconds() == 60);
    ServiceUnit timed = unit.withTimerSeconds(60);
    assertTrue(timed.withTimerSeconds(60) == timed);
    assertEquals(30L, timed.withTimerSeconds(30).timerSeconds());
  }

  @Test
  void timerFileUnreadable(@TempDir Path dir) {
    assertThrows(java.io.UncheckedIOException.class, () -> ServiceUnit.timerIntervalSeconds(dir));
  }

  @Test
  void timerIntervalBranches(@TempDir Path dir) throws IOException {
    Path active = write(dir, "a.timer", "[Timer]\nOnUnitActiveSec=2m\n");
    assertEquals(120L, ServiceUnit.timerIntervalSeconds(active));
    Path boot = write(dir, "b.timer", "[Timer]\nOnBootSec=30\n");
    assertEquals(30L, ServiceUnit.timerIntervalSeconds(boot));
    Path blankActive = write(dir, "c.timer", "[Timer]\nOnUnitActiveSec=   \nOnBootSec=45\n");
    assertEquals(45L, ServiceUnit.timerIntervalSeconds(blankActive));
    Path none = write(dir, "d.timer", "[Timer]\nWantedBy=x\n");
    assertEquals(0L, ServiceUnit.timerIntervalSeconds(none));
  }

  @Test
  void parseWithoutSuffixAndUnreadable(@TempDir Path dir) throws IOException {
    Path file = write(dir, "custom", "[Service]\nExecStart=/bin/true\n");
    assertEquals("custom", ServiceUnit.parse(file).name());
    assertThrows(NullPointerException.class, () -> ServiceUnit.parse(null));
    assertThrows(java.io.UncheckedIOException.class, () -> ServiceUnit.parse(dir));
  }

  @Test
  void parseMissingExecStartThrows(@TempDir Path dir) throws IOException {
    Path file = write(dir, "empty.service", "[Service]\nUser=root\n");
    assertThrows(IllegalArgumentException.class, () -> ServiceUnit.parse(file));
  }

  @Test
  void quotedValues(@TempDir Path dir) throws IOException {
    ServiceUnit unit =
        ServiceUnit.parse(
            write(
                dir,
                "q.service",
                "[Unit]\nDescription=\"My Service\"\n"
                    + "[Service]\nExecStart=/bin/echo hi\nWorkingDirectory='/tmp/x'\n"));
    assertEquals("My Service", unit.description());
    assertEquals(Path.of("/tmp/x"), unit.workingDirectory());
    ServiceUnit single =
        ServiceUnit.parse(
            write(
                dir,
                "s.service",
                "[Unit]\nDescription='Single'\n[Service]\nExecStart=/bin/true\n"));
    assertEquals("Single", single.description());
  }

  @Test
  void dashPathsAndBlankCommands(@TempDir Path dir) throws IOException {
    ServiceUnit dashed =
        ServiceUnit.parse(
            write(
                dir,
                "d.service",
                "[Service]\nExecStart=/bin/true\nReadWritePaths=-/var/log/jahaziel /var/lib/x\n"));
    assertEquals(
        List.of(Path.of("/var/log/jahaziel"), Path.of("/var/lib/x")), dashed.readWritePaths());
    ServiceUnit blankPre =
        ServiceUnit.parse(
            write(
                dir, "b.service", "[Service]\nExecStart=/bin/true\nExecStartPre=   \nExecStop=\n"));
    assertTrue(blankPre.execStartPre().isEmpty());
    assertTrue(blankPre.execStop().isEmpty());
    ServiceUnit blankUser =
        ServiceUnit.parse(
            write(dir, "u.service", "[Service]\nExecStart=/bin/true\nUser=   \nGroup=\n"));
    assertTrue(blankUser.user() == null);
    assertTrue(blankUser.group() == null);
  }

  @Test
  void truthyAndStrict(@TempDir Path dir) throws IOException {
    ServiceUnit one =
        ServiceUnit.parse(
            write(
                dir,
                "o.service",
                "[Service]\nExecStart=/bin/true\nNoNewPrivileges=1\nProtectHome=1\nPrivateTmp=yes\n"));
    assertTrue(one.noNewPrivileges());
    assertTrue(one.protectHome());
    ServiceUnit strictYes =
        ServiceUnit.parse(
            write(dir, "s.service", "[Service]\nExecStart=/bin/true\nProtectSystem=yes\n"));
    assertTrue(strictYes.protectSystem());
    ServiceUnit strictNo =
        ServiceUnit.parse(
            write(
                dir,
                "n.service",
                "[Service]\nExecStart=/bin/true\nProtectSystem=no\nNoNewPrivileges=no\n"));
    assertFalse(strictNo.protectSystem());
    assertFalse(strictNo.noNewPrivileges());
    ServiceUnit plain =
        ServiceUnit.parse(write(dir, "p.service", "[Service]\nExecStart=/bin/true\n"));
    assertFalse(plain.noNewPrivileges());
    assertFalse(plain.protectSystem());
  }

  @Test
  void restartVariants(@TempDir Path dir) throws IOException {
    assertEquals(
        ServiceUnit.Restart.NO,
        ServiceUnit.parse(write(dir, "n.service", "[Service]\nExecStart=/bin/true\n")).restart());
    assertEquals(
        ServiceUnit.Restart.ALWAYS,
        ServiceUnit.parse(
                write(dir, "a.service", "[Service]\nExecStart=/bin/true\nRestart=always\n"))
            .restart());
    assertEquals(
        ServiceUnit.Restart.ON_FAILURE,
        ServiceUnit.parse(
                write(dir, "f.service", "[Service]\nExecStart=/bin/true\nRestart=on-failure\n"))
            .restart());
    assertEquals(
        ServiceUnit.Restart.ON_FAILURE,
        ServiceUnit.parse(
                write(dir, "ab.service", "[Service]\nExecStart=/bin/true\nRestart=on-abnormal\n"))
            .restart());
    assertEquals(
        ServiceUnit.Restart.NO,
        ServiceUnit.parse(
                write(dir, "w.service", "[Service]\nExecStart=/bin/true\nRestart=weird\n"))
            .restart());
  }

  @Test
  void parseLongAndSeconds(@TempDir Path dir) throws IOException {
    assertEquals(
        0L,
        ServiceUnit.parse(write(dir, "e.service", "[Service]\nExecStart=/bin/true\n"))
            .limitNofile());
    assertEquals(
        0L,
        ServiceUnit.parse(
                write(dir, "b.service", "[Service]\nExecStart=/bin/true\nLimitNOFILE=   \n"))
            .limitNofile());
    assertEquals(
        0L,
        ServiceUnit.parse(
                write(dir, "x.service", "[Service]\nExecStart=/bin/true\nLimitNOFILE=abc\n"))
            .limitNofile());
    assertEquals(
        5L,
        ServiceUnit.parse(write(dir, "r.service", "[Service]\nExecStart=/bin/true\nRestartSec=5\n"))
            .restartSec());
    assertEquals(
        7200L,
        ServiceUnit.parse(
                write(dir, "h.service", "[Service]\nExecStart=/bin/true\nRestartSec=2h\n"))
            .restartSec());
    assertEquals(
        86400L,
        ServiceUnit.parse(
                write(dir, "dd.service", "[Service]\nExecStart=/bin/true\nRestartSec=1d\n"))
            .restartSec());
    assertEquals(
        10L,
        ServiceUnit.parse(
                write(dir, "t.service", "[Service]\nExecStart=/bin/true\nRestartSec=10\n"))
            .restartSec());
    assertEquals(
        0L,
        ServiceUnit.parse(
                write(dir, "bog.service", "[Service]\nExecStart=/bin/true\nRestartSec=bogus!\n"))
            .restartSec());
    assertEquals(
        0L,
        ServiceUnit.parse(
                write(dir, "bl.service", "[Service]\nExecStart=/bin/true\nRestartSec=   \n"))
            .restartSec());
  }

  @Test
  void readValuesEdges(@TempDir Path dir) throws IOException {
    ServiceUnit unit =
        ServiceUnit.parse(
            write(
                dir,
                "m.service",
                "# comment\n; semi\n[Unit]\nDescription=first\nDescription=second\n"
                    + "NOEQUALS\nAfter=net.service\nWants=\n"
                    + "[Service]\nExecStart=/bin/true\n"));
    assertEquals("first", unit.description());
    assertEquals(List.of("net.service"), unit.after());
    assertTrue(unit.wants().isEmpty());
    assertTrue(unit.toString().contains("m ("));
  }

  @Test
  void splitListBlank(@TempDir Path dir) throws IOException {
    ServiceUnit unit =
        ServiceUnit.parse(
            write(
                dir,
                "bl.service",
                "[Unit]\nAfter=   \nWants=   \n[Service]\nExecStart=/bin/true\n"));
    assertTrue(unit.after().isEmpty());
    assertTrue(unit.wants().isEmpty());
  }
}
