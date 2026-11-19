/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Human approval gate for catalog additions, templates, share-scope elevation and role changes. A
 * request has no effect until approved; every decision is appended to an audit log under {@code
 * data/approvals}.
 */
public final class ApprovalQueue {
    /** Pending status. */
    public static final String PENDING = "PENDING";

    /** Approved status. */
    public static final String APPROVED = "APPROVED";

    /** Rejected status. */
    public static final String REJECTED = "REJECTED";

    /** One approval request. */
    public static final class ApprovalRequest {
        private final String id;
        private final String type;
        private final String requester;
        private final String target;
        private final Map<String, String> payload;
        private final Instant createdAt;
        private String status;

        /** Creates a request. */
        public ApprovalRequest(
                String id,
                String type,
                String requester,
                String target,
                Map<String, String> payload,
                Instant createdAt,
                String status) {
            this.id = id;
            this.type = type;
            this.requester = requester;
            this.target = target;
            this.payload = new HashMap<String, String>(payload);
            this.createdAt = createdAt;
            this.status = status;
        }

        /** Returns the request id. */
        public String id() {
            return id;
        }

        /** Returns the request type. */
        public String type() {
            return type;
        }

        /** Returns the requester. */
        public String requester() {
            return requester;
        }

        /** Returns the target. */
        public String target() {
            return target;
        }

        /** Returns the payload. */
        public Map<String, String> payload() {
            return new HashMap<String, String>(payload);
        }

        /** Returns the creation time. */
        public Instant createdAt() {
            return createdAt;
        }

        /** Returns the status. */
        public String status() {
            return status;
        }

        /** Updates the status. */
        public void status(String value) {
            status = value;
        }
    }

    /** One recorded decision. */
    public static final class Decision {
        private final String requestId;
        private final String action;
        private final String approver;
        private final String reason;

        /** Creates a decision record. */
        public Decision(String requestId, String action, String approver, String reason) {
            this.requestId = requestId;
            this.action = action;
            this.approver = approver;
            this.reason = reason;
        }

        /** Returns the request id. */
        public String requestId() {
            return requestId;
        }

        /** Returns approve or reject. */
        public String action() {
            return action;
        }

        /** Returns the approver. */
        public String approver() {
            return approver;
        }

        /** Returns the reason, or empty. */
        public String reason() {
            return reason;
        }
    }

    private final Path queueDir;
    private final Path queueFile;
    private final Path decisionsFile;
    private final Clock clock;
    private final List<ApprovalRequest> requests = new ArrayList<ApprovalRequest>();
    private final List<Decision> decisions = new ArrayList<Decision>();

    /** Creates a queue using the system clock. */
    public ApprovalQueue(Path dataDir) {
        this(dataDir, Clock.systemUTC());
    }

    /** Creates a queue with an explicit clock. */
    public ApprovalQueue(Path dataDir, Clock clock) {
        if (dataDir == null) {
            throw new IllegalArgumentException("dataDir must not be null");
        }
        this.queueDir = dataDir.toAbsolutePath().normalize().resolve("approvals");
        this.queueFile = queueDir.resolve("queue.psv");
        this.decisionsFile = queueDir.resolve("decisions.jsonl");
        this.clock = clock;
        OpsFiles.ensureDir(queueDir);
        load();
    }

    /** Returns the decisions log path. */
    public Path decisionsFile() {
        return decisionsFile;
    }

    /** Submits a request; it remains pending until approved. */
    public String submit(
            String type, String requester, String target, Map<String, String> payload) {
        if (type == null
                || type.trim().isEmpty()
                || requester == null
                || requester.trim().isEmpty()) {
            throw new IllegalArgumentException("type and requester must not be blank");
        }
        String id = UUID.randomUUID().toString();
        ApprovalRequest request =
                new ApprovalRequest(
                        id,
                        type,
                        requester,
                        target == null ? "" : target,
                        payload == null ? new HashMap<String, String>() : payload,
                        Instant.now(clock),
                        PENDING);
        requests.add(request);
        persist();
        return id;
    }

    /** Approves a pending request. */
    public void approve(String id, String approver) {
        decide(id, approver, APPROVED, "");
    }

    /** Rejects a pending request with a reason. */
    public void reject(String id, String approver, String reason) {
        decide(id, approver, REJECTED, reason == null ? "" : reason);
    }

    /** Returns all requests. */
    public List<ApprovalRequest> requests() {
        return new ArrayList<ApprovalRequest>(requests);
    }

