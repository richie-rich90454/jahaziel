/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A port to register for a workspace.
 *
 * @param port the port the workspace listens on
 * @param protocol one of http, https, tcp
 */
public final class RegisterPortRequest {

    @NotNull
    @Min(1)
    @Max(65535)
    private final Integer port;

    @NotBlank private final String protocol;

    public RegisterPortRequest(
            @NotNull @Min(1) @Max(65535) Integer port, @NotBlank String protocol) {
        this.port = port;
        this.protocol = protocol;
    }

    public Integer port() {
        return this.port;
    }

    public Integer getPort() {
        return this.port;
    }

    public String protocol() {
        return this.protocol;
    }

    public String getProtocol() {
        return this.protocol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegisterPortRequest)) return false;
        RegisterPortRequest other = (RegisterPortRequest) o;
        return (java.util.Objects.equals(this.port, other.port))
                && java.util.Objects.equals(this.protocol, other.protocol);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.port, this.protocol);
    }

    @Override
    public String toString() {
        return "RegisterPortRequest["
                + "port="
                + this.port
                + ", "
                + "protocol="
                + this.protocol
                + "]";
    }
}
