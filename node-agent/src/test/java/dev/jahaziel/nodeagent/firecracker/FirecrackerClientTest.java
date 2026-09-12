/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.firecracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Behavior of {@link FirecrackerClient} request building and response parsing. */
class FirecrackerClientTest {
  @Test
  void putRequestCarriesLength() {
    byte[] request = FirecrackerClient.putRequest("/actions", "{\"a\":1}");
    String text = new String(request, StandardCharsets.US_ASCII);
    assertTrue(text.startsWith("PUT /actions HTTP/1.1\r\n"));
    assertTrue(text.contains("Content-Length: 7\r\n"));
    assertTrue(text.endsWith("{\"a\":1}"));
  }

  @Test
  void statusParses() {
    assertEquals(
        204,
        FirecrackerClient.statusOf(
            "HTTP/1.1 204 No Content\r\n\r\n".getBytes(StandardCharsets.US_ASCII)));
    assertEquals(
        400,
        FirecrackerClient.statusOf(
            "HTTP/1.1 400 Bad Request\r\n\r\n{}".getBytes(StandardCharsets.US_ASCII)));
    assertThrows(FirecrackerException.class, () -> FirecrackerClient.statusOf(new byte[0]));
  }

  @Test
  void bodiesCarryConfig() {
    assertEquals(2, FirecrackerClient.machineBody(2, 2048).get("vcpu_count"));
    assertEquals(2048L, FirecrackerClient.machineBody(2, 2048).get("mem_size_mib"));
    assertEquals(
        "eth0", FirecrackerClient.networkBody("tap-1", "AA:FC:00:00:00:01").get("iface_id"));
    Map<String, Object> drive = FirecrackerClient.driveBody("/rootfs.img");
    assertEquals("/rootfs.img", drive.get("path_on_host"));
    assertEquals(true, drive.get("is_root_device"));
    assertEquals(true, drive.get("is_read_only"));
    assertTrue(FirecrackerClient.bootBody("/kernel").get("boot_args").toString().contains(" ro "));
  }
}
