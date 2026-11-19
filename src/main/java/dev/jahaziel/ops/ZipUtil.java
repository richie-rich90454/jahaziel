/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Zip helpers with explicit loops and zip-slip containment. */
public final class ZipUtil {
    private ZipUtil() {}

    /** Adds a byte array as a zip entry. */
    public static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    /** Adds a UTF-8 text entry. */
    public static void putText(ZipOutputStream zip, String name, String text) throws IOException {
        put(zip, name, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Zips a directory tree and returns each entry's SHA-256 digest. */
    public static Map<String, String> zipTree(Path sourceDir, Path zipFile) {
        OpsFiles.ensureDir(zipFile.getParent());
        Map<String, String> hashes = new HashMap<String, String>();
        try {
            OutputStream fileOut = Files.newOutputStream(zipFile);
            ZipOutputStream zip = new ZipOutputStream(fileOut);
            try {
                addTree(sourceDir, sourceDir, zip, hashes);
            } finally {
                zip.close();
            }
        } catch (IOException failure) {
            throw new IllegalStateException("cannot create archive " + zipFile, failure);
        }
        return hashes;
    }

    private static void addTree(
            Path root, Path dir, ZipOutputStream zip, Map<String, String> hashes) throws IOException {
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
                addTree(root, child.toPath(), zip, hashes);
            } else if (child.isFile()) {
                byte[] bytes = Files.readAllBytes(child.toPath());
                String name = normalize(root.relativize(child.toPath()));
                put(zip, name, bytes);
                hashes.put(name, OpsFiles.sha256(bytes));
            }
        }
    }

    /**
     * Extracts every entry under targetDir. A name that escapes the target is rejected before any
     * write, preventing zip-slip.
     */
    public static List<String> unzip(Path zipFile, Path targetDir) {
        OpsFiles.ensureDir(targetDir);
        List<String> extracted = new ArrayList<String>();
        try {
            InputStream fileIn = Files.newInputStream(zipFile);
            ZipInputStream zip = new ZipInputStream(fileIn);
            try {
                ZipEntry entry = zip.getNextEntry();
                while (entry != null) {
                    String name = entry.getName();
                    Path target = targetDir.resolve(name).normalize();
                    if (!OpsFiles.contained(targetDir, target)) {
                        throw new IllegalArgumentException("archive entry escapes target: " + name);
                    }
                    if (entry.isDirectory()) {
                        OpsFiles.ensureDir(target);
                    } else {
                        if (target.getParent() != null) {
                            OpsFiles.ensureDir(target.getParent());
                        }
                        byte[] bytes = readAll(zip);
                        Files.write(target, bytes);
                        extracted.add(normalize(targetDir.relativize(target)));
                    }
                    zip.closeEntry();
                    entry = zip.getNextEntry();
                }
            } finally {
                zip.close();
            }
        } catch (IOException failure) {
            throw new IllegalStateException("cannot extract archive " + zipFile, failure);
        }
        return extracted;
    }

    /** Reads a zip entry's bytes, returned for integrity checks. */
    public static Map<String, byte[]> readEntries(Path zipFile) {
        Map<String, byte[]> entries = new HashMap<String, byte[]>();
        try {
            InputStream fileIn = Files.newInputStream(zipFile);
            ZipInputStream zip = new ZipInputStream(fileIn);
            try {
                ZipEntry entry = zip.getNextEntry();
                while (entry != null) {
                    if (!entry.isDirectory()) {
                        entries.put(normalize(java.nio.file.Paths.get(entry.getName())), readAll(zip));
                    }
                    zip.closeEntry();
                    entry = zip.getNextEntry();
                }
            } finally {
                zip.close();
            }
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read archive " + zipFile, failure);
        }
        return entries;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int count = input.read(buffer);
        while (count >= 0) {
            if (count > 0) {
                out.write(buffer, 0, count);
            }
            count = input.read(buffer);
        }
        return out.toByteArray();
    }

    private static String normalize(Path relative) {
        return relative.toString().replace(File.separatorChar, '/');
    }
}
