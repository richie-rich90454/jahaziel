/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** A deterministic SHA-256 manifest for a directory tree or an archive's entries. */
public final class ChecksumManifest {
    private static final String HEADER = "# sha256 manifest v1";

    private ChecksumManifest() {}

    /** One manifest line: a relative name and its digest. */
    public static final class Entry {
        private final String name;
        private final String hash;

        /** Creates an entry. */
        public Entry(String name, String hash) {
            this.name = name;
            this.hash = hash;
        }

        /** Returns the relative name. */
        public String name() {
            return name;
        }

        /** Returns the lower-case digest. */
        public String hash() {
            return hash;
        }
    }

    /** Writes a manifest of every regular file under root to manifestFile. */
    public static List<Entry> write(Path root, Path manifestFile) {
        List<Path> files = new ArrayList<Path>();
        collect(root, root, files);
        List<Entry> entries = new ArrayList<Entry>();
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            String name = normalize(root.relativize(file));
            String hash = OpsFiles.sha256(file);
            entries.add(new Entry(name, hash));
            out.append(hash).append("  ").append(name).append('\n');
        }
        OpsFiles.writeText(manifestFile, out.toString());
        return entries;
    }

    /** Writes a manifest from already-computed digests, as produced while zipping. */
    public static void writeEntries(java.util.Map<String, String> hashes, Path manifestFile) {
        List<String> names = new ArrayList<String>(hashes.keySet());
        OpsFiles.sortStrings(names);
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            out.append(hashes.get(name)).append("  ").append(name).append('\n');
        }
        OpsFiles.writeText(manifestFile, out.toString());
    }

    /** Reads the entries of a manifest file. */
    public static List<Entry> read(Path manifestFile) {
        List<Entry> entries = new ArrayList<Entry>();
        String text = OpsFiles.readText(manifestFile);
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int split = line.indexOf("  ");
            if (split < 0) {
                continue;
            }
            entries.add(new Entry(line.substring(split + 2).trim(), line.substring(0, split).trim()));
        }
        return entries;
    }

    /** Returns true when every manifest entry matches the file under root. */
    public static boolean verify(Path root, Path manifestFile) {
        List<Entry> entries = read(manifestFile);
        if (entries.isEmpty()) {
            return false;
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            Path file = root.resolve(entry.name()).normalize();
            if (!OpsFiles.contained(root, file) || !java.nio.file.Files.isRegularFile(file)) {
                return false;
            }
            if (!OpsFiles.sha256(file).equalsIgnoreCase(entry.hash())) {
                return false;
            }
        }
        return true;
    }

    /** Returns true when every digest in the manifest matches the supplied archive entries. */
    public static boolean verifyEntries(java.util.Map<String, byte[]> contents, Path manifestFile) {
        List<Entry> entries = read(manifestFile);
        if (entries.isEmpty()) {
            return false;
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            byte[] bytes = contents.get(entry.name());
            if (bytes == null || !OpsFiles.sha256(bytes).equalsIgnoreCase(entry.hash())) {
                return false;
            }
        }
        return true;
    }

    private static void collect(Path root, Path dir, List<Path> out) {
        File[] children = dir.toFile().listFiles();
        if (children == null) {
            return;
        }
        List<File> sorted = new ArrayList<File>(Arrays.asList(children));
        Collections.sort(
                sorted,
                new Comparator<File>() {
                    @Override
                    public int compare(File left, File right) {
                        return left.getName().compareTo(right.getName());
                    }
                });
        for (int i = 0; i < sorted.size(); i++) {
            File child = sorted.get(i);
            if (child.isDirectory()) {
                collect(root, child.toPath(), out);
            } else if (child.isFile()) {
                out.add(child.toPath());
            }
        }
    }

    private static String normalize(Path relative) {
        return relative.toString().replace(File.separatorChar, '/');
    }
}
