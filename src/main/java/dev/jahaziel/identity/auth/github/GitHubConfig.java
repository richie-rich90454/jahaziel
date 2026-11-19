/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.github;

import dev.jahaziel.plugin.PluginContext;

import java.nio.file.Path;

/** Settings for the GitHub provider, read once at initialization. */
public final class GitHubConfig {
    private static final String DEFAULT_SCOPES = "read:user user:email";
    private static final String DEFAULT_AUTHORIZE = "https://github.com/login/oauth/authorize";
    private static final String DEFAULT_TOKEN = "https://github.com/login/oauth/access_token";
    private static final String DEFAULT_USER = "https://api.github.com/user";
    private static final String DEFAULT_EMAILS = "https://api.github.com/user/emails";

    private final String clientId;
    private final String clientSecret;
    private final String scopes;
    private final String authorizeUrl;
    private final String tokenUrl;
    private final String userUrl;
    private final String emailsUrl;
    private final Path identityDir;
    private final Path keysDir;

    public GitHubConfig(PluginContext context) {
        this.clientId =
                required(
                        context,
                        "missing github client id",
                        "github.client-id",
                        "GITHUB_CLIENT_ID");
        this.clientSecret = first(context, "github.client-secret", "GITHUB_CLIENT_SECRET");
        this.scopes = orDefault(first(context, "github.scopes", "GITHUB_SCOPES"), DEFAULT_SCOPES);
        this.authorizeUrl =
                orDefault(
                        first(context, "github.authorize-url", "GITHUB_AUTHORIZE_URL"),
                        DEFAULT_AUTHORIZE);
        this.tokenUrl =
                orDefault(first(context, "github.token-url", "GITHUB_TOKEN_URL"), DEFAULT_TOKEN);
        this.userUrl =
                orDefault(first(context, "github.user-url", "GITHUB_USER_URL"), DEFAULT_USER);
        this.emailsUrl =
                orDefault(first(context, "github.emails-url", "GITHUB_EMAILS_URL"), DEFAULT_EMAILS);
        this.identityDir = context.dataDir().resolve("identity");
        this.keysDir = context.dataDir().resolve("keys");
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    public String scopes() {
        return scopes;
    }

    public String authorizeUrl() {
        return authorizeUrl;
    }

    public String tokenUrl() {
        return tokenUrl;
    }

    public String userUrl() {
        return userUrl;
    }

    public String emailsUrl() {
        return emailsUrl;
    }

    public Path identityDir() {
        return identityDir;
    }

    public Path keysDir() {
        return keysDir;
    }

    private static String first(PluginContext context, String... keys) {
        for (int index = 0; index < keys.length; index++) {
            String value = context.setting(keys[index]);
            if (value != null && value.length() > 0) {
                return value;
            }
        }
        return null;
    }

    private static String required(PluginContext context, String message, String... keys) {
        String value = first(context, keys);
        if (value == null) {
            throw new GitHubException(message);
        }
        return value;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }
}
