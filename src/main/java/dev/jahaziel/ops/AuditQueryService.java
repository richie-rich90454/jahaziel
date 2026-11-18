/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Query, export and hash-chain verification for audit entries. */
public final class AuditQueryService {
    /** One append-only audit entry with a hash-chain link. */
    public static final class AuditEntry {
        private final String eventId;
        private final Instant timestamp;
        private final String actor;
        private final String action;
        private final String workspace;
        private final String result;
        private final String details;
        private final String prevHash;

        private final String hash;

        /** Creates an entry and computes its hash from the canonical fields. */
        public AuditEntry(
                String eventId,
                Instant timestamp,
                String actor,
                String action,
                String workspace,
                String result,
                String details,
                String prevHash) {
            this(
                    eventId,
                    timestamp,
                    actor,
                    action,
                    workspace,
                    result,
                    details,
                    prevHash,
                    computeHash(eventId, timestamp, actor, action, workspace, result, details, prevHash));
        }

        /** Creates an entry with an explicit hash, used when verifying stored records. */
        public AuditEntry(
                String eventId,
                Instant timestamp,
                String actor,
                String action,
                String workspace,
                String result,
                String details,
                String prevHash,
                String hash) {
            this.eventId = eventId;
            this.timestamp = timestamp;
            this.actor = actor;
            this.action = action;
            this.workspace = workspace;
            this.result = result;
            this.details = details;
            this.prevHash = prevHash;
            this.hash = hash;
        }

        /** Returns the event id. */
        public String eventId() {
            return eventId;
        }

        /** Returns the timestamp. */
        public Instant timestamp() {
            return timestamp;
        }

        /** Returns the actor. */
        public String actor() {
            return actor;
        }

        /** Returns the action. */
        public String action() {
            return action;
        }

        /** Returns the workspace id, or empty. */
        public String workspace() {
            return workspace;
        }

        /** Returns the result. */
        public String result() {
            return result;
        }

        /** Returns the details. */
        public String details() {
            return details;
        }

        /** Returns the previous hash. */
        public String prevHash() {
            return prevHash;
        }

        /** Returns the stored hash. */
        public String hash() {
            return hash;
        }

        /** Recomputes the hash from this entry's canonical fields. */
        public String computeHash() {
            return computeHash(eventId, timestamp, actor, action, workspace, result, details, prevHash);
        }

        private static String computeHash(
                String eventId,
                Instant timestamp,
                String actor,
                String action,
                String workspace,
                String result,
                String details,
                String prevHash) {
            String canonical =
                    value(eventId)
                            + "\u001f"
                            + value(timestamp)
                            + "\u001f"
                            + value(actor)
                            + "\u001f"
                            + value(action)
                            + "\u001f"
                            + value(workspace)
                            + "\u001f"
                            + value(result)
                            + "\u001f"
                            + value(details)
                            + "\u001f"
                            + value(prevHash);
            return OpsFiles.sha256(canonical.getBytes(StandardCharsets.UTF_8));
        }

        private static String value(Object object) {
            return object == null ? "" : object.toString();
        }
    }

    /** Optional filters; a null field means no constraint. */
    public static final class AuditFilter {
        private Instant from;
        private Instant to;
        private String actor;
        private String action;
        private String workspace;
        private String result;

        /** Creates an empty filter. */
        public AuditFilter() {}

        /** Sets the inclusive lower time bound. */
        public void from(Instant value) {
            from = value;
        }

        /** Sets the inclusive upper time bound. */
        public void to(Instant value) {
            to = value;
        }

        /** Sets the actor filter. */
        public void actor(String value) {
            actor = value;
        }

        /** Sets the action filter. */
        public void action(String value) {
            action = value;
        }

        /** Sets the workspace filter. */
        public void workspace(String value) {
            workspace = value;
        }

        /** Sets the result filter. */
        public void result(String value) {
            result = value;
        }

        /** Returns the lower bound. */
        public Instant from() {
            return from;
        }

        /** Returns the upper bound. */
        public Instant to() {
            return to;
        }

        private boolean matches(AuditEntry entry) {
            if (from != null && entry.timestamp().isBefore(from)) {
                return false;
            }
            if (to != null && entry.timestamp().isAfter(to)) {
                return false;
            }
            if (actor != null && !actor.equals(entry.actor())) {
                return false;
            }
            if (action != null && !action.equals(entry.action())) {
                return false;
            }
            if (workspace != null && !workspace.equals(entry.workspace())) {
                return false;
            }
            if (result != null && !result.equals(entry.result())) {
                return false;
            }
            return true;
        }
    }

