/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.platform.AtomicJsonFiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** File-backed pending logins, sessions, JTI denylist, and encrypted refresh tokens. */
public final class BasisStore {
    private static final long PENDING_LIFETIME_MILLIS = 10L * 60L * 1000L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file;
    private final Path keyFile;
    private final ObjectNode root;
    private final ObjectNode pending;
    private final ObjectNode sessions;
    private final ObjectNode denied;
    private final ObjectNode refresh;
    private final SecretKey encryptionKey;

    public BasisStore(BasisConfig config) {
        try {
            Files.createDirectories(config.identityDir());
            Files.createDirectories(config.keysDir());
        } catch (IOException failure) {
            throw new BasisException("cannot create basis-auth data directories", failure);
        }
        this.file = config.identityDir().resolve("basis-auth-store.json");
        this.keyFile = config.keysDir().resolve("basis-auth.key");
        this.root = loadRoot();
        this.pending = object(root, "pending");
        this.sessions = object(root, "sessions");
        this.denied = object(root, "denied");
        this.refresh = object(root, "refresh");
        prune();
        this.encryptionKey = loadKey();
    }

    public synchronized void putPending(
            String state, String nonce, String verifier, String redirectUri) {
        ObjectNode node = mapper.createObjectNode();
        node.put("nonce", nonce);
        node.put("verifier", verifier);
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
        Pending found =
                new Pending(
                        text(node, "nonce"),
                        text(node, "verifier"),
                        text(node, "redirectUri"),
                        number(node, "expiresAt"));
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
        refresh.remove(sessionId);
        flush();
    }

    public synchronized void removeSessionsForSubject(String subject) {
        List<String> doomed = new ArrayList<String>();
        for (String id : sessions.propertyNames()) {
            if (subject.equals(text(sessions.get(id), "subject"))) {
                doomed.add(id);
            }
        }
        for (int index = 0; index < doomed.size(); index++) {
            String id = doomed.get(index);
            sessions.remove(id);
            refresh.remove(id);
        }
        if (!doomed.isEmpty()) {
            flush();
        }
    }

    public synchronized void addDeniedJti(String jti, long validAfter, long expiresAt) {
        JsonNode existing = denied.get(jti);
        long earliest = validAfter;
        long latestExpiry = expiresAt;
        if (existing != null) {
            earliest = Math.min(earliest, number(existing, "validAfter"));
            latestExpiry = Math.max(latestExpiry, number(existing, "expiresAt"));
        }
        ObjectNode node = mapper.createObjectNode();
        node.put("validAfter", earliest);
        node.put("expiresAt", latestExpiry);
        denied.set(jti, node);
        flush();
    }

    public synchronized boolean isDenied(String jti, long nowSeconds) {
        JsonNode node = denied.get(jti);
        if (node == null) {
            return false;
        }
        long expiresAt = number(node, "expiresAt");
        if (expiresAt > 0 && nowSeconds >= expiresAt) {
            return false;
        }
        return nowSeconds >= number(node, "validAfter");
    }

    synchronized long validAfter(String jti) {
        JsonNode node = denied.get(jti);
        return node == null ? Long.MAX_VALUE : number(node, "validAfter");
    }

    public synchronized void putRefreshToken(String sessionId, String token) {
        if (token == null || token.length() == 0) {
            return;
        }
        refresh.put(sessionId, encrypt(token));
        flush();
    }

    public synchronized String refreshToken(String sessionId) {
        JsonNode node = refresh.get(sessionId);
        return node == null || node.isNull() ? null : decrypt(node.asString());
    }

    /**
     * Rotates a refresh token atomically. A mismatched old token means the token was already used,
     * so the whole session family for this subject is revoked.
     */
    public synchronized void consumeAndReplace(
            String sessionId, String expected, String replacement) {
        JsonNode node = refresh.get(sessionId);
        if (node == null || node.isNull()) {
            throw new RefreshReuseException();
        }
        if (!expected.equals(decrypt(node.asString()))) {
            AuthIdentity identity = getSession(sessionId);
            if (identity == null) {
                clearRefresh(sessionId);
            } else {
                removeSessionsForSubject(identity.subject());
            }
            throw new RefreshReuseException();
        }
        refresh.put(sessionId, encrypt(replacement));
        flush();
    }

    /** Clears refresh state so a new sign-in is required. */
    public synchronized void clearRefresh(String sessionId) {
        if (refresh.remove(sessionId) != null) {
            flush();
        }
    }

