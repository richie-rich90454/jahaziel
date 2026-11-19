/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Behavior of {@link SupportBundleService}: sanitization, manifest and containment. */
class SupportBundleServiceTest {
    @TempDir Path temp;

    @Test
    void bundleIsSanitizedAndVerified() throws Exception {
        Path data = temp.resolve("data");
        Path logs = data.resolve("logs");
        Files.createDirectories(logs);
        Files.write(
                logs.resolve("app.jsonl"),
                ("Authorization: Bearer abc.def.ghi\napi_token=supersecret\nmessage=hello\n")
                        .getBytes(StandardCharsets.UTF_8));

        Map<String, String> config = new HashMap<String, String>();
        config.put("host", "127.0.0.1");
        config.put("password", "hunter2");
        Map<String, String> health = new HashMap<String, String>();
        health.put("status", "UP");
        health.put("access_token", "tok-9f8e7d");
        List<String> audit = new ArrayList<String>();
        audit.add("user alice logged in");
        audit.add("jwt=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signaturepart");

        SupportBundleService service = new SupportBundleService(data);
        Path bundle = service.createBundle(config, health, audit);
        assertTrue(OpsFiles.contained(service.supportDir(), bundle));
        assertTrue(service.verify(bundle));

        Map<String, byte[]> entries = ZipUtil.readEntries(bundle);
        String configText = new String(entries.get("config.txt"), StandardCharsets.UTF_8);
        String healthText = new String(entries.get("health.json"), StandardCharsets.UTF_8);
        String auditText = new String(entries.get("audit-tail.txt"), StandardCharsets.UTF_8);
        String logText = new String(entries.get("logs/app.jsonl"), StandardCharsets.UTF_8);
        assertTrue(configText.contains("[REDACTED]"));
        assertFalse(configText.contains("hunter2"));
        assertFalse(healthText.contains("tok-9f8e7d"));
        assertFalse(auditText.contains("eyJhbGci"));
        assertFalse(logText.contains("supersecret"));
        assertFalse(logText.contains("abc.def.ghi"));
        assertTrue(logText.contains("message=hello"));
        assertTrue(entries.containsKey("manifest.sha256"));
    }

    @Test
    void emptyBundleStillHasManifest() {
        SupportBundleService service = new SupportBundleService(temp.resolve("data"));
        Path bundle =
                service.createBundle(
                        new HashMap<String, String>(),
                        new HashMap<String, String>(),
                        new ArrayList<String>());
        assertTrue(service.verify(bundle));
        assertEquals(4, ZipUtil.readEntries(bundle).size());
    }
}
