/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.debug.internal;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Binds the configured DAP transport name to the allowlisted transport constants. */
@Component
public final class DebugTransportConverter
        implements Converter<String, DebugAdapterProperties.Transport> {
    @Override
    public DebugAdapterProperties.Transport convert(String source) {
        if (source == null || source.trim().isEmpty()) {
            return DebugAdapterProperties.Transport.STDIO;
        }
        String value = source.trim().toUpperCase(Locale.ROOT);
        if ("TCP".equals(value)) {
            return DebugAdapterProperties.Transport.TCP;
        }
        if ("STDIO".equals(value)) {
            return DebugAdapterProperties.Transport.STDIO;
        }
        throw new IllegalArgumentException("unknown debug transport: " + source);
    }
}
