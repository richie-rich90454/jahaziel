/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist.supervisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
                        "[Unit]\n"
                                + "Description=Jahaziel Control Plane\n"
                                + "After=network.target postgresql.service\n"
                                + "Wants=postgresql.service\n"
                                + "\n"
                                + "[Service]\n"
                                + "Type=simple\n"
                                + "User=jahaziel-control-plane\n"
                                + "Group=jahaziel-control-plane\n"
                                + "EnvironmentFile=/etc/jahaziel/control-plane.env\n"
                                + "ExecStart=/usr/bin/java -jar /opt/jahaziel/control-plane-exec.jar\n"
                                + "Restart=on-failure\n"
                                + "RestartSec=5\n"
                                + "LimitNOFILE=65536\n"
                                + "NoNewPrivileges=yes\n"
                                + "ProtectSystem=strict\n"
                                + "ProtectHome=yes\n"
                                + "PrivateTmp=yes\n"
                                + "ReadWritePaths=/var/log/jahaziel\n"
                                + "\n"
                                + "[Install]\n"
                                + "WantedBy=multi-user.target\n");

        ServiceUnit unit = ServiceUnit.parse(file);

        assertEquals("jahaziel-control-plane", unit.name());
        assertEquals("Jahaziel Control Plane", unit.description());
        assertEquals(List.of("network.target", "postgresql.service"), unit.after());
        assertEquals(List.of("postgresql.service"), unit.wants());
        assertEquals("jahaziel-control-plane", unit.user());
        assertEquals(Path.of("/etc/jahaziel/control-plane.env"), unit.environmentFile());
        assertEquals(
                List.of("/usr/bin/java", "-jar", "/opt/jahaziel/control-plane-exec.jar"),
                unit.execStart());
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
                        "jahaziel-sync-push.service",
                        "[Service]\n"
                                + "User=jahaziel-guest\n"
                                + "ExecStartPre=/usr/bin/python3 /usr/local/bin/jahaziel-sync pull\n"
                                + "ExecStart=/usr/bin/python3 /usr/local/bin/jahaziel-sync push\n"
                                + "ExecStop=/usr/bin/python3 /usr/local/bin/jahaziel-sync push\n"
                                + "Restart=always\n"
                                + "RestartSec=2s\n");
        ServiceUnit unit = ServiceUnit.parse(service);

        assertEquals(
                List.of("/usr/bin/python3", "/usr/local/bin/jahaziel-sync", "pull"),
                unit.execStartPre());
        assertEquals(
                List.of("/usr/bin/python3", "/usr/local/bin/jahaziel-sync", "push"),
                unit.execStop());
        assertEquals(ServiceUnit.Restart.ALWAYS, unit.restart());
        assertEquals(2L, unit.restartSec());
        assertNull(unit.workingDirectory());

        Path timer =
                write(
                        dir,
                        "jahaziel-sync-push.service",
                        "[Service]\n"
                                + "ExecStart=/usr/bin/python3 /usr/local/bin/jahaziel-sync push\n"
                                + "\n"
                                + "[Timer]\n"
                                + "OnUnitActiveSec=1m\n");
        ServiceUnit interval = ServiceUnit.parse(timer);

        assertTrue(interval.isTimer());
        assertEquals(60L, interval.timerSeconds());
    }

    @Test
    void foldsCompanionTimerFilesIntoTheirService(@TempDir Path dir) throws IOException {
        write(
                dir,
                "jahaziel-sync-push.service",
                "[Service]\n" + "ExecStart=/usr/bin/python3 /usr/local/bin/jahaziel-sync push\n");
        write(dir, "jahaziel-sync-push.timer", "[Timer]\n" + "OnUnitActiveSec=1m\n");

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
                                "[Unit]\n"
                                        + "After=network.target jahaziel-control-plane.service\n"
                                        + "[Service]\n"
                                        + "ExecStart=/usr/bin/java -jar /opt/jahaziel/gateway-exec.jar\n"));
        ServiceUnit controlPlane =
                ServiceUnit.parse(
                        write(
                                dir,
                                "jahaziel-control-plane.service",
                                "[Unit]\n"
                                        + "After=network.target\n"
                                        + "[Service]\n"
                                        + "ExecStart=/usr/bin/java -jar /opt/jahaziel/control-plane-exec.jar\n"));
        ServiceUnit relay =
                ServiceUnit.parse(
                        write(
                                dir,
                                "jahaziel-sync-relay.service",
                                "[Service]\n"
                                        + "User=root\n"
                                        + "ExecStart=/usr/sbin/socat VSOCK-LISTEN:9002,fork TCP:127.0.0.1:19002\n"));

        List<String> order = new ArrayList<>();
        for (ServiceUnit unit :
                ServiceSupervisor.requireOrdered(List.of(gateway, relay, controlPlane))) {
            order.add(unit.name());
        }

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
                                "[Service]\n"
                                        + "User=jahaziel-node-agent\n"
                                        + "Group=jahaziel-node-agent\n"
                                        + "ExecStart=/usr/bin/java -jar /opt/jahaziel/node-agent-exec.jar\n"
                                        + "LimitNOFILE=65536\n"
                                        + "NoNewPrivileges=yes\n"
                                        + "ProtectSystem=strict\n"
                                        + "PrivateTmp=yes\n"
                                        + "ReadWritePaths=/var/log/jahaziel /var/lib/jahaziel\n"));

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
