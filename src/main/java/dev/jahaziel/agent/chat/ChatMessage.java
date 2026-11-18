/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.chat;

/** One chat message with the editor context captured when it was sent. */
public final class ChatMessage {
    private String id;
    private String role;
    private String text;
    private String activeFile;
    private String selection;
    private String createdAt;

    public ChatMessage() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getActiveFile() {
        return activeFile;
    }

    public void setActiveFile(String activeFile) {
        this.activeFile = activeFile;
    }

    public String getSelection() {
        return selection;
    }

    public void setSelection(String selection) {
        this.selection = selection;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
