/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Loads bundled and jar-adjacent plugins, verifying a SHA-256 manifest first. */
public final class PluginLoader {
    private static final String CHECKSUM_SUFFIX = ".sha256";
    private final List<Plugin> builtIns;

    public PluginLoader(List<Plugin> builtIns) {
        Objects.requireNonNull(builtIns, "builtIns");
        this.builtIns = Collections.unmodifiableList(new ArrayList<Plugin>(builtIns));
    }

    public List<Plugin> builtIns() {
        return builtIns;
    }

    public List<Plugin> loadFrom(Path directory) throws IOException {
        List<Plugin> loaded = new ArrayList<Plugin>(builtIns);
        if (directory == null || !Files.isDirectory(directory)) {
            return loaded;
        }
        List<Path> jars = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.jar")) {
            for (Path jar : stream) {
                jars.add(jar);
            }
        }
        Collections.sort(jars);
        for (Path jar : jars) {
            verifyChecksum(jar);
            loaded.addAll(loadJar(jar));
        }
        return loaded;
    }

    private void verifyChecksum(Path jar) throws IOException {
        Path checksum = jar.resolveSibling(jar.getFileName().toString() + CHECKSUM_SUFFIX);
        if (!Files.isRegularFile(checksum)) {
            throw new IOException("missing plugin checksum: " + checksum);
        }
        String expected = new String(Files.readAllBytes(checksum), StandardCharsets.UTF_8).trim();
        String actual = sha256(jar);
        if (!expected.equalsIgnoreCase(actual)) {
            throw new IOException("plugin checksum mismatch: " + jar);
        }
    }

    private String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IOException("SHA-256 is unavailable", failure);
        }
        byte[] buffer = new byte[65536];
        try (InputStream input = Files.newInputStream(path)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder text = new StringBuilder(64);
        for (byte value : digest.digest()) {
            String hex = Integer.toHexString(value & 0xff);
            if (hex.length() == 1) {
                text.append('0');
            }
            text.append(hex);
        }
        return text.toString();
    }

    private List<Plugin> loadJar(Path jar) throws IOException {
        List<Plugin> found = new ArrayList<Plugin>();
        URL url = jar.toUri().toURL();
        URLClassLoader loader =
                new URLClassLoader(new URL[] {url}, PluginLoader.class.getClassLoader());
        try (JarFile archive = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (!entryName.endsWith(".class")) {
                    continue;
                }
                String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');
                try {
                    Class<?> type = Class.forName(className, false, loader);
                    if (!Plugin.class.isAssignableFrom(type)) {
                        continue;
                    }
                    if (java.lang.reflect.Modifier.isAbstract(type.getModifiers())) {
                        continue;
                    }
                    java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    found.add((Plugin) constructor.newInstance());
                } catch (ReflectiveOperationException failure) {
                    throw new IOException(
                            "cannot load plugin class " + className + " from " + jar, failure);
                }
            }
        }
        return found;
    }
}
