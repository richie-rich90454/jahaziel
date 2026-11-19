/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import java.util.ArrayList;
import java.util.List;

/** A parsed CSV document with detected headers and inferred column types. */
public final class CsvTable {
    private final List<String> headers;
    private final List<String> types;
    private final List<List<String>> rows;
    private final boolean hasHeader;

    public CsvTable(
            List<String> headers, List<String> types, List<List<String>> rows, boolean hasHeader) {
        if (headers == null || types == null || rows == null) {
            throw new IllegalArgumentException("headers, types and rows are required");
        }
        this.headers = new ArrayList<String>(headers);
        this.types = new ArrayList<String>(types);
        this.rows = new ArrayList<List<String>>(rows.size());
        for (List<String> row : rows) {
            this.rows.add(new ArrayList<String>(row));
        }
        this.hasHeader = hasHeader;
    }

    public List<String> headers() {
        return new ArrayList<String>(headers);
    }

    public List<String> types() {
        return new ArrayList<String>(types);
    }

    public List<List<String>> rows() {
        List<List<String>> copy = new ArrayList<List<String>>(rows.size());
        for (List<String> row : rows) {
            copy.add(new ArrayList<String>(row));
        }
        return copy;
    }

    public boolean hasHeader() {
        return hasHeader;
    }

    /** Serializes the table back to CSV, preserving the detected header row when present. */
    public String toCsv() {
        StringBuilder out = new StringBuilder();
        if (hasHeader) {
            appendRow(out, headers);
        }
        for (List<String> row : rows) {
            appendRow(out, row);
        }
        return out.toString();
    }

    private static void appendRow(StringBuilder out, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                out.append(',');
            }
            out.append(escape(values.get(index)));
        }
        out.append('\n');
    }

    private static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
