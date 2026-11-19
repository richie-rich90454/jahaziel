/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.cli.CliTestSupport.Output;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.function.Executable;

/** Version channels and locally generated shell completion scripts. */
class ChannelsCompletionTest {
    @TempDir private Path jar;

    private String userDir;

    @BeforeEach
    void pinUserDir() {
        userDir = System.getProperty("user.dir");
        System.setProperty("user.dir", jar.toString());
    }

    @AfterEach
    void restoreUserDir() {
        System.setProperty("user.dir", userDir);
        Cli.setOffline(false);
    }

    private static Map<String, String> env(String key, String value) {
        Map<String, String> values = new HashMap<String, String>();
        values.put(key, value);
        return values;
    }

    @Test
    void defaultChannelIsPinnedStable() {
        assertEquals(Channels.STABLE, Channels.resolve(new HashMap<String, String>()));
        assertEquals(Channels.STABLE, Channels.resolve(null));
        assertTrue(Channels.isPinned(Channels.resolve(new HashMap<String, String>())));
        assertTrue(
                Doctor.channel(new HashMap<String, String>())
                        .detail()
                        .contains("update channel=stable (pinned)"));
    }

    @Test
    void channelFromEnvironment() {
        Map<String, String> latest = env(Channels.ENV_KEY, "LATEST");
        assertEquals(Channels.LATEST, Channels.resolve(latest));
        assertFalse(Channels.isPinned(Channels.resolve(latest)));
        assertTrue(Doctor.channel(latest).detail().contains("(tracks latest)"));
        assertEquals(Channels.STABLE, Channels.resolve(env(Channels.ENV_KEY, "nonsense")));
        assertEquals(Channels.LATEST, Channels.resolve(env(Channels.ENV_KEY, " latest ")));
    }

    @Test
    void completionTreeHasAllCommands() {
        Completion.Node tree = Completion.tree();
        assertEquals("jahaziel", tree.name());
        assertEquals(10, tree.children().size());
        assertTrue(tree.flags().contains("--offline"));
    }

    @Test
    void bashCompletionGoesToStdout() {
        Output output = CliTestSupport.dispatch("completion", "bash");
        assertEquals(0, output.code());
        assertTrue(output.out().contains("complete -F _jahaziel_complete jahaziel"));
        assertTrue(output.out().contains("update"));
        assertTrue(output.out().contains("ops"));
        assertTrue(output.out().contains("delete-user"));
        assertTrue(output.err().isEmpty());
    }

    @Test
    void zshAndPowershellCompletion() {
        Output zsh = CliTestSupport.dispatch("completion", "zsh");
        assertEquals(0, zsh.code());
        assertTrue(zsh.out().contains("#compdef jahaziel"));
        assertTrue(zsh.out().contains("words=("));
        Output ps = CliTestSupport.dispatch("completion", "powershell");
        assertEquals(0, ps.code());
        assertTrue(ps.out().contains("Register-ArgumentCompleter"));
        assertTrue(ps.out().contains("-CommandName jahaziel"));
        assertEquals(Completion.generate("BASH"), Completion.generate("bash"));
    }

    @Test
    void completionRejectsUnknownShell() {
        Output output = CliTestSupport.dispatch("completion", "fish");
        assertEquals(2, output.code());
        assertTrue(output.err().contains("unsupported shell: fish"));
        Output missing = CliTestSupport.dispatch("completion");
        assertEquals(2, missing.code());
        assertTrue(missing.err().contains("needs a shell"));
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        Completion.generate(null);
                    }
                });
    }

    @Test
    void updateDispatchStatusAndDryRun() throws Exception {
        CliTestSupport.writeEnv(jar, CliTestSupport.validEnv());
        Files.createDirectories(jar.resolve("data"));
        Output status = CliTestSupport.dispatch("update", "--status");
        assertEquals(0, status.code());
        assertTrue(status.out().contains("pending: none"));
        Output bare = CliTestSupport.dispatch("update");
        assertEquals(0, bare.code());
        assertTrue(bare.out().contains("pending: none"));
    }
}
