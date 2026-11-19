/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.local;

import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.plugin.PluginContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** File-backed users, pending logins, and sessions, all below the plugin data dir. */
public final class LocalStore {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path usersFile;
    private final Path pendingFile;
    private final Path sessionsFile;
    private final Path keyFile;
    private final ObjectNode users;
    private final ObjectNode pending;
    private final ObjectNode sessions;
    private final SecretKey encryptionKey;

    public LocalStore(PluginContext context) {
        Path root = context.dataDir().resolve("local");
        Path keysDir = context.dataDir().resolve("keys");
        try {
            Files.createDirectories(root);
            Files.createDirectories(keysDir);
        } catch (IOException failure) {
            throw new LocalAuthException("cannot create local auth directories", failure);
        }
        this.usersFile = root.resolve("users.json");
        this.pendingFile = root.resolve("pending.json");
        this.sessionsFile = root.resolve("sessions.json");
        this.keyFile = keysDir.resolve("local.key");
        this.users = loadRoot(usersFile);
        this.pending = loadRoot(pendingFile);
        this.sessions = loadRoot(sessionsFile);
        this.encryptionKey = loadKey();
    }

    public synchronized boolean hasUser(String username) {
        return users.get(username) != null;
    }

    public synchronized void createUser(
            String username,
            String email,
            byte[] salt,
            byte[] hash,
            int iterations,
            String encryptedSecret) {
        User user =
                new User(
                        username,
                        email,
                        salt,
                        hash,
                        iterations,
                        encryptedSecret,
                        -1L,
                        false,
                        false);
        writeUser(user);
    }

    public synchronized User getUser(String username) {
        JsonNode node = users.get(username);
        if (node == null) {
            return null;
        }
        String email = text(node, "email");
        if (email == null) {
            email = "";
        }
        String encryptedSecret = text(node, "secret");
        if (encryptedSecret == null) {
            encryptedSecret = "";
        }
        JsonNode counterNode = node.get("totpCounter");
        long totpCounter = counterNode == null ? -1L : counterNode.asLong();
        int iterations = node.path("iterations").asInt();
        boolean enrolled = node.path("enrolled").asBoolean(false);
        boolean admin = node.path("admin").asBoolean(false);
        return new User(
                username,
                email,
                decode(text(node, "salt")),
                decode(text(node, "hash")),
                iterations,
                encryptedSecret,
                totpCounter,
                enrolled,
                admin);
    }

    public synchronized void saveUser(User user) {
        writeUser(user);
    }

    /** Decrypts the TOTP secret, or returns null when the user has not enrolled. */
    public synchronized byte[] totpSecret(User user) {
        return user.encryptedSecret().length() == 0 ? null : decrypt(user.encryptedSecret());
    }

    /** Encrypts a TOTP secret for storage at rest. */
    public synchronized String encryptSecret(byte[] secret) {
        return encrypt(secret);
    }

    public synchronized void putPending(
            String state, String nonce, String redirectUri, long createdAt) {
        ObjectNode node = mapper.createObjectNode();
        node.put("nonce", nonce);
        node.put("redirectUri", redirectUri);
        node.put("createdAt", createdAt);
        pending.set(state, node);
        flush(pendingFile, pending);
    }

    /** Consumes a pending login, returning false when the state is unknown. */
    public synchronized boolean takePending(String state) {
        if (pending.get(state) == null) {
            return false;
        }
        pending.remove(state);
        flush(pendingFile, pending);
        return true;
    }

    public synchronized void putSession(String sessionId, AuthIdentity identity) {
        ObjectNode node = mapper.createObjectNode();
        node.put("provider", identity.provider());
        node.put("subject", identity.subject());
        node.put("email", identity.email());
        node.put("displayName", identity.displayName());
        node.put("emailVerified", identity.emailVerified());
        node.put("mfaAssured", identity.mfaAssured());
        ObjectNode claims = mapper.createObjectNode();
        for (Map.Entry<String, String> entry : identity.claims().entrySet()) {
            claims.put(entry.getKey(), entry.getValue());
        }
        node.set("claims", claims);
        sessions.set(sessionId, node);
        flush(sessionsFile, sessions);
    }

    public synchronized AuthIdentity getSession(String sessionId) {
        JsonNode node = sessions.get(sessionId);
        if (node == null) {
            return null;
        }
        Map<String, String> claims = new HashMap<String, String>();
        JsonNode stored = node.get("claims");
        if (stored != null && stored.isObject()) {
            for (String name : stored.propertyNames()) {
                claims.put(name, stored.get(name).asString());
            }
        }
        return new AuthIdentity(
                text(node, "provider"),
                text(node, "subject"),
                text(node, "email"),
                text(node, "displayName"),
                node.path("emailVerified").asBoolean(false),
                node.path("mfaAssured").asBoolean(false),
                claims);
    }

