/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.perf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * O(1) lookup of a catalog artifact by its (os, arch, id) triple. Artifacts are plain classes so
 * the index can be built from any server-owned catalog list without reflection or code generation.
 */
public final class CatalogIndex {
    /** Plain catalog row; the triple is its identity, the remaining fields are metadata. */
    public static final class Artifact {
        private final String os;
        private final String arch;
        private final String id;
        private final String version;
        private final String url;
        private final String sha256;

        public Artifact(
                String os, String arch, String id, String version, String url, String sha256) {
            this.os = require(os, "os");
            this.arch = require(arch, "arch");
            this.id = require(id, "id");
            this.version = version == null ? "" : version;
            this.url = url == null ? "" : url;
            this.sha256 = sha256 == null ? "" : sha256;
        }

        public String os() {
            return os;
        }

        public String getOs() {
            return os;
        }

        public String arch() {
            return arch;
        }

        public String getArch() {
            return arch;
        }

        public String id() {
            return id;
        }

        public String getId() {
            return id;
        }

        public String version() {
            return version;
        }

        public String getVersion() {
            return version;
        }

        public String url() {
            return url;
        }

        public String getUrl() {
            return url;
        }

        public String sha256() {
            return sha256;
        }

        public String getSha256() {
            return sha256;
        }

        private static String require(String value, String field) {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException(field + " must not be empty");
            }
            return value;
        }
    }

    private static final class Key {
        private final String os;
        private final String arch;
        private final String id;

        private Key(String os, String arch, String id) {
            this.os = os;
            this.arch = arch;
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            Key that = (Key) other;
            return os.equals(that.os) && arch.equals(that.arch) && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            int result = os.hashCode();
            result = 31 * result + arch.hashCode();
            result = 31 * result + id.hashCode();
            return result;
        }
    }

    private final Map<Key, Artifact> byKey = new HashMap<Key, Artifact>();

    public CatalogIndex(List<Artifact> artifacts) {
        if (artifacts == null) {
            throw new IllegalArgumentException("artifacts must not be null");
        }
        for (int i = 0; i < artifacts.size(); i++) {
            Artifact artifact = artifacts.get(i);
            if (artifact == null) {
                throw new IllegalArgumentException("artifact must not be null");
            }
            Key key = new Key(artifact.os(), artifact.arch(), artifact.id());
            if (byKey.containsKey(key)) {
                throw new IllegalArgumentException(
                        "duplicate catalog entry: "
                                + artifact.os()
                                + "/"
                                + artifact.arch()
                                + "/"
                                + artifact.id());
            }
            byKey.put(key, artifact);
        }
    }

    public Artifact find(String os, String arch, String id) {
        return byKey.get(new Key(os, arch, id));
    }

    public boolean contains(String os, String arch, String id) {
        return byKey.containsKey(new Key(os, arch, id));
    }

    public int size() {
        return byKey.size();
    }
}
