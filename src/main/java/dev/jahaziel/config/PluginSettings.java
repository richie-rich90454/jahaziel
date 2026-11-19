/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.core.env.Environment;

/** Collects the plugin settings the auth plugins read into the map their context expects. */
public final class PluginSettings {
    private static final String[] KEYS = {
        "basis.auth-issuer", "BASIS_AUTH_ISSUER",
        "basis.client-id", "BASIS_CLIENT_ID",
        "basis.client-secret", "BASIS_CLIENT_SECRET",
        "basis.resource", "BASIS_RESOURCE",
        "basis.resource-secret", "BASIS_RESOURCE_SECRET",
        "basis.internal-url", "BASIS_AUTH_INTERNAL_URL", "BASIS_INTERNAL_URL",
        "basis.scopes", "BASIS_AUTH_SCOPES", "BASIS_SCOPES",
        "basis.offline-access", "BASIS_AUTH_OFFLINE_ACCESS",
        "microsoft.client-id", "MICROSOFT_CLIENT_ID",
        "microsoft.client-secret", "MICROSOFT_CLIENT_SECRET",
        "microsoft.issuer", "MICROSOFT_ISSUER",
        "microsoft.redirect-uri", "MICROSOFT_REDIRECT_URI",
        "microsoft.scopes", "MICROSOFT_SCOPES",
        "google.client-id", "GOOGLE_CLIENT_ID",
        "google.client-secret", "GOOGLE_CLIENT_SECRET",
        "google.issuer", "GOOGLE_ISSUER",
        "google.redirect-uri", "GOOGLE_REDIRECT_URI",
        "google.scopes", "GOOGLE_SCOPES",
        "github.client-id", "GITHUB_CLIENT_ID",
        "github.client-secret", "GITHUB_CLIENT_SECRET",
        "github.scopes", "GITHUB_SCOPES",
        "github.authorize-url", "GITHUB_AUTHORIZE_URL",
        "github.token-url", "GITHUB_TOKEN_URL",
        "github.user-url", "GITHUB_USER_URL",
        "github.emails-url", "GITHUB_EMAILS_URL",
        "oidc.client-id", "OIDC_CLIENT_ID",
        "oidc.client-secret", "OIDC_CLIENT_SECRET",
        "oidc.issuer", "OIDC_ISSUER",
        "oidc.redirect-uri", "OIDC_REDIRECT_URI",
        "oidc.scopes", "OIDC_SCOPES",
        "local.username", "LOCAL_USERNAME",
        "local.password", "LOCAL_PASSWORD",
        "local.email", "LOCAL_EMAIL",
        "passkey.origin", "PASSKEY_ORIGIN",
        "passkey.rp-id", "PASSKEY_RP_ID",
        "passkey.rp-name", "PASSKEY_RP_NAME",
        "api-token.ttl-millis", "API_TOKEN_TTL_MILLIS",
        "api-token.issuer", "API_TOKEN_ISSUER",
        "ldap.url", "LDAP_URL",
        "ldap.base-dn", "LDAP_BASE_DN",
        "ldap.subject-attribute", "LDAP_SUBJECT_ATTRIBUTE",
        "ldap.email-attribute", "LDAP_EMAIL_ATTRIBUTE",
        "ldap.display-name-attribute", "LDAP_DISPLAY_NAME_ATTRIBUTE",
        "ldap.mfa-attribute", "LDAP_MFA_ATTRIBUTE",
        "ldap.mfa-value", "LDAP_MFA_VALUE",
        "ldap.user-filter", "LDAP_USER_FILTER",
        "ldap.bind-dn", "LDAP_BIND_DN",
        "ldap.bind-password", "LDAP_BIND_PASSWORD"
    };

    private PluginSettings() {}

    /** Reads each known key from the Spring environment, keeping only values that are present. */
    public static Map<String, String> from(Environment environment) {
        Map<String, String> settings = new HashMap<String, String>();
        for (int index = 0; index < KEYS.length; index++) {
            String key = KEYS[index];
            String value = environment.getProperty(key);
            if (value != null && value.length() > 0) {
                settings.put(key, value);
            }
        }
        return settings;
    }
}
