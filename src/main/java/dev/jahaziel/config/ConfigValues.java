/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.config;

import java.util.ArrayList;
import java.util.List;

/** Converts simple property strings into the collection shapes plain services accept. */
public final class ConfigValues {
    private ConfigValues() {}

    /** Splits a comma-separated property into trimmed, non-empty values. */
    public static List<String> csv(String value) {
        List<String> result = new ArrayList<String>();
        if (value == null) {
            return result;
        }
        String[] parts = value.split(",");
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index].trim();
            if (part.length() > 0) {
                result.add(part);
            }
        }
        return result;
    }

    /** Returns null for an absent or blank property so services keep their own defaults. */
    public static String emptyToNull(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        return value.trim();
    }
}
