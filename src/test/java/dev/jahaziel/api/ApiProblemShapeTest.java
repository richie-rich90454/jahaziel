/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** RFC 9457 fields and the browser-compatible aliases stay in sync. */
class ApiProblemShapeTest {
    @Test
    void messageAndCodeAliasDetailAndType() {
        ApiProblem problem =
                new ApiProblem("Bad Request", 400, "The request contains an invalid value", "/x");

        assertEquals(problem.getDetail(), problem.getMessage());
        assertEquals(problem.getType(), problem.getCode());
        assertNotNull(problem.getCorrelationId());
    }

    @Test
    void settersKeepAliasesInSync() {
        ApiProblem problem = new ApiProblem();
        problem.setMessage("fixed message");
        problem.setCode("VALIDATION_FAILED");

        assertEquals("fixed message", problem.getDetail());
        assertEquals("VALIDATION_FAILED", problem.getType());
    }
}