    /** Result of a hash-chain walk. */
    public static final class ChainStatus {
        private final boolean valid;
        private final int brokenIndex;
        private final String detail;

        /** Creates a status. */
        public ChainStatus(boolean valid, int brokenIndex, String detail) {
            this.valid = valid;
            this.brokenIndex = brokenIndex;
            this.detail = detail;
        }

        /** Returns whether the chain is intact. */
        public boolean valid() {
            return valid;
        }

        /** Returns the index of the first broken entry, or -1. */
        public int brokenIndex() {
            return brokenIndex;
        }

        /** Returns a human-readable explanation. */
        public String detail() {
            return detail;
        }
    }

    /** Returns entries matching every supplied filter, preserving input order. */
    public List<AuditEntry> query(List<AuditEntry> entries, AuditFilter filter) {
        List<AuditEntry> out = new ArrayList<AuditEntry>();
        if (entries == null) {
            return out;
        }
        AuditFilter effective = filter == null ? new AuditFilter() : filter;
        for (int i = 0; i < entries.size(); i++) {
            AuditEntry entry = entries.get(i);
            if (effective.matches(entry)) {
                out.add(entry);
            }
        }
        return out;
    }

    /** Exports entries as CSV with a header row. */
    public String toCsv(List<AuditEntry> entries) {
        StringBuilder out = new StringBuilder("eventId,timestamp,actor,action,workspace,result,details,prevHash,hash\n");
        for (int i = 0; i < entries.size(); i++) {
            AuditEntry entry = entries.get(i);
            out.append(cell(entry.eventId())).append(',');
            out.append(cell(value(entry.timestamp()))).append(',');
            out.append(cell(entry.actor())).append(',');
            out.append(cell(entry.action())).append(',');
            out.append(cell(entry.workspace())).append(',');
            out.append(cell(entry.result())).append(',');
            out.append(cell(entry.details())).append(',');
            out.append(cell(entry.prevHash())).append(',');
            out.append(cell(entry.hash())).append('\n');
        }
        return out.toString();
    }

    /** Exports entries as a JSON array. */
    public String toJson(List<AuditEntry> entries) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            AuditEntry entry = entries.get(i);
            out.append('{');
            out.append("\"eventId\":\"").append(OpsFiles.jsonEscape(entry.eventId())).append('"');
            out.append(",\"timestamp\":\"").append(OpsFiles.jsonEscape(value(entry.timestamp()))).append('"');
            out.append(",\"actor\":\"").append(OpsFiles.jsonEscape(entry.actor())).append('"');
            out.append(",\"action\":\"").append(OpsFiles.jsonEscape(entry.action())).append('"');
            out.append(",\"workspace\":\"").append(OpsFiles.jsonEscape(entry.workspace())).append('"');
            out.append(",\"result\":\"").append(OpsFiles.jsonEscape(entry.result())).append('"');
            out.append(",\"details\":\"").append(OpsFiles.jsonEscape(entry.details())).append('"');
            out.append(",\"prevHash\":\"").append(OpsFiles.jsonEscape(entry.prevHash())).append('"');
            out.append(",\"hash\":\"").append(OpsFiles.jsonEscape(entry.hash())).append('"');
            out.append('}');
        }
        return out.append(']').toString();
    }

    /** Walks the chain, checking each stored hash and its link to the predecessor. */
    public ChainStatus verifyChain(List<AuditEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return new ChainStatus(true, -1, "empty chain");
        }
        String previous = "";
        for (int i = 0; i < entries.size(); i++) {
            AuditEntry entry = entries.get(i);
            String expectedPrev = previous;
            if (entry.prevHash() == null || !entry.prevHash().equals(expectedPrev)) {
                return new ChainStatus(false, i, "prev-hash mismatch at index " + i);
            }
            if (entry.hash() == null || !entry.hash().equals(entry.computeHash())) {
                return new ChainStatus(false, i, "hash mismatch at index " + i);
            }
            previous = entry.hash();
        }
        return new ChainStatus(true, -1, "chain intact");
    }

    private static String cell(String value) {
        String safe = value == null ? "" : value;
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static String value(Object object) {
        return object == null ? "" : object.toString();
    }
}