    /** Records the absolute expiry of the latest access token for diagnostics. */
    public synchronized void putTokenExpiresAt(String sessionId, long expiresAt) {
        JsonNode node = sessions.get(sessionId);
        if (node != null && node.isObject()) {
            ((ObjectNode) node).put("accessTokenExpiresAt", expiresAt);
            flush();
        }
    }

    /**
     * Sessions whose access token expires inside the window and that still hold a refresh token.
     * Sessions with no recorded expiry are skipped: the app cannot know when they are due.
     */
    public synchronized List<String> sessionsNeedingRefresh(long nowMillis, long windowMillis) {
        List<String> due = new ArrayList<String>();
        for (String id : sessions.propertyNames()) {
            long expiresAt = number(sessions.get(id), "accessTokenExpiresAt");
            if (expiresAt > nowMillis
                    && expiresAt <= nowMillis + windowMillis
                    && refresh.get(id) != null) {
                due.add(id);
            }
        }
        return due;
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
        } catch (Exception failure) {
            AtomicJsonFiles.quarantine(file);
            return mapper.createObjectNode();
        }
        AtomicJsonFiles.quarantine(file);
        return mapper.createObjectNode();
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
                try {
                    byte[] encoded =
                            Base64.getDecoder()
                                    .decode(
                                            new String(
                                                            Files.readAllBytes(keyFile),
                                                            StandardCharsets.UTF_8)
                                                    .trim());
                    return new javax.crypto.spec.SecretKeySpec(encoded, "AES");
                } catch (IllegalArgumentException corrupt) {
                    throw new BasisException("basis-auth encryption key is corrupt", corrupt);
                }
            }
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            SecretKey generated = generator.generateKey();
            String encoded = Base64.getEncoder().encodeToString(generated.getEncoded());
            AtomicJsonFiles.writeAtomically(keyFile, encoded.getBytes(StandardCharsets.UTF_8));
            return generated;
        } catch (IOException failure) {
            throw new BasisException("cannot persist basis-auth encryption key", failure);
        } catch (GeneralSecurityException failure) {
            throw new BasisException("cannot create basis-auth encryption key", failure);
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
            throw new BasisException("cannot encrypt refresh token", failure);
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
            throw new BasisException("cannot decrypt refresh token", failure);
        }
    }

    private void flush() {
        prune();
        try {
            AtomicJsonFiles.writeAtomically(
                    file, mapper.writeValueAsString(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new BasisException("cannot persist basis-auth store", failure);
        }
    }

    /** Drops expired pending logins, denylist entries, and sessions so the store stays bounded. */
    private void prune() {
        long nowMillis = System.currentTimeMillis();
        long nowSeconds = nowMillis / 1000L;
        List<String> doomed = new ArrayList<String>();
        for (String state : pending.propertyNames()) {
            if (number(pending.get(state), "expiresAt") <= nowMillis) {
                doomed.add(state);
            }
        }
        for (int index = 0; index < doomed.size(); index++) {
            pending.remove(doomed.get(index));
        }
        doomed.clear();
        for (String jti : denied.propertyNames()) {
            long expiresAt = number(denied.get(jti), "expiresAt");
            if (expiresAt > 0L && nowSeconds >= expiresAt) {
                doomed.add(jti);
            }
        }
        for (int index = 0; index < doomed.size(); index++) {
            denied.remove(doomed.get(index));
        }
        doomed.clear();
        for (String id : sessions.propertyNames()) {
            long expiresAt = number(sessions.get(id), "accessTokenExpiresAt");
            if (expiresAt > 0L && nowMillis >= expiresAt) {
                doomed.add(id);
            }
        }
        for (int index = 0; index < doomed.size(); index++) {
            String id = doomed.get(index);
            sessions.remove(id);
            refresh.remove(id);
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
        private final String nonce;
        private final String verifier;
        private final String redirectUri;
        private final long expiresAt;

        private Pending(String nonce, String verifier, String redirectUri, long expiresAt) {
            this.nonce = nonce;
            this.verifier = verifier;
            this.redirectUri = redirectUri;
            this.expiresAt = expiresAt;
        }

        public String nonce() {
            return nonce;
        }

        public String verifier() {
            return verifier;
        }

        public String redirectUri() {
            return redirectUri;
        }

        public long expiresAt() {
            return expiresAt;
        }
    }
}
