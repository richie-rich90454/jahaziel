/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.diff;

import dev.jahaziel.agent.AgentException;
import dev.jahaziel.agent.AgentStore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict unified-diff reader. It accepts exactly one file section with well-formed hunk counts and
 * a path that resolves inside the target workspace. Anything else is rejected before staging.
 */
public final class StrictPatchParser {
    private static final Pattern HUNK =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");
    private static final int MAX_PATCH_BYTES = 1024 * 1024;
    private static final int MAX_LINES = 20000;
    private static final int MAX_HUNKS = 512;
    private static final String NO_NEWLINE = "\\ No newline at end of file";

    private final AgentStore store;

    public StrictPatchParser(AgentStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Parses one unified diff and validates that every path stays inside the workspace.
     *
     * @param patchText the agent-produced diff
     * @param workspaceId the workspace the diff claims to touch
     * @return the parsed patch with all decisions pending
     */
    public UnifiedPatch parse(String patchText, UUID workspaceId) {
        if (patchText == null || patchText.isEmpty()) {
            throw new AgentException("patch must not be blank");
        }
        if (patchText.getBytes(StandardCharsets.UTF_8).length > MAX_PATCH_BYTES) {
            throw new AgentException("patch exceeds the size limit");
        }
        String[] split = patchText.split("\n", -1);
        int length = split.length;
        while (length > 0 && clean(split[length - 1]).isEmpty()) {
            length--;
        }
        String[] lines = new String[length];
        System.arraycopy(split, 0, lines, 0, length);
        if (lines.length > MAX_LINES) {
            throw new AgentException("patch exceeds the line limit");
        }

        UnifiedPatch patch = new UnifiedPatch();
        List<UnifiedPatch.Hunk> hunks = new ArrayList<UnifiedPatch.Hunk>();
        List<String> decisions = new ArrayList<String>();
        int index = 0;
        while (index < lines.length && clean(lines[index]).isEmpty()) {
            index++;
        }
        if (index >= lines.length || !clean(lines[index]).startsWith("--- ")) {
            throw new AgentException("patch must start with a --- file header");
        }
        String oldPath = clean(lines[index]).substring(4).trim();
        index++;
        if (index >= lines.length || !clean(lines[index]).startsWith("+++ ")) {
            throw new AgentException("patch must declare a +++ file header");
        }
        String newPath = clean(lines[index]).substring(4).trim();
        index++;
        String relative = validatePair(oldPath, newPath, workspaceId);
        patch.setOldPath(oldPath);
        patch.setNewPath(newPath);
        patch.setFilePath(relative);

        int hunkCount = 0;
        int bodyLines = 0;
        while (index < lines.length) {
            String header = clean(lines[index]);
            if (header.isEmpty()) {
                index++;
                continue;
            }
            if (header.startsWith("--- ")) {
                throw new AgentException("only one file section is allowed per patch");
            }
            Matcher matcher = HUNK.matcher(header);
            if (!matcher.matches()) {
                throw new AgentException("malformed hunk header");
            }
            UnifiedPatch.Hunk hunk = new UnifiedPatch.Hunk();
            hunk.setOldStart(Integer.parseInt(matcher.group(1)));
            hunk.setOldCount(count(matcher.group(2)));
            hunk.setNewStart(Integer.parseInt(matcher.group(3)));
            hunk.setNewCount(count(matcher.group(4)));
            index++;
            int oldSeen = 0;
            int newSeen = 0;
            while (index < lines.length) {
                String line = clean(lines[index]);
                if (line.isEmpty()) {
                    throw new AgentException("empty line inside hunk");
                }
                if (line.startsWith("--- ") || line.startsWith("@@")) {
                    break;
                }
                char kind = line.charAt(0);
                if (kind == ' ') {
                    oldSeen++;
                    newSeen++;
                } else if (kind == '-') {
                    oldSeen++;
                } else if (kind == '+') {
                    newSeen++;
                } else if (!NO_NEWLINE.equals(line)) {
                    throw new AgentException("unexpected line inside hunk");
                }
                hunk.getLines().add(line);
                bodyLines++;
                if (bodyLines > MAX_LINES) {
                    throw new AgentException("patch exceeds the line limit");
                }
                index++;
            }
            if (oldSeen != hunk.getOldCount() || newSeen != hunk.getNewCount()) {
                throw new AgentException("hunk line counts do not match its header");
            }
            hunks.add(hunk);
            decisions.add(UnifiedPatch.PENDING);
            hunkCount++;
            if (hunkCount > MAX_HUNKS) {
                throw new AgentException("patch exceeds the hunk limit");
            }
        }
        if (hunkCount == 0) {
            throw new AgentException("patch must contain at least one hunk");
        }
        patch.setHunks(hunks);
        patch.setDecisions(decisions);
        return patch;
    }

    private String validatePair(String oldPath, String newPath, UUID workspaceId) {
        if (oldPath.indexOf('\t') >= 0 || newPath.indexOf('\t') >= 0) {
            throw new AgentException("patch file headers must not carry timestamps");
        }
        boolean oldNull = "/dev/null".equals(oldPath);
        boolean newNull = "/dev/null".equals(newPath);
        if (oldNull && newNull) {
            throw new AgentException("both sides of a patch cannot be /dev/null");
        }
        String relative = null;
        if (!oldNull) {
            if (!oldPath.startsWith("a/")) {
                throw new AgentException("old patch path must use the a/ prefix");
            }
            relative = store.requireRelativePath(oldPath.substring(2));
        }
        if (!newNull) {
            if (!newPath.startsWith("b/")) {
                throw new AgentException("new patch path must use the b/ prefix");
            }
            String candidate = store.requireRelativePath(newPath.substring(2));
            if (relative == null) {
                relative = candidate;
            } else if (!relative.equals(candidate)) {
                throw new AgentException("patch file headers disagree on the path");
            }
        }
        store.resolveWorkspacePath(workspaceId, relative);
        return relative;
    }

    private static int count(String raw) {
        if (raw == null) {
            return 1;
        }
        int value = Integer.parseInt(raw);
        if (value < 0) {
            throw new AgentException("negative hunk count");
        }
        return value;
    }

    private static String clean(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