    /** Returns pending requests. */
    public List<ApprovalRequest> pending() {
        List<ApprovalRequest> out = new ArrayList<ApprovalRequest>();
        for (int i = 0; i < requests.size(); i++) {
            if (PENDING.equals(requests.get(i).status())) {
                out.add(requests.get(i));
            }
        }
        return out;
    }

    /** Returns recorded decisions. */
    public List<Decision> decisions() {
        return new ArrayList<Decision>(decisions);
    }

    /** Returns a request by id, or null. */
    public ApprovalRequest find(String id) {
        for (int i = 0; i < requests.size(); i++) {
            if (requests.get(i).id().equals(id)) {
                return requests.get(i);
            }
        }
        return null;
    }

    /**
     * Returns whether a matching request has been approved. This is the gate callers use before
     * applying catalog, template, share or role changes.
     */
    public boolean isApproved(String type, String target) {
        for (int i = 0; i < requests.size(); i++) {
            ApprovalRequest request = requests.get(i);
            if (APPROVED.equals(request.status())
                    && safe(request.type()).equals(safe(type))
                    && safe(request.target()).equals(safe(target))) {
                return true;
            }
        }
        return false;
    }

    private void decide(String id, String approver, String status, String reason) {
        if (approver == null || approver.trim().isEmpty()) {
            throw new IllegalArgumentException("approver must not be blank");
        }
        ApprovalRequest request = find(id);
        if (request == null) {
            throw new IllegalArgumentException("unknown approval request: " + id);
        }
        if (!PENDING.equals(request.status())) {
            throw new IllegalStateException("request is already " + request.status());
        }
        request.status(status);
        decisions.add(new Decision(id, status, approver, reason));
        persist();
        appendDecision(id, status, approver, reason);
    }

    private void persist() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < requests.size(); i++) {
            ApprovalRequest request = requests.get(i);
            out.append(safe(request.id())).append('|');
            out.append(safe(request.type())).append('|');
            out.append(safe(request.requester())).append('|');
            out.append(safe(request.target())).append('|');
            out.append(request.status()).append('|');
            out.append(request.createdAt().toEpochMilli()).append('|');
            Map<String, String> payload = request.payload();
            List<String> keys = OpsFiles.sortedKeys(payload);
            for (int j = 0; j < keys.size(); j++) {
                if (j > 0) {
                    out.append(';');
                }
                out.append(safe(keys.get(j))).append('=').append(safe(payload.get(keys.get(j))));
            }
            out.append('\n');
        }
        OpsFiles.writeText(queueFile, out.toString());
    }

    private void load() {
        String text = OpsFiles.readText(queueFile);
        if (text.isEmpty()) {
            return;
        }
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) {
                continue;
            }
            String[] parts = lines[i].split("\\|", -1);
            if (parts.length < 6) {
                continue;
            }
            Map<String, String> payload = new HashMap<String, String>();
            if (parts.length >= 7 && !parts[6].isEmpty()) {
                String[] pairs = parts[6].split(";", -1);
                for (int j = 0; j < pairs.length; j++) {
                    int equals = pairs[j].indexOf('=');
                    if (equals > 0) {
                        payload.put(pairs[j].substring(0, equals), pairs[j].substring(equals + 1));
                    }
                }
            }
            requests.add(
                    new ApprovalRequest(
                            parts[0],
                            parts[1],
                            parts[2],
                            parts[3],
                            payload,
                            Instant.ofEpochMilli(Long.parseLong(parts[5])),
                            parts[4]));
        }
    }

    private void appendDecision(String id, String status, String approver, String reason) {
        StringBuilder line =
                new StringBuilder("{\"requestId\":\"").append(OpsFiles.jsonEscape(id)).append('"');
        line.append(",\"action\":\"").append(OpsFiles.jsonEscape(status)).append('"');
        line.append(",\"approver\":\"").append(OpsFiles.jsonEscape(approver)).append('"');
        line.append(",\"reason\":\"")
                .append(OpsFiles.jsonEscape(OpsFiles.sanitizeText(reason)))
                .append('"');
        line.append(",\"at\":\"").append(Instant.now(clock).toString()).append("\"}\n");
        try {
            Files.write(
                    decisionsFile,
                    line.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("cannot append approval decision", failure);
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "/").replace(";", ",").replace("=", ":").replace("\n", " ");
    }
}
