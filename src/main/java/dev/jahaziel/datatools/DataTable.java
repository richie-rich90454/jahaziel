/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import java.util.ArrayList;
import java.util.List;

/** A bounded, tabular result with column labels and stringified cell values. */
public final class DataTable {
    private final List<String> columns;
    private final List<List<String>> rows;

    public DataTable(List<String> columns, List<List<String>> rows) {
        if (columns == null || rows == null) {
            throw new IllegalArgumentException("columns and rows are required");
        }
        this.columns = new ArrayList<String>(columns);
        this.rows = new ArrayList<List<String>>(rows.size());
        for (List<String> row : rows) {
            this.rows.add(new ArrayList<String>(row));
        }
    }

    public List<String> columns() {
        return new ArrayList<String>(columns);
    }

    public List<List<String>> rows() {
        List<List<String>> copy = new ArrayList<List<String>>(rows.size());
        for (List<String> row : rows) {
            copy.add(new ArrayList<String>(row));
        }
        return copy;
    }

    public int rowCount() {
        return rows.size();
    }
}
