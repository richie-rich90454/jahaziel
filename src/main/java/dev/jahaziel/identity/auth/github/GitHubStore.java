/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.github;

import dev.jahaziel.identity.auth.AuthIdentity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
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

/** File-backed pending logins, sessions, and AES-GCM encrypted access tokens. */
public final class GitHubStore {
    private static final long PENDING_LIFETIME_MILLIS = 5L * 60L * 1000L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file;
    private final Path keyFile;
    private final ObjectNode root;
    private final ObjectNode pending;
    private final ObjectNode sessions;
    private final ObjectNode tokens;
    private final SecretKey encryptionKey;

    public GitHubStore(GitHubConfig config) {
        try {
            Files.createDirectories(config.identityDir());
            Files.createDirectories(config.keysDir());
        } catch (IOException failure) {
            throw new GitHubException("cannot create github data directories", failure);
        }
        this.file = config.identityDir().resolve("github-auth-store.json");
        this.keyFile = config.keysDir().resolve("github-auth.key");
        this.root = loadRoot();
        this.pending = object(root, "pending");
        this.sessions = object(root, "sessions");
        this.tokens = object(root, "tokens");
        this.encryptionKey = loadKey();
    }

    public synchronized void putPending(String state, String redirectUri) {
        ObjectNode node = mapper.createObjectNode();
        node.put("redirectUri", redirectUri);
        node.put("expiresAt", System.currentTimeMillis() + PENDING_LIFETIME_MILLIS);
        pending.set(state, node);
        flush();
    }

    public synchronized Pending takePending(String state) {
        JsonNode node = pending.get(state);
        if (node == null) {
            return null;
        }
        Pending found = new Pending(text(node, "redirectUri"), number(node, "expiresAt"));
        pending.remove(state);
        flush();
        if (found.expiresAt() <= System.currentTimeMillis()) {
            return null;
        }
        return found;
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
        flush();
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
        tokens.remove(sessionId);
        flush();
    }

    public synchronized void putAccessToken(String sessionId, String token) {
        if (token == null || token.length() == 0) {
            return;
        }
        tokens.put(sessionId, encrypt(token));
        flush();
    }

    public synchronized String accessToken(String sessionId) {
        JsonNode node = tokens.get(sessionId);
        return node == null || node.isNull() ? null : decrypt(node.asString());
    }

    private ObjectNode loadRoot() {
        if (!Files.exists(file)) {
            return mapper.createObjectNode();
        }
        try {
            JsonNode node = mapper.readTree(Files.readAllBytes(file));
            if (node != null && node.isObject()) {
                return (ObjectNode) node;
            }
        } catch (IOException failure) {
            throw new GitHubException("cannot read github store", failure);
        }
        throw new GitHubException("github store is not an object");
    }

    private ObjectNode object(ObjectNode parent, String name) {
        JsonNode node = parent.get(name);
        if (node != null && node.isObject()) {
            return (ObjectNode) node;
        }
        ObjectNode created = mapper.createObjectNode();
        parent.set(name, created);
        return created;
    }

    private SecretKey loadKey() {
        try {
            if (Files.exists(keyFile)) {
                byte[] encoded = Base64.getDecoder().decode(
                        new String(Files.readAllBytes(keyFile), StandardCharsets.UTF_8).trim());
                return new SecretKeySpec(encoded, "AES");
            }
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            SecretKey generated = generator.generateKey();
            String encoded = Base64.getEncoder().encodeToString(generated.getEncoded());
            Files.write(keyFile, encoded.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return generated;
        } catch (IOException failure) {
            throw new GitHubException("cannot persist github encryption key", failure);
        } catch (GeneralSecurityException failure) {
            throw new GitHubException("cannot create github encryption key", failure);
        }
    }

    private String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException failure) {
            throw new GitHubException("cannot encrypt github access token", failure);
        }
    }

    private String decrypt(String value) {
        try {
            byte[] combined = Base64.getDecoder().decode(value);
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - iv.length];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException failure) {
            throw new GitHubException("cannot decrypt github access token", failure);
        }
    }

    private void flush() {
        try {
            Files.write(file, mapper.writeValueAsString(root).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException failure) {
            throw new GitHubException("cannot persist github store", failure);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static long number(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? 0L : value.asLong();
    }

    /** One pending browser login. */
    public static final class Pending {
        private final String redirectUri;
        private final long expiresAt;

        private Pending(String redirectUri, long expiresAt) {
            this.redirectUri = redirectUri;
            this.expiresAt = expiresAt;
        }

        public String redirectUri() {
            return redirectUri;
        }

        public long expiresAt() {
            return expiresAt;
        }
    }
}
