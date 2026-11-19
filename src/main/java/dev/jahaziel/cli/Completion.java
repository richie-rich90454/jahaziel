/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Shell completion scripts generated locally from the CLI command tree. No package is installed and
 * nothing is written to disk: the script goes to standard output.
 */
public final class Completion {
    /** One node in the command tree. */
    public static final class Node {
        private final String name;
        private final String description;
        private final List<Node> children = new ArrayList<Node>();
        private final List<String> flags = new ArrayList<String>();

        /** Creates a node. */
        public Node(String name, String description) {
            this.name = name;
            this.description = description;
        }

        /** Adds a child command and returns it. */
        public Node child(String childName, String childDescription) {
            Node child = new Node(childName, childDescription);
            children.add(child);
            return child;
        }

        /** Adds a flag to this node. */
        public void flag(String flag) {
            flags.add(flag);
        }

        /** Returns the command name. */
        public String name() {
            return name;
        }

        /** Returns the short description. */
        public String description() {
            return description;
        }

        /** Returns the child commands. */
        public List<Node> children() {
            return children;
        }

        /** Returns the flags. */
        public List<String> flags() {
            return flags;
        }
    }

    private Completion() {}

    /** Builds the command tree used by the CLI. */
    public static Node tree() {
        Node root = new Node("jahaziel", "Portable Jahaziel CLI");
        root.flag("--help");
        root.flag("--version");
        root.flag("--offline");

        root.child("init", "Write .env and create data/");

        Node doctor = root.child("doctor", "Run pre-boot checks");
        doctor.flag("--data-dir");

        Node update = root.child("update", "Stage and verify a jar update");
        update.flag("--to");
        update.flag("--sums");
        update.flag("--version");
        update.flag("--dry-run");
        update.flag("--status");
        update.flag("--rollback");

        Node upgrade = root.child("upgrade", "Replace the running jar offline");
        upgrade.flag("--to");
        upgrade.flag("--check");

        Node backup = root.child("backup", "Write a tar.gz backup");
        backup.flag("--no-secrets");

        root.child("restore", "Extract a backup archive");
        root.child("logs", "Tail the control-plane log").flag("--follow");
        root.child("support-bundle", "Write a redacted diagnostics bundle");
        root.child("completion", "Generate a shell completion script");

        Node ops = root.child("ops", "Delegate to operational services");
        ops.child("support-bundle", "Build a verified support archive");
        ops.child("backup", "Create a scheduled snapshot");
        ops.child("restore-drill", "Verify the newest backup in a disposable workspace");
        Node audit = ops.child("audit", "Inspect the audit trail");
        audit.child("query", "Filter and print audit entries").flag("--actor");
        ops.child("export-user", "Export a user's data").flag("--confirm");
        ops.child("delete-user", "Delete a user after typed confirmation").flag("--confirm");
        ops.child("reindex", "Rebuild workspace search indexes").flag("--workspace");
        ops.child("capabilities", "List tool capabilities");
        return root;
    }

    /**
     * Generates a completion script.
     *
     * @param shell one of {@code bash}, {@code zsh}, {@code powershell}
     * @return the script text
     */
    public static String generate(String shell) {
        if (shell == null) {
            throw new IllegalArgumentException("shell is required (bash, zsh, powershell)");
        }
        String normalized = shell.trim().toLowerCase(java.util.Locale.ROOT);
        if ("bash".equals(normalized)) {
            return bash();
        }
        if ("zsh".equals(normalized)) {
            return zsh();
        }
        if ("powershell".equals(normalized)) {
            return powershell();
        }
        throw new IllegalArgumentException("unsupported shell: " + shell + " (bash, zsh, powershell)");
    }

    private static List<String> words() {
        List<String> words = new ArrayList<String>();
        collect(tree(), words, true);
        return words;
    }

    private static void collect(Node node, List<String> out, boolean root) {
        if (!root) {
            add(out, node.name());
        }
        List<Node> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            collect(children.get(i), out, false);
        }
        List<String> flags = node.flags();
        for (int i = 0; i < flags.size(); i++) {
            add(out, flags.get(i));
        }
    }

    private static void add(List<String> out, String value) {
        if (!out.contains(value)) {
            out.add(value);
        }
    }

    private static String bash() {
        StringBuilder out = new StringBuilder();
        out.append("# generated locally by `jahaziel completion bash`; no install required\n");
        out.append("_jahaziel_complete() {\n");
        out.append("    local cur=\"${COMP_WORDS[COMP_CWORD]}\"\n");
        out.append("    local words=\"").append(join(words(), " ")).append("\"\n");
        out.append("    COMPREPLY=( $(compgen -W \"$words\" -- \"$cur\") )\n");
        out.append("}\n");
        out.append("complete -F _jahaziel_complete jahaziel\n");
        return out.toString();
    }

    private static String zsh() {
        StringBuilder out = new StringBuilder();
        out.append("#compdef jahaziel\n");
        out.append("# generated locally by `jahaziel completion zsh`; no install required\n");
        out.append("_jahaziel() {\n");
        out.append("    local -a words\n");
        StringBuilder list = new StringBuilder("    words=(");
        List<String> values = words();
        for (int i = 0; i < values.size(); i++) {
            list.append('\'').append(values.get(i)).append('\'');
            if (i + 1 < values.size()) {
                list.append(' ');
            }
        }
        out.append(list.append(")\n"));
        out.append("    _describe 'jahaziel commands' words\n");
        out.append("}\n");
        out.append("compdef _jahaziel jahaziel\n");
        return out.toString();
    }

    private static String powershell() {
        StringBuilder out = new StringBuilder();
        out.append("# generated locally by `jahaziel completion powershell`; no install required\n");
        out.append("Register-ArgumentCompleter -Native -CommandName jahaziel -ScriptBlock {\n");
        out.append("    param($wordToComplete, $commandAst, $cursorPosition)\n");
        StringBuilder list = new StringBuilder("    $words = @(");
        List<String> values = words();
        for (int i = 0; i < values.size(); i++) {
            list.append('\'').append(values.get(i).replace("'", "''")).append('\'');
            if (i + 1 < values.size()) {
                list.append(", ");
            }
        }
        out.append(list.append(")\n"));
        out.append("    foreach ($word in $words) {\n");
        out.append("        if ($word -like \"$wordToComplete*\") {\n");
        out.append("            [System.Management.Automation.CompletionResult]::new(");
        out.append("$word, $word, 'ParameterValue', $word)\n");
        out.append("        }\n");
        out.append("    }\n");
        out.append("}\n");
        return out.toString();
    }

    private static String join(List<String> values, String separator) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(separator);
            }
            out.append(values.get(i));
        }
        return out.toString();
    }
}
