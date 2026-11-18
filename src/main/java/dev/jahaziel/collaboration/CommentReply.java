/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

/** One reply attached to a file comment. */
public final class CommentReply {
    private final String id;
    private final String commentId;
    private final String author;
    private final String body;
    private final long createdAt;

    public CommentReply(String id, String commentId, String author, String body, long createdAt) {
        this.id = id;
        this.commentId = commentId;
        this.author = author;
        this.body = body;
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public String commentId() {
        return commentId;
    }

    public String author() {
        return author;
    }

    public String body() {
        return body;
    }

    public long createdAt() {
        return createdAt;
    }
}
