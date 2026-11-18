/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.model;

import dev.jahaziel.agent.AgentException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Configuration-driven model allowlist. Model ids come from configuration only, and the optional
 * local endpoint must be loopback. A model with no configured capability reports UNAVAILABLE.
 */
public final class ModelCatalog {
    public static final String AVAILABLE = "AVAILABLE";
    public static final String UNAVAILABLE = "UNAVAILABLE";
    private static final String LOCAL_ID = "local";

    private final List<String> ids = new ArrayList<String>();
    private final Map<String, String> capabilities = new HashMap<String, String>();
    private final String localEndpoint;

    /**
     * Creates the catalog from configuration.
     *
     * @param allowedIds configured model ids, never browser supplied
     * @param configuredEndpoint an optional local endpoint, or null
     */
    public ModelCatalog(List<String> allowedIds, String configuredEndpoint) {
        if (allowedIds != null) {
            for (String id : allowedIds) {
                if (id == null || !id.matches("[A-Za-z0-9._:-]{1,64}")) {
                    throw new AgentException("invalid configured model id");
                }
                if (!capabilities.containsKey(id)) {
                    ids.add(id);
                    capabilities.put(id, UNAVAILABLE);
                }
            }
        }
        this.localEndpoint = loopbackEndpoint(configuredEndpoint);
        for (String id : ids) {
            if (!LOCAL_ID.equals(id) || this.localEndpoint != null) {
                capabilities.put(id, AVAILABLE);
            }
        }
    }

    /** Returns every allowlisted model with its current capability. */
    public List<ModelInfo> catalog() {
        List<ModelInfo> result = new ArrayList<ModelInfo>();
        for (String id : ids) {
            ModelInfo info = new ModelInfo();
            info.setId(id);
            info.setCapability(capability(id));
            result.add(info);
        }
        return result;
    }

    /** True when an id was present in configuration. */
    public boolean isAllowed(String id) {
        return id != null && capabilities.containsKey(id);
    }

    /** Returns AVAILABLE or UNAVAILABLE, never a value for a non-allowlisted id. */
    public String capability(String id) {
        String capability = id == null ? null : capabilities.get(id);
        return capability == null ? UNAVAILABLE : capability;
    }

    /** Returns the validated loopback endpoint, or null when absent or invalid. */
    public String localEndpoint() {
        return localEndpoint;
    }

    private static String loopbackEndpoint(String configured) {
        if (configured == null || configured.trim().isEmpty()) {
            return null;
        }
        try {
            URI uri = URI.create(configured.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            boolean loopback =
                "127.0.0.1".equals(normalizedHost)
                    || "::1".equals(normalizedHost)
                    || "[::1]".equals(normalizedHost)
                    || "localhost".equals(normalizedHost);
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            if (!loopback || uri.getUserInfo() != null) {
                return null;
            }
            return uri.toString();
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** One catalog entry. */
    public static final class ModelInfo {
        private String id;
        private String capability;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getCapability() {
            return capability;
        }

        public void setCapability(String capability) {
            this.capability = capability;
        }
    }
}
