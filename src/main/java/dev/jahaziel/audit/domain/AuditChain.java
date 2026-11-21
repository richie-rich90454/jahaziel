/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.domain;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;

/**
 * Single source of truth for the audit hash chain. Every persisted field that can change the
 * meaning of an event is part of the digest, and verification re-derives each link from those
 * fields instead of trusting a stored hash.
 */
public final class AuditChain {
    /** Sentinel used for the hash of the first event in the chain. */
    public static final String GENESIS = "genesis";

    private static final char SEPARATOR = '\u001f';
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AuditChain() {}

    /**
     * Computes the digest of one event over its canonical fields.
     *
     * @return lowercase hex SHA-256
     */
    public static String hash(
            String eventId,
            String timestamp,
            String actor,
            String actorType,
            String action,
            String target,
            String targetType,
            String outcome,
            String details,
            String sourceIp,
            String prevHash) {
        String canonical =
                join(eventId)
                        + SEPARATOR
                        + join(timestamp)
                        + SEPARATOR
                        + join(actor)
                        + SEPARATOR
                        + join(actorType)
                        + SEPARATOR
                        + join(action)
                        + SEPARATOR
                        + join(target)
                        + SEPARATOR
                        + join(targetType)
                        + SEPARATOR
                        + join(outcome)
                        + SEPARATOR
                        + join(details)
                        + SEPARATOR
                        + join(sourceIp)
                        + SEPARATOR
                        + join(prevHash);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    /**
     * Verifies a chain in chronological order. The first event must link to {@link #GENESIS}; every
     * later event must link to the previous digest. An empty chain is never intact.
     *
     * @param records the events, in any order
     * @return true only when every link and every stored digest is consistent
     */
    public static boolean verify(List<AuditRecord> records) {
        if (records == null || records.isEmpty()) {
            return false;
        }
        List<AuditRecord> ordered = new ArrayList<AuditRecord>(records);
        ordered.sort((left, right) -> left.timestamp().compareTo(right.timestamp()));
        String previous = GENESIS;
        for (int i = 0; i < ordered.size(); i++) {
            AuditRecord record = ordered.get(i);
            String expectedPrev = i == 0 ? "" : previous;
            if (record.prevHash().orElse("") == null
                    || !expectedPrev.equals(record.prevHash().orElse(""))) {
                return false;
            }
            String expected =
                    hash(
                            record.eventId().toString(),
                            record.timestamp().toString(),
                            record.actor(),
                            record.actorType().name(),
                            record.action(),
                            record.target().orElse(""),
                            record.targetType().orElse(""),
                            record.outcome().name(),
                            detailsJson(record),
                            record.sourceIp().orElse(""),
                            previous);
            if (!record.hash().equalsIgnoreCase(expected)) {
                return false;
            }
            previous = record.hash();
        }
        return true;
    }

    /** Returns the stable JSON rendering of a record's details used by the digest. */
    public static String detailsJson(AuditRecord record) {
        try {
            return MAPPER.writeValueAsString(new TreeMap<String, String>(record.details()));
        } catch (JacksonException failure) {
            throw new IllegalStateException("Audit details cannot be serialized", failure);
        }
    }

    private static String join(String value) {
        return value == null ? "" : value;
    }
}
