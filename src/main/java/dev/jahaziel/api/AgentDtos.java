/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

/** Plain request and response bodies for the agent endpoints. No records and no validation logic. */
public final class AgentDtos {
    private AgentDtos() {}

    /** Generic agent request; unused fields stay null. */
    public static final class AgentRequest {
        private String name;
        private String text;
        private String line;
        private String file;
        private String selection;
        private String patch;
        private int hunkIndex;
        private long tokensIn;
        private long tokensOut;

        public String getName() { return name; }
        public void setName(String value) { this.name = value; }
        public String getText() { return text; }
        public void setText(String value) { this.text = value; }
        public String getLine() { return line; }
        public void setLine(String value) { this.line = value; }
        public String getFile() { return file; }
        public void setFile(String value) { this.file = value; }
        public String getSelection() { return selection; }
        public void setSelection(String value) { this.selection = value; }
        public String getPatch() { return patch; }
        public void setPatch(String value) { this.patch = value; }
        public int getHunkIndex() { return hunkIndex; }
        public void setHunkIndex(int value) { this.hunkIndex = value; }
        public long getTokensIn() { return tokensIn; }
        public void setTokensIn(long value) { this.tokensIn = value; }
        public long getTokensOut() { return tokensOut; }
        public void setTokensOut(long value) { this.tokensOut = value; }
    }

    /** One agent session view. */
    public static final class SessionDto {
        private String id;
        private String name;
        private String status;
        private String workspaceId;

        public String getId() { return id; }
        public void setId(String value) { this.id = value; }
        public String getName() { return name; }
        public void setName(String value) { this.name = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { this.status = value; }
        public String getWorkspaceId() { return workspaceId; }
        public void setWorkspaceId(String value) { this.workspaceId = value; }
    }

    /** One chat message view. */
    public static final class MessageDto {
        private String id;
        private String role;
        private String text;
        private String activeFile;
        private String createdAt;

        public String getId() { return id; }
        public void setId(String value) { this.id = value; }
        public String getRole() { return role; }
        public void setRole(String value) { this.role = value; }
        public String getText() { return text; }
        public void setText(String value) { this.text = value; }
        public String getActiveFile() { return activeFile; }
        public void setActiveFile(String value) { this.activeFile = value; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String value) { this.createdAt = value; }
    }

    /** One prompt template view. */
    public static final class PromptDto {
        private String id;
        private String name;
        private String body;
        private String owner;

        public String getId() { return id; }
        public void setId(String value) { this.id = value; }
        public String getName() { return name; }
        public void setName(String value) { this.name = value; }
        public String getBody() { return body; }
        public void setBody(String value) { this.body = value; }
        public String getOwner() { return owner; }
        public void setOwner(String value) { this.owner = value; }
    }

    /** One staged patch view. */
    public static final class PatchDto {
        private String id;
        private String filePath;
        private String status;

        public String getId() { return id; }
        public void setId(String value) { this.id = value; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String value) { this.filePath = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { this.status = value; }
    }

    /** One allowlisted model view. */
    public static final class ModelDto {
        private String id;
        private String capability;

        public String getId() { return id; }
        public void setId(String value) { this.id = value; }
        public String getCapability() { return capability; }
        public void setCapability(String value) { this.capability = value; }
    }

    /** One usage snapshot view. */
    public static final class UsageDto {
        private String userId;
        private String workspaceId;
        private long tokensIn;
        private long tokensOut;
        private long messages;

        public String getUserId() { return userId; }
        public void setUserId(String value) { this.userId = value; }
        public String getWorkspaceId() { return workspaceId; }
        public void setWorkspaceId(String value) { this.workspaceId = value; }
        public long getTokensIn() { return tokensIn; }
        public void setTokensIn(long value) { this.tokensIn = value; }
        public long getTokensOut() { return tokensOut; }
        public void setTokensOut(long value) { this.tokensOut = value; }
        public long getMessages() { return messages; }
        public void setMessages(long value) { this.messages = value; }
    }

    /** Rendered prompt text. */
    public static final class RenderedDto {
        private String rendered;

        public String getRendered() { return rendered; }
        public void setRendered(String value) { this.rendered = value; }
    }

    /** Generic status body for cancel, resume, kill and similar actions. */
    public static final class StatusDto {
        private String status;

        public StatusDto() {}
        public StatusDto(String value) { this.status = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { this.status = value; }
    }
}
