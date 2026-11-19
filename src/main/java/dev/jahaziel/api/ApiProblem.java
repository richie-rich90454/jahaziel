/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import java.util.UUID;

/**
 * RFC 9457 problem body. Every field is handwritten at the API boundary; raw exception messages and
 * filesystem paths never reach a caller.
 */
public final class ApiProblem {
    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private String correlationId;

    public ApiProblem() {
        this.type = "about:blank";
        this.correlationId = UUID.randomUUID().toString();
    }

    public ApiProblem(String title, int status, String detail, String instance) {
        this.type = "about:blank";
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.correlationId = UUID.randomUUID().toString();
    }

    public String getType() {
        return type;
    }

    public void setType(String value) {
        this.type = value;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String value) {
        this.title = value;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int value) {
        this.status = value;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String value) {
        this.detail = value;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String value) {
        this.instance = value;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String value) {
        this.correlationId = value;
    }
}
