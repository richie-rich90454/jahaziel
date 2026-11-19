/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import java.util.Map;

/** Plain request and response bodies for the operations admin endpoints. */
public final class OpsDtos {
    private OpsDtos() {}

    /** A created support bundle; only the file name is exposed. */
    public static final class BundleDto {
        private String fileName;
        private boolean verified;

        public String getFileName() { return fileName; }
        public void setFileName(String value) { this.fileName = value; }
        public boolean isVerified() { return verified; }
        public void setVerified(boolean value) { this.verified = value; }
    }

    /** Backup objectives and newest archive name. */
    public static final class BackupStatusDto {
        private String latest;
        private int count;
        private long rpoMinutes;
        private long rtoMinutes;

        public String getLatest() { return latest; }
        public void setLatest(String value) { this.latest = value; }
        public int getCount() { return count; }
        public void setCount(int value) { this.count = value; }
        public long getRpoMinutes() { return rpoMinutes; }
        public void setRpoMinutes(long value) { this.rpoMinutes = value; }
        public long getRtoMinutes() { return rtoMinutes; }
        public void setRtoMinutes(long value) { this.rtoMinutes = value; }
    }

    /** Optional audit filters. A null field means no constraint. */
    public static final class AuditQueryRequest {
        private String from;
        private String to;
        private String actor;
        private String action;
        private String workspace;
        private String result;
        private String format;

        public String getFrom() { return from; }
        public void setFrom(String value) { this.from = value; }
        public String getTo() { return to; }
        public void setTo(String value) { this.to = value; }
        public String getActor() { return actor; }
        public void setActor(String value) { this.actor = value; }
        public String getAction() { return action; }
        public void setAction(String value) { this.action = value; }
        public String getWorkspace() { return workspace; }
        public void setWorkspace(String value) { this.workspace = value; }
        public String getResult() { return result; }
        public void setResult(String value) { this.result = value; }
        public String getFormat() { return format; }
        public void setFormat(String value) { this.format = value; }
    }

    /** One bounded audit entry view. */
    public static final class AuditDto {
        private String eventId;
        private String timestamp;
        private String actor;
        private String action;
        private String workspace;
        private String result;

        public String getEventId() { return eventId; }
        public void setEventId(String value) { this.eventId = value; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String value) { this.timestamp = value; }
        public String getActor() { return actor; }
        public void setActor(String value) { this.actor = value; }
        public String getAction() { return action; }
        public void setAction(String value) { this.action = value; }
        public String getWorkspace() { return workspace; }
        public void setWorkspace(String value) { this.workspace = value; }
        public String getResult() { return result; }
        public void setResult(String value) { this.result = value; }
    }

    /** An audit export in CSV or JSON. */
    public static final class AuditExportDto {
        private String format;
        private String content;

        public String getFormat() { return format; }
        public void setFormat(String value) { this.format = value; }
        public String getContent() { return content; }
        public void setContent(String value) { this.content = value; }
    }

    /** GDPR operation input. Deletion additionally requires a typed confirmation. */
    public static final class GdprRequest {
        private String userId;
        private String confirmation;

        public String getUserId() { return userId; }
        public void setUserId(String value) { this.userId = value; }
        public String getConfirmation() { return confirmation; }
        public void setConfirmation(String value) { this.confirmation = value; }
    }

    /** A GDPR operation result: a file name for export, a pseudonym for deletion. */
    public static final class GdprDto {
        private String result;

        public GdprDto() {}
        public GdprDto(String value) { this.result = value; }
        public String getResult() { return result; }
        public void setResult(String value) { this.result = value; }
    }

    /** A new approval request. */
    public static final class ApprovalSubmitRequest {
        private String type;
        private String target;
        private Map<String, String> payload;

        public String getType() { return type; }
        public void setType(String value) { this.type = value; }
        public String getTarget() { return target; }
        public void setTarget(String value) { this.target = value; }
        public Map<String, String> getPayload() { return payload; }
        public void setPayload(Map<String, String> value) { this.payload = value; }
    }

    /** One approval request view. */
    public static final class ApprovalDto {
        private String id;
        private String type;
        private String requester;
        private String target;
        private String status;
        private String createdAt;

        public String getId() { return id; }
        public void setId(String value) { this.id = value; }
        public String getType() { return type; }
        public void setType(String value) { this.type = value; }
        public String getRequester() { return requester; }
        public void setRequester(String value) { this.requester = value; }
        public String getTarget() { return target; }
        public void setTarget(String value) { this.target = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { this.status = value; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String value) { this.createdAt = value; }
    }

    /** A decision reason for rejection. */
    public static final class DecisionRequest {
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String value) { this.reason = value; }
    }

    /** Generic status body. */
    public static final class StatusDto {
        private String status;

        public StatusDto() {}
        public StatusDto(String value) { this.status = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { this.status = value; }
    }
}
