/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses bounded CSV text into rows with header detection and column type inference. */
public final class CsvReader {
    public static final int MAX_ROWS = 1000;
    public static final long MAX_BYTES = 8L * 1024 * 1024;

    public CsvTable read(String content) {
        return read(content, MAX_ROWS);
    }

    public CsvTable read(String content, int maxRows) {
        if (maxRows < 1 || maxRows > MAX_ROWS) {
            throw new IllegalArgumentException("maxRows must be between 1 and " + MAX_ROWS);
        }
        if (content == null) {
            throw new IllegalArgumentException("content is required");
        }
        if (content.length() > MAX_BYTES) {
            throw new IllegalArgumentException("csv content is too large");
        }
        List<List<String>> records = parse(content, maxRows + 1);
        if (records.isEmpty()) {
            return new CsvTable(
                    new ArrayList<String>(),
                    new ArrayList<String>(),
                    new ArrayList<List<String>>(),
                    false);
        }
        boolean hasHeader = looksLikeHeader(records.get(0));
        List<List<String>> data = new ArrayList<List<String>>();
        List<String> headers = new ArrayList<String>();
        if (hasHeader) {
            headers.addAll(records.get(0));
            for (int index = 1; index < records.size() && data.size() < maxRows; index++) {
                data.add(records.get(index));
            }
        } else {
            int width = records.get(0).size();
            for (int column = 1; column <= width; column++) {
                headers.add("column_" + column);
            }
            for (int index = 0; index < records.size() && data.size() < maxRows; index++) {
                data.add(records.get(index));
            }
        }
        List<List<String>> normalized = new ArrayList<List<String>>(data.size());
        for (List<String> row : data) {
            List<String> padded = new ArrayList<String>(row);
            while (padded.size() < headers.size()) {
                padded.add("");
            }
            normalized.add(padded);
        }
        return new CsvTable(headers, inferTypes(normalized, headers.size()), normalized, hasHeader);
    }

    private static List<List<String>> parse(String content, int maxRecords) {
        List<List<String>> records = new ArrayList<List<String>>();
        List<String> current = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int index = 0;
        while (index < content.length() && records.size() < maxRecords) {
            char character = content.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < content.length() && content.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == ',') {
                current.add(field.toString());
                field.setLength(0);
            } else if (character == '\n') {
                current.add(field.toString());
                field.setLength(0);
                records.add(current);
                current = new ArrayList<String>();
            } else if (character != '\r') {
                field.append(character);
            }
            index++;
        }
        if ((field.length() > 0 || !current.isEmpty()) && records.size() < maxRecords) {
            current.add(field.toString());
            records.add(current);
        }
        return records;
    }

    private static boolean looksLikeHeader(List<String> first) {
        if (first.isEmpty()) {
            return false;
        }
        for (String cell : first) {
            if (cell == null || cell.trim().isEmpty() || isNumber(cell) || isBoolean(cell)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> inferTypes(List<List<String>> rows, int width) {
        List<String> types = new ArrayList<String>();
        for (int column = 0; column < width; column++) {
            boolean allInteger = true;
            boolean allNumber = true;
            boolean allBoolean = true;
            for (List<String> row : rows) {
                String value = column < row.size() ? row.get(column) : "";
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                if (!isInteger(value)) {
                    allInteger = false;
                }
                if (!isNumber(value)) {
                    allNumber = false;
                }
                if (!isBoolean(value)) {
                    allBoolean = false;
                }
            }
            if (allInteger) {
                types.add("INTEGER");
            } else if (allNumber) {
                types.add("REAL");
            } else if (allBoolean) {
                types.add("BOOLEAN");
            } else {
                types.add("TEXT");
            }
        }
        return types;
    }

    private static boolean isInteger(String value) {
        try {
            Long.parseLong(value.trim());
            return true;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static boolean isNumber(String value) {
        try {
            Double.parseDouble(value.trim());
            return true;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static boolean isBoolean(String value) {
        String text = value.trim().toLowerCase(Locale.ROOT);
        return "true".equals(text)
                || "false".equals(text)
                || "yes".equals(text)
                || "no".equals(text);
    }
}
