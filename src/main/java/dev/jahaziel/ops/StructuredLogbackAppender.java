/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Logback appender that writes every event through {@link StructuredLogger}, so application logs
 * get the same rotation, day rollover and secret redaction as operational events.
 *
 * <p>Enable it in {@code src/main/resources/logback.xml} (that file is not part of this change):
 *
 * <pre>
 * &lt;appender name="STRUCTURED" class="dev.jahaziel.ops.StructuredLogbackAppender"&gt;
 *   &lt;dataDir&gt;${jahaziel.storage.root:-data}&lt;/dataDir&gt;
 * &lt;/appender&gt;
 * &lt;root level="INFO"&gt;
 *   &lt;appender-ref ref="STRUCTURED"/&gt;
 * &lt;/root&gt;
 * </pre>
 */
public final class StructuredLogbackAppender extends AppenderBase<ILoggingEvent> {
    private String dataDir = "data";
    private StructuredLogger logger;

    /** Creates an appender configured by Logback through {@code setDataDir}. */
    public StructuredLogbackAppender() {}

    /** Creates an appender writing through an existing logger, useful for tests and embedding. */
    public StructuredLogbackAppender(StructuredLogger logger) {
        this.logger = logger;
    }

    /** Sets the data directory used when no logger was supplied. */
    public void setDataDir(String value) {
        if (value != null && !value.trim().isEmpty()) {
            this.dataDir = value.trim();
        }
    }

    @Override
    public void start() {
        if (logger == null) {
            logger = new StructuredLogger(Paths.get(dataDir));
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (logger == null || event == null) {
            return;
        }
        Map<String, String> fields = new HashMap<String, String>();
        fields.put("thread", event.getThreadName());
        fields.put("logger", event.getLoggerName());
        if (event.getThrowableProxy() != null) {
            fields.put("error", event.getThrowableProxy().getMessage());
        }
        logger.log(
                event.getLevel().toString(),
                OpsFiles.sanitizeText(event.getFormattedMessage()),
                fields);
    }
}
