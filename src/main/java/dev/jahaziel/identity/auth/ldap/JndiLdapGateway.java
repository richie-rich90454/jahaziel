/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.ldap;

import dev.jahaziel.identity.auth.AuthIdentity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

/** JNDI bind authentication against the configured directory. No third-party dependencies. */
public final class JndiLdapGateway extends LdapGateway {
    @Override
    public AuthIdentity authenticate(LdapConfig config, String username, String password) {
        if (username == null
                || username.length() == 0
                || password == null
                || password.length() == 0) {
            throw new LdapException("invalid credentials");
        }
        List<String> names = new ArrayList<String>();
        names.add(config.subjectAttribute());
        names.add(config.emailAttribute());
        names.add(config.displayNameAttribute());
        if (config.mfaAttribute() != null) {
            names.add(config.mfaAttribute());
        }
        String dn = null;
        Map<String, String> attributes = new HashMap<String, String>();
        DirContext search = connect(config, config.bindDn(), config.bindPassword());
        NamingEnumeration<SearchResult> results = null;
        try {
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setReturningAttributes(names.toArray(new String[names.size()]));
            String filter = config.userFilter().replace("{0}", escape(username));
            results = search.search(config.baseDn(), filter, controls);
            if (results.hasMore()) {
                SearchResult result = results.next();
                dn = result.getNameInNamespace();
                Attributes found = result.getAttributes();
                for (int index = 0; index < names.size(); index++) {
                    String name = names.get(index);
                    attributes.put(name, value(found, name));
                }
            }
        } catch (NamingException failure) {
            throw new LdapException("LDAP search failed", failure);
        } finally {
            close(results);
            close(search);
        }
        if (dn == null) {
            throw new LdapException("invalid credentials");
        }
        DirContext user = null;
        try {
            user = connect(config, dn, password);
        } catch (LdapException failure) {
            throw new LdapException("invalid credentials");
        } finally {
            close(user);
        }
        return LdapIdentityMapper.map(config, attributes, dn);
    }

    private static DirContext connect(LdapConfig config, String dn, String password) {
        Hashtable<String, String> environment = new Hashtable<String, String>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, config.url());
        environment.put(Context.SECURITY_AUTHENTICATION, "simple");
        environment.put(Context.SECURITY_PRINCIPAL, dn == null ? "" : dn);
        environment.put(Context.SECURITY_CREDENTIALS, password == null ? "" : password);
        try {
            return new InitialDirContext(environment);
        } catch (NamingException failure) {
            throw new LdapException("LDAP bind failed", failure);
        }
    }

    private static String value(Attributes attributes, String name) {
        Attribute attribute = attributes == null ? null : attributes.get(name);
        if (attribute == null) {
            return null;
        }
        try {
            Object value = attribute.get();
            return value == null ? null : String.valueOf(value);
        } catch (NamingException failure) {
            return null;
        }
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\') {
                escaped.append("\\5c");
            } else if (character == '*') {
                escaped.append("\\2a");
            } else if (character == '(') {
                escaped.append("\\28");
            } else if (character == ')') {
                escaped.append("\\29");
            } else if (character == '\u0000') {
                escaped.append("\\00");
            } else {
                escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private static void close(DirContext context) {
        if (context == null) {
            return;
        }
        try {
            context.close();
        } catch (NamingException ignored) {
            // Releasing a connection must not mask the authentication result.
        }
    }

    private static void close(NamingEnumeration<?> results) {
        if (results == null) {
            return;
        }
        try {
            results.close();
        } catch (NamingException ignored) {
            // Releasing an enumeration must not mask the authentication result.
        }
    }
}
