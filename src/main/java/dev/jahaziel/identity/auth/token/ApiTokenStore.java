/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.token;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** File-backed token metadata (hash only), all under the data directory. */
public final class ApiTokenStore {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file;
    private final ObjectNode root;
    private final ObjectNode tokens;

    public ApiTokenStore(ApiTokenConfig config) {
        this.file = config.storeFile();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException failure) {
            throw new ApiTokenException("cannot create api-token data directory", failure);
        }
        this.root = loadRoot();
        JsonNode found = root.get("tokens");
        if (found != null && found.isObject()) {
            this.tokens = (ObjectNode) found;
        } else {
            this.tokens = mapper.createObjectNode();
            root.set("tokens", tokens);
        }
    }

    public synchronized void put(TokenRecord record) {
        tokens.set(record.id(), toNode(record));
        flush();
    }

    public synchronized TokenRecord get(String id) {
        JsonNode node = tokens.get(id);
        return node == null ? null : fromNode(id, node);
    }

    public synchronized List<TokenRecord> all() {
        List<TokenRecord> records = new ArrayList<TokenRecord>();
        for (String id : tokens.propertyNames()) {
            records.add(fromNode(id, tokens.get(id)));
        }
        return records;
    }

    public synchronized TokenRecord findByHash(String hash) {
        List<TokenRecord> records = all();
        for (int index = 0; index < records.size(); index++) {
            TokenRecord candidate = records.get(index);
            if (candidate.hash().equals(hash)) {
                return candidate;
            }
        }
        return null;
    }

    private ObjectNode toNode(TokenRecord record) {
        ObjectNode node = mapper.createObjectNode();
        node.put("hash", record.hash());
        node.put("subject", record.subject());
        node.put("email", record.email());
        node.put("displayName", record.displayName());
        node.put("expiresAt", record.expiresAt());
        node.put("active", record.active());
        ArrayNode scopes = mapper.createArrayNode();
        for (String scope : record.scopes()) {
            scopes.add(scope);
        }
        node.set("scopes", scopes);
        return node;
    }

    private TokenRecord fromNode(String id, JsonNode node) {
        List<String> scopes = new ArrayList<String>();
        JsonNode stored = node.get("scopes");
        if (stored != null && stored.isArray()) {
            for (JsonNode scope : stored) {
                scopes.add(scope.asString());
            }
        }
        return new TokenRecord(
                id,
                text(node, "hash"),
                text(node, "subject"),
                text(node, "email"),
                text(node, "displayName"),
                scopes,
                number(node, "expiresAt"),
                node.path("active").asBoolean(false));
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
            throw new ApiTokenException("cannot read api-token store", failure);
        }
        throw new ApiTokenException("api-token store is not an object");
    }

    private void flush() {
        try {
            Files.write(
                    file,
                    mapper.writeValueAsString(root).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException failure) {
            throw new ApiTokenException("cannot persist api-token store", failure);
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
