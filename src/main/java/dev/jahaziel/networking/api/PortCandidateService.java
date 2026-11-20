/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.api;

import dev.jahaziel.workspace.domain.WorkspaceId;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;
import java.util.Objects;

/** Finds loopback listeners owned by live Jahaziel-managed task and terminal process trees. */
public abstract class PortCandidateService {
    /**
     * Returns likely preview ports for a workspace without registering or publishing them.
     *
     * @param workspaceId the authorized workspace
     * @return current listening ports held by tracked task or terminal processes
     */
    public abstract List<Candidate> candidates(WorkspaceId workspaceId);

    /** A listener found in the selected workspace's tracked processes. */
    public static final class Candidate {
        private final int port;
        private final List<Source> sources;

        public Candidate(int port, List<Source> sources) {
            if (port < 1 || port > 65535) throw new IllegalArgumentException("port out of range");
            this.sources = List.copyOf(sources);
            if (this.sources.isEmpty())
                throw new IllegalArgumentException("sources must not be empty");
            this.port = port;
        }

        public int port() {
            return port;
        }

        public int getPort() {
            return port;
        }

        public List<Source> sources() {
            return sources;
        }

        public List<Source> getSources() {
            return sources;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Candidate)) return false;
            Candidate other = (Candidate) o;
            return port == other.port && sources.equals(other.sources);
        }

        @Override
        public int hashCode() {
            return Objects.hash(port, sources);
        }

        @Override
        public String toString() {
            return "Candidate[port=" + port + ", sources=" + sources + "]";
        }
    }

    /** Process categories that may own a suggested listener. */
    public static final class Source {
        public static final Source TASK = new Source("TASK");
        public static final Source TERMINAL = new Source("TERMINAL");

        private final String name;

        private Source(String name) {
            this.name = name;
        }

        /** Serializes the source as its stable name. */
        @JsonSerialize(using = ToStringSerializer.class)
        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
