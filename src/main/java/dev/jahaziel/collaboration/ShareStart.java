/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

/** Result of creating a share link: the id plus the one-time raw token. */
public final class ShareStart {
    private final String shareId;
    private final String token;

    public ShareStart(String shareId, String token) {
        this.shareId = shareId;
        this.token = token;
    }

    public String shareId() {
        return shareId;
    }

    public String token() {
        return token;
    }
}
