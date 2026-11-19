/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.oidc;

import dev.jahaziel.identity.auth.AuthIdentity;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * File-backed pending logins and sessions. Everything is written under the plugin data directory.
 */
public final class OidcStore {
    private static final long PENDING_LIFETIME_MILLIS = 10L * 60L * 1000L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file;
    private final ObjectNode root;
    private final ObjectNode pending;
    private final ObjectNode sessions;

    public OidcStore(Path dataDir, String provider) {
        Path dir = dataDir.resolve("oidc");
        try {
            Files.createDirectories(dir);
        } catch (IOException failure) {
            throw new OidcException("cannot create oidc data directory", failure);
        }
        this.file = dir.resolve(provider + "-store.json");
        this.root = loadRoot();
        this.pending = object(root, "pending");
        this.sessions = object(root, "sessions");
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
        flush();
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
            throw new OidcException("cannot read oidc store", failure);
        }
        throw new OidcException("oidc store is not an object");
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

    private void flush() {
        try {
            Files.write(
                    file,
                    mapper.writeValueAsString(root).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException failure) {
            throw new OidcException("cannot persist oidc store", failure);
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
