/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist.supervisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Parsing and ordering of our own service files, the systemd replacement. */
class ServiceUnitTest {

  private static Path write(Path dir, String name, String content) throws IOException {
    Path file = dir.resolve(name);
    Files.writeString(file, content);
    return file;
  }

  @Test
  void parsesTheDirectivesTheUnitsUse(@TempDir Path dir) throws IOException {
    Path file =
        write(
            dir,
            "jahaziel-control-plane.service",
            """
            [Unit]
            Description=Jahaziel Control Plane
            After=network.target postgresql.service
            Wants=postgresql.service

            [Service]
            Type=simple
            User=jahaziel-control-plane
            Group=jahaziel-control-plane
            EnvironmentFile=/etc/jahaziel/control-plane.env
            ExecStart=/usr/bin/java -jar /opt/jahaziel/control-plane-exec.jar
            Restart=on-failure
            RestartSec=5
            LimitNOFILE=65536
            NoNewPrivileges=yes
            ProtectSystem=strict
            ProtectHome=yes
            PrivateTmp=yes
            ReadWritePaths=/var/log/jahaziel

            [Install]
            WantedBy=multi-user.target
            """);

    ServiceUnit unit = ServiceUnit.parse(file);

    assertEquals("jahaziel-control-plane", unit.name());
    assertEquals("Jahaziel Control Plane", unit.description());
    assertEquals(List.of("network.target", "postgresql.service"), unit.after());
    assertEquals(List.of("postgresql.service"), unit.wants());
    assertEquals("jahaziel-control-plane", unit.user());
    assertEquals(Path.of("/etc/jahaziel/control-plane.env"), unit.environmentFile());
    assertEquals(
        List.of("/usr/bin/java", "-jar", "/opt/jahaziel/control-plane-exec.jar"), unit.execStart());
    assertEquals(ServiceUnit.Restart.ON_FAILURE, unit.restart());
    assertEquals(5L, unit.restartSec());
    assertEquals(65536L, unit.limitNofile());
    assertTrue(unit.noNewPrivileges());
    assertTrue(unit.protectSystem());
    assertTrue(unit.protectHome());
    assertTrue(unit.privateTmp());
    assertEquals(List.of(Path.of("/var/log/jahaziel")), unit.readWritePaths());
    assertFalse(unit.isTimer());
  }

  @Test
  void parsesHooksAndTimerIntervals(@TempDir Path dir) throws IOException {
    Path service =
        write(
            dir,
            "jahaziel-code-server.service",
            """
            [Service]
            User=jahaziel-guest
            ExecStartPre=/usr/bin/python3 /usr/local/bin/jahaziel-sync pull
            ExecStart=/opt/jahaziel-code-server/bin/jahaziel-code-server --host 127.0.0.1
            ExecStop=/usr/bin/python3 /usr/local/bin/jahaziel-sync push
            Restart=always
            RestartSec=2s
            """);
    ServiceUnit unit = ServiceUnit.parse(service);

    assertEquals(
        List.of("/usr/bin/python3", "/usr/local/bin/jahaziel-sync", "pull"), unit.execStartPre());
    assertEquals(
        List.of("/usr/bin/python3", "/usr/local/bin/jahaziel-sync", "push"), unit.execStop());
    assertEquals(ServiceUnit.Restart.ALWAYS, unit.restart());
    assertEquals(2L, unit.restartSec());
    assertNull(unit.workingDirectory());

    Path timer =
        write(
            dir,
            "jahaziel-sync-push.service",
            """
            [Service]
            ExecStart=/usr/bin/python3 /usr/local/bin/jahaziel-sync push

            [Timer]
            OnUnitActiveSec=1m
            """);
    ServiceUnit interval = ServiceUnit.parse(timer);

    assertTrue(interval.isTimer());
    assertEquals(60L, interval.timerSeconds());
  }

  @Test
  void foldsCompanionTimerFilesIntoTheirService(@TempDir Path dir) throws IOException {
    write(
        dir,
        "jahaziel-sync-push.service",
        """
        [Service]
        ExecStart=/usr/bin/python3 /usr/local/bin/jahaziel-sync push
        """);
    write(
        dir,
        "jahaziel-sync-push.timer",
        """
        [Timer]
        OnUnitActiveSec=1m
        """);

    List<ServiceUnit> units = ServiceSupervisor.load(dir);

    assertEquals(1, units.size(), "the .timer file is a schedule, not a service");
    assertTrue(units.get(0).isTimer(), "the interval must reach the service it schedules");
    assertEquals(60L, units.get(0).timerSeconds());
  }

  @Test
  void ordersUnitsByTheirLocalAfterDependencies(@TempDir Path dir) throws IOException {
    ServiceUnit gateway =
        ServiceUnit.parse(
            write(
                dir,
                "jahaziel-gateway.service",
                """
                [Unit]
                After=network.target jahaziel-control-plane.service
                [Service]
                ExecStart=/usr/bin/java -jar /opt/jahaziel/gateway-exec.jar
                """));
    ServiceUnit controlPlane =
        ServiceUnit.parse(
            write(
                dir,
                "jahaziel-control-plane.service",
                """
                [Unit]
                After=network.target
                [Service]
                ExecStart=/usr/bin/java -jar /opt/jahaziel/control-plane-exec.jar
                """));
    ServiceUnit relay =
        ServiceUnit.parse(
            write(
                dir,
                "jahaziel-sync-relay.service",
                """
                [Service]
                User=root
                ExecStart=/usr/sbin/socat VSOCK-LISTEN:9002,fork TCP:127.0.0.1:19002
                """));

    List<String> order =
        ServiceSupervisor.requireOrdered(List.of(gateway, relay, controlPlane)).stream()
            .map(ServiceUnit::name)
            .toList();

    assertTrue(
        order.indexOf("jahaziel-control-plane") < order.indexOf("jahaziel-gateway"),
        "control-plane must start before gateway: " + order);
    assertEquals(3, order.size());
  }

  @Test
  void buildsTheSandboxPrefixFromTheHardeningDirectives(@TempDir Path dir) throws IOException {
    ServiceUnit unit =
        ServiceUnit.parse(
            write(
                dir,
                "jahaziel-node-agent.service",
                """
                [Service]
                User=jahaziel-node-agent
                Group=jahaziel-node-agent
                ExecStart=/usr/bin/java -jar /opt/jahaziel/node-agent-exec.jar
                LimitNOFILE=65536
                NoNewPrivileges=yes
                ProtectSystem=strict
                PrivateTmp=yes
                ReadWritePaths=/var/log/jahaziel /var/lib/jahaziel
                """));

    List<String> prefix = ServiceSupervisor.sandboxPrefix(unit);

    assertEquals("unshare", prefix.get(0));
    assertTrue(prefix.contains("setpriv"));
    assertTrue(prefix.contains("--no-new-privs"));
    assertTrue(prefix.contains("--reuid=jahaziel-node-agent"));
    assertTrue(prefix.contains("--regid=jahaziel-node-agent"));
    assertTrue(prefix.contains("--rlimit-nofile=65536"));
    String script = ServiceSupervisor.sandboxScript(unit);
    assertTrue(script.contains("remount,ro /"), script);
    assertTrue(script.contains("tmpfs /tmp"), script);
    assertTrue(script.contains("/var/lib/jahaziel"), script);
    assertTrue(script.endsWith("exec \"$@\""), script);
  }
}
