/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.platform.Platform;
import dev.jahaziel.plugin.PluginDescriptor;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class ProcessEnvironmentTest {
    @Test
    void linuxUsesBashSetsidPosixPathAndProc() {
        ProcessEnvironment environment = new LinuxProcessEnvironment("/bin/bash", "/usr/bin/setsid");

        assertEquals("process-env-linux", environment.descriptor().name());
        assertTrue(environment.isSupported());
        assertEquals(
                Arrays.asList("/bin/bash", "-i"),
                environment.shellCommand(new ArrayList<String>()));
        assertEquals(
                Arrays.asList("/usr/bin/setsid", "--", "node", "server.js"),
                environment.sessionWrapper(Arrays.asList("node", "server.js")));
        assertEquals(Paths.get("/proc"), environment.processRoot());

        Map<String, String> expected = new LinkedHashMap<String, String>();
        expected.put("HOME", path("/ws/home"));
        expected.put("PATH", "/usr/local/bin:/usr/bin:/bin");
        expected.put("TERM", "xterm-256color");
        expected.put("LANG", "C.UTF-8");
        assertEquals(
                expected,
                environment.environment(
                        Paths.get("/ws/home"), Collections.<String, String>emptyMap()));
    }

    @Test
    void linuxTerminalEnvironmentWithToolsIsUnchanged() {
        ProcessEnvironment environment = new LinuxProcessEnvironment("/bin/bash", "/usr/bin/setsid");
        Map<String, String> extra =
                environment.toolEnvironment(Paths.get("/tools/bin"), Paths.get("/profile"));

        Map<String, String> expected = new LinkedHashMap<String, String>();
        expected.put("HOME", path("/ws/home"));
        expected.put(
                "PATH",
                path("/tools/bin") + File.pathSeparator + "/usr/local/bin:/usr/bin:/bin");
        expected.put("XDG_CONFIG_HOME", Paths.get("/profile/config").toString());
        expected.put("XDG_DATA_HOME", Paths.get("/profile/data").toString());
        expected.put("XDG_CACHE_HOME", Paths.get("/profile/cache").toString());
        expected.put("TERM", "xterm-256color");
        expected.put("LANG", "C.UTF-8");
        assertEquals(
                expected, environment.environment(Paths.get("/ws/home"), extra));
    }

    @Test
    void macUsesPosixShellAndPathWithoutSetsidOrProc() {
        ProcessEnvironment environment = new MacProcessEnvironment();

        assertEquals("process-env-mac", environment.descriptor().name());
        assertTrue(environment.isSupported());
        assertEquals(
                Arrays.asList("/bin/bash", "-i"),
                environment.shellCommand(new ArrayList<String>()));
        assertEquals(
                Arrays.asList("node", "server.js"),
                environment.sessionWrapper(Arrays.asList("node", "server.js")));
        assertNull(environment.processRoot());

        Map<String, String> expected = new LinkedHashMap<String, String>();
        expected.put("HOME", path("/ws/home"));
        expected.put("PATH", "/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin");
        expected.put("TERM", "xterm-256color");
        expected.put("LANG", "C.UTF-8");
        assertEquals(
                expected,
                environment.environment(
                        Paths.get("/ws/home"), Collections.<String, String>emptyMap()));
    }

    @Test
    void windowsUsesConPtyShellsAndWindowsOnlyVariables() {
        ProcessEnvironment environment = new WindowsProcessEnvironment();

        assertEquals("process-env-windows", environment.descriptor().name());
        assertTrue(environment.isSupported());
        assertEquals(
                Arrays.asList("powershell.exe", "-NoLogo", "-NoExit"),
                environment.shellCommand(new ArrayList<String>()));
        assertEquals(
                Arrays.asList("cmd.exe", "/C", "echo", "hi"),
                environment.shellCommand(Arrays.asList("echo", "hi")));
        assertEquals(
                Arrays.asList("node", "server.js"),
                environment.sessionWrapper(Arrays.asList("node", "server.js")));
        assertNull(environment.processRoot());

        Map<String, String> actual =
                environment.environment(
                        Paths.get("C:\\ws\\home"), Collections.<String, String>emptyMap());
        assertEquals(Paths.get("C:\\ws\\home").toString(), actual.get("USERPROFILE"));
        assertEquals("C:\\Windows\\System32;C:\\Windows;C:\\Windows\\System32\\Wbem", actual.get("Path"));
        assertEquals("C:\\Windows\\System32\\cmd.exe", actual.get("ComSpec"));
        assertEquals("C:\\Windows", actual.get("SystemRoot"));
        assertFalse(actual.containsKey("HOME"));
        assertFalse(actual.containsKey("PATH"));
        assertFalse(actual.containsKey("TERM"));
        assertFalse(actual.containsKey("LANG"));
        assertFalse(actual.containsKey("XDG_CONFIG_HOME"));
    }

    @Test
    void selectorReturnsTheStrategyForEachDetectedOs() {
        assertEquals(
                "process-env-linux",
                ProcessEnvironments.forOs(Platform.Os.LINUX).descriptor().name());
        assertEquals(
                "process-env-mac",
                ProcessEnvironments.forOs(Platform.Os.MACOS).descriptor().name());
        assertEquals(
                "process-env-windows",
                ProcessEnvironments.forOs(Platform.Os.WINDOWS).descriptor().name());
    }

    @Test
    void selectorFailsClosedForAnUnknownOs() {
        assertThrows(
                IllegalStateException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        ProcessEnvironments.forOs(null);
                    }
                });
    }

    @Test
    void concreteHelpersWorkForAFakeUnsupportedStrategy() {
        ProcessEnvironment fake = new FakeProcessEnvironment(false);

        assertFalse(fake.isSupported());
        assertEquals(
                Arrays.asList("/fake/sh", "-i", "-c"),
                fake.shellCommand(Arrays.asList("-c")));
        assertEquals(
                Arrays.asList("node"), fake.sessionWrapper(Arrays.asList("node")));
        assertNull(fake.processRoot());

        Map<String, String> expected = new LinkedHashMap<String, String>();
        expected.put("HOME", path("/ws/home"));
        expected.put("PATH", "/fake/bin");
        expected.put("TERM", "xterm-256color");
        expected.put("LANG", "C.UTF-8");
        assertEquals(
                expected,
                fake.environment(Paths.get("/ws/home"), Collections.<String, String>emptyMap()));
    }

    @Test
    void extraValuesOverrideHostDefaults() {
        ProcessEnvironment environment = new MacProcessEnvironment();
        Map<String, String> extra = new LinkedHashMap<String, String>();
        extra.put("PATH", "/custom/bin");

        assertEquals(
                "/custom/bin",
                environment
                        .environment(Paths.get("/ws/home"), extra)
                        .get("PATH"));
    }

    private static String path(String value) {
        return Paths.get(value).toString();
    }

    private static final class FakeProcessEnvironment extends ProcessEnvironment {
        private final boolean supported;

        private FakeProcessEnvironment(boolean supported) {
            super(
                    new PluginDescriptor(
                            "fake-process-env",
                            "1.0",
                            new ArrayList<String>(),
                            new ArrayList<String>()));
            this.supported = supported;
        }

        @Override
        public boolean isSupported() {
            return supported;
        }

        @Override
        public String pathValue() {
            return "/fake/bin";
        }

        @Override
        protected List<String> shellPrefix() {
            return Arrays.asList("/fake/sh", "-i");
        }
    }
}
