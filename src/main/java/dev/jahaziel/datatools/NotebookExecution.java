/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import java.util.ArrayList;
import java.util.List;

/** The bounded result of one notebook task run. */
public final class NotebookExecution {
    private final String notebookId;
    private final String runId;
    private final List<CellOutput> outputs;

    public NotebookExecution(String notebookId, String runId, List<CellOutput> outputs) {
        if (outputs == null) {
            throw new IllegalArgumentException("outputs are required");
        }
        this.notebookId = notebookId;
        this.runId = runId;
        this.outputs = new ArrayList<CellOutput>(outputs);
    }

    public String notebookId() {
        return notebookId;
    }

    public String runId() {
        return runId;
    }

    public List<CellOutput> outputs() {
        return new ArrayList<CellOutput>(outputs);
    }
}
