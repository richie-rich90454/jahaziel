/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.ldap;

import dev.jahaziel.identity.auth.AuthIdentity;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps directory attributes to a stable identity. MFA is claimed only when configured and proven.
 */
public final class LdapIdentityMapper {
    private LdapIdentityMapper() {}

    public static AuthIdentity map(LdapConfig config, Map<String, String> attributes, String dn) {
        String subject =
                first(attributes.get(config.subjectAttribute()), attributes.get("objectGUID"), dn);
        if (subject == null || subject.length() == 0) {
            throw new LdapException("LDAP entry has no subject attribute");
        }
        String email = value(attributes, config.emailAttribute());
        String displayName = value(attributes, config.displayNameAttribute());
        boolean mfa = false;
        String attribute = config.mfaAttribute();
        if (attribute != null) {
            String observed = attributes.get(attribute);
            mfa = observed != null && observed.trim().equalsIgnoreCase(config.mfaValue());
        }
        Map<String, String> claims = new HashMap<String, String>();
        claims.put("subject_attribute", config.subjectAttribute());
        if (attribute != null) {
            claims.put("mfa_attribute", attribute);
        }
        return new AuthIdentity("ldap", subject, email, displayName, false, mfa, claims);
    }

    private static String value(Map<String, String> attributes, String name) {
        String found = attributes.get(name);
        return found == null ? "" : found;
    }

    private static String first(String... candidates) {
        for (int index = 0; index < candidates.length; index++) {
            String candidate = candidates[index];
            if (candidate != null && candidate.length() > 0) {
                return candidate;
            }
        }
        return null;
    }
}
