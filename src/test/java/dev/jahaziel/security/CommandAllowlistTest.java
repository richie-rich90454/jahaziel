/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;

class CommandAllowlistTest {

    @Test
    void configuredCommandsAreAllowed() {
        CommandAllowlist list = new CommandAllowlist();
        list.allow("git", "/opt/tools/git");

        assertTrue(list.allows("git"));
        assertTrue(list.allows("GIT"));
    }

    @Test
    void rejectsPathsAndUnknownCommands() {
        CommandAllowlist list = new CommandAllowlist();
        list.allow("git", "/opt/tools/git");

        assertFalse(list.allows("/opt/tools/git"));
        assertFalse(list.allows("C:\\Windows\\System32\\cmd.exe"));
        assertFalse(list.allows("rm"));
    }

    @Test
    void argvNeverUsesBrowserExecutable() {
        CommandAllowlist list = new CommandAllowlist();
        list.allow("git", "/opt/tools/git");
        ArrayList<String> args = new ArrayList<String>();
        args.add("status");

        ArrayList<String> argv = list.argv("git", args);

        assertEquals("/opt/tools/git", argv.get(0));
        assertEquals("status", argv.get(1));
    }

    @Test
    void unknownCommandThrows() {
        final CommandAllowlist list = new CommandAllowlist();
        assertThrows(
                SecurityException.class,
                new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        list.argv("rm", new ArrayList<String>());
                    }
                });
    }

    @Test
    void debugAdaptersUseSeparateAllowlist() {
        CommandAllowlist list = new CommandAllowlist();
        list.allow("node", "/opt/tools/node");
        list.allowDebugAdapter("node");

        assertTrue(list.allowsDebugAdapter("node"));
        assertFalse(list.allowsDebugAdapter("python"));
        assertFalse(list.allows("python"));
    }
}