    public synchronized void removeSession(String sessionId) {
        sessions.remove(sessionId);
        flush(sessionsFile, sessions);
    }

    private void writeUser(User user) {
        ObjectNode node = mapper.createObjectNode();
        node.put("username", user.username());
        node.put("email", user.email());
        node.put("salt", encode(user.salt()));
        node.put("hash", encode(user.hash()));
        node.put("iterations", user.iterations());
        node.put("secret", user.encryptedSecret());
        node.put("totpCounter", user.totpCounter());
        node.put("enrolled", user.enrolled());
        node.put("admin", user.admin());
        users.set(user.username(), node);
        flush(usersFile, users);
    }

    private ObjectNode loadRoot(Path file) {
        if (!Files.exists(file)) {
            return mapper.createObjectNode();
        }
        try {
            JsonNode node = mapper.readTree(Files.readAllBytes(file));
            if (node != null && node.isObject()) {
                return (ObjectNode) node;
            }
        } catch (IOException failure) {
            throw new LocalAuthException("cannot read local auth store", failure);
        }
        throw new LocalAuthException("local auth store is not an object");
    }

    private SecretKey loadKey() {
        try {
            if (Files.exists(keyFile)) {
                byte[] encoded =
                        Base64.getDecoder()
                                .decode(
                                        new String(
                                                        Files.readAllBytes(keyFile),
                                                        StandardCharsets.UTF_8)
                                                .trim());
                return new SecretKeySpec(encoded, "AES");
            }
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            SecretKey generated = generator.generateKey();
            String encoded = Base64.getEncoder().encodeToString(generated.getEncoded());
            Files.write(
                    keyFile,
                    encoded.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return generated;
        } catch (IOException failure) {
            throw new LocalAuthException("cannot persist local auth key", failure);
        } catch (GeneralSecurityException failure) {
            throw new LocalAuthException("cannot create local auth key", failure);
        }
    }

    private String encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain);
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return encode(combined);
        } catch (GeneralSecurityException failure) {
            throw new LocalAuthException("cannot encrypt TOTP secret", failure);
        }
    }

    private byte[] decrypt(String value) {
        try {
            byte[] combined = decode(value);
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - iv.length];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            return cipher.doFinal(encrypted);
        } catch (GeneralSecurityException failure) {
            throw new LocalAuthException("cannot decrypt TOTP secret", failure);
        }
    }

    private void flush(Path file, ObjectNode node) {
        try {
            Files.write(
                    file,
                    mapper.writeValueAsString(node).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException failure) {
            throw new LocalAuthException("cannot persist local auth store", failure);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private static byte[] decode(String value) {
        return value == null ? new byte[0] : Base64.getDecoder().decode(value);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    /** One stored local user. Mutable enrollment fields are updated as MFA is assured. */
    public static final class User {
        private final String username;
        private final String email;
        private final byte[] salt;
        private final byte[] hash;
        private final int iterations;
        private final String encryptedSecret;
        private long totpCounter;
        private boolean enrolled;
        private boolean admin;

        private User(
                String username,
                String email,
                byte[] salt,
                byte[] hash,
                int iterations,
                String encryptedSecret,
                long totpCounter,
                boolean enrolled,
                boolean admin) {
            this.username = username;
            this.email = email;
            this.salt = salt;
            this.hash = hash;
            this.iterations = iterations;
            this.encryptedSecret = encryptedSecret;
            this.totpCounter = totpCounter;
            this.enrolled = enrolled;
            this.admin = admin;
        }

        public String username() {
            return username;
        }

        public String email() {
            return email;
        }

        public byte[] salt() {
            return salt;
        }

        public byte[] hash() {
            return hash;
        }

        public int iterations() {
            return iterations;
        }

        public String encryptedSecret() {
            return encryptedSecret;
        }

        public long totpCounter() {
            return totpCounter;
        }

        public boolean enrolled() {
            return enrolled;
        }

        public boolean admin() {
            return admin;
        }

        public void setTotpCounter(long value) {
            totpCounter = value;
        }

        public void setEnrolled(boolean value) {
            enrolled = value;
        }

        public void setAdmin(boolean value) {
            admin = value;
        }
    }
}
