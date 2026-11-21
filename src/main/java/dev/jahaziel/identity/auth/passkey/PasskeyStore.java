/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.passkey;

import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.platform.AtomicJsonFiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** File-backed credentials, pending ceremonies, and sessions, all under the data directory. */
public final class PasskeyStore {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file;
    private final ObjectNode root;
    private final ObjectNode credentials;
    private final ObjectNode pending;
    private final ObjectNode sessions;

    public PasskeyStore(PasskeyConfig config) {
        this.file = config.storeFile();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException failure) {
            throw new PasskeyException("cannot create passkey data directory", failure);
        }
        this.root = loadRoot();
        this.credentials = object(root, "credentials");
        this.pending = object(root, "pending");
        this.sessions = object(root, "sessions");
    }

    public synchronized void putCredential(PasskeyCredential credential) {
        ObjectNode node = mapper.createObjectNode();
        node.put("publicKey", WebAuthn.encode(credential.cosePublicKey()));
        node.put("signCount", credential.signCount());
        node.put("rpId", credential.rpId());
        credentials.set(credential.credentialId(), node);
        flush();
    }

    public synchronized PasskeyCredential getCredential(String credentialId) {
        JsonNode node = credentials.get(credentialId);
        if (node == null) {
            return null;
        }
        return new PasskeyCredential(
                credentialId,
                WebAuthn.decode(text(node, "publicKey")),
                number(node, "signCount"),
                text(node, "rpId"));
    }

    public synchronized void updateSignCount(String credentialId, long signCount) {
        JsonNode node = credentials.get(credentialId);
        if (node != null && node.isObject()) {
            ((ObjectNode) node).put("signCount", signCount);
            flush();
        }
    }

    public synchronized void putPending(String state, PasskeyPending value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("challenge", value.challenge());
        node.put("nonce", value.nonce());
        node.put("redirectUri", value.redirectUri());
        node.put("expiresAt", value.expiresAt());
        pending.set(state, node);
        flush();
    }

    public synchronized PasskeyPending takePending(String state) {
        JsonNode node = pending.get(state);
        if (node == null) {
            return null;
        }
        PasskeyPending found =
                new PasskeyPending(
                        text(node, "challenge"),
                        text(node, "nonce"),
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

    private void flush() {
        try {
            AtomicJsonFiles.writeAtomically(
                    file, mapper.writeValueAsString(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new PasskeyException("cannot persist passkey store", failure);
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
}
