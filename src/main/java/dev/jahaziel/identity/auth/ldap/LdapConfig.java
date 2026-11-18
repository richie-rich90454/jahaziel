/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.ldap;

import dev.jahaziel.plugin.PluginContext;

import java.nio.file.Path;

/** Settings for the LDAP provider, validated once at initialization. */
public final class LdapConfig {
    private final String url;
    private final String baseDn;
    private final String subjectAttribute;
    private final String emailAttribute;
    private final String displayNameAttribute;
    private final String mfaAttribute;
    private final String mfaValue;
    private final String userFilter;
    private final String bindDn;
    private final String bindPassword;
    private final Path storeFile;

    public LdapConfig(PluginContext context) {
        this.url = required(context, "missing LDAP url", "ldap.url", "LDAP_URL");
        if (!url.startsWith("ldap://") && !url.startsWith("ldaps://")) {
            throw new LdapException("LDAP url must start with ldap:// or ldaps://");
        }
        this.baseDn = required(context, "missing LDAP base DN", "ldap.base-dn", "LDAP_BASE_DN");
        String subject = first(context, "ldap.subject-attribute", "LDAP_SUBJECT_ATTRIBUTE");
        this.subjectAttribute = subject == null ? "entryUUID" : subject;
        String email = first(context, "ldap.email-attribute", "LDAP_EMAIL_ATTRIBUTE");
        this.emailAttribute = email == null ? "mail" : email;
        String display =
                first(context, "ldap.display-name-attribute", "LDAP_DISPLAY_NAME_ATTRIBUTE");
        this.displayNameAttribute = display == null ? "displayName" : display;
        this.mfaAttribute = first(context, "ldap.mfa-attribute", "LDAP_MFA_ATTRIBUTE");
        String expected = first(context, "ldap.mfa-value", "LDAP_MFA_VALUE");
        this.mfaValue = expected == null ? "true" : expected;
        String filter = first(context, "ldap.user-filter", "LDAP_USER_FILTER");
        this.userFilter = filter == null ? "(uid={0})" : filter;
        this.bindDn = first(context, "ldap.bind-dn", "LDAP_BIND_DN");
        this.bindPassword = first(context, "ldap.bind-password", "LDAP_BIND_PASSWORD");
        this.storeFile = context.dataDir().resolve("ldap").resolve("ldap-store.json");
    }

    public String url() {
        return url;
    }

    public String baseDn() {
        return baseDn;
    }

    public String subjectAttribute() {
        return subjectAttribute;
    }

    public String emailAttribute() {
        return emailAttribute;
    }

    public String displayNameAttribute() {
        return displayNameAttribute;
    }

    public String mfaAttribute() {
        return mfaAttribute;
    }

    public String mfaValue() {
        return mfaValue;
    }

    public String userFilter() {
        return userFilter;
    }

    public String bindDn() {
        return bindDn;
    }

    public String bindPassword() {
        return bindPassword;
    }

    public Path storeFile() {
        return storeFile;
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
            throw new LdapException(message);
        }
        return value;
    }
}
