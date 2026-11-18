/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.ldap;

import dev.jahaziel.identity.auth.AuthIdentity;

/** Directory access seam. Abstract class so tests can supply an in-memory fake. */
public abstract class LdapGateway {
    public abstract AuthIdentity authenticate(LdapConfig config, String username, String password);
}
