/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.firecracker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

/**
 * Firecracker API client speaking raw HTTP/1.1 over the jail Unix socket. One call per
 * configuration step, then start. Refuses to proceed when any step fails.
 */
public final class FirecrackerClient {
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Puts the root drive, network interface, vsock, machine config, and boot source, then starts
   * the instance.
   *
   * @param apiSocket the Firecracker API socket path inside the jail
   * @param rootfs the rootfs image path as seen by Firecracker
   * @param tapName the host TAP device name
   * @param guestMac the guest MAC address
   * @param vcpuCount virtual CPU count
   * @param memoryMib guest memory in MiB
   * @param kernelPath the kernel path as seen by Firecracker
   */
  public void boot(
      String apiSocket,
      String rootfs,
      String tapName,
      String guestMac,
      int vcpuCount,
      long memoryMib,
      String kernelPath) {
    put(apiSocket, "/drives/rootfs", driveBody(rootfs));
    put(apiSocket, "/network-interfaces/eth0", networkBody(tapName, guestMac));
    put(apiSocket, "/vsock", Map.of("guest_cid", 3, "uds_path", "/vsock.sock"));
    put(apiSocket, "/machine-config", machineBody(vcpuCount, memoryMib));
    put(apiSocket, "/boot-source", bootBody(kernelPath));
    put(apiSocket, "/actions", Map.of("action_type", "InstanceStart"));
  }

  /**
   * Powers the instance off.
   *
   * @param apiSocket the Firecracker API socket path inside the jail
   */
  public void shutdown(String apiSocket) {
    put(apiSocket, "/actions", Map.of("action_type", "SendCtrlAltDel"));
  }

  /**
   * Builds a raw HTTP/1.1 PUT request.
   *
   * @param path the request path
   * @param json the JSON body
   * @return the request bytes
   */
  static byte[] putRequest(String path, String json) {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    String head =
        "PUT "
            + path
            + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: "
            + body.length
            + "\r\nConnection: close\r\n\r\n";
    byte[] headBytes = head.getBytes(StandardCharsets.US_ASCII);
    byte[] request = new byte[headBytes.length + body.length];
    System.arraycopy(headBytes, 0, request, 0, headBytes.length);
    System.arraycopy(body, 0, request, headBytes.length, body.length);
    return request;
  }

  /**
   * Reads the status code of a raw HTTP/1.1 response.
   *
   * @param response the response bytes
   * @return the status code
   */
  static int statusOf(byte[] response) {
    String text = new String(response, StandardCharsets.US_ASCII);
    int end = text.indexOf("\r\n");
    if (end < 0) {
      throw new FirecrackerException("<response>", -1, text);
    }
    String[] parts = text.substring(0, end).split(" ");
    if (parts.length < 2) {
      throw new FirecrackerException("<response>", -1, text);
    }
    try {
      return Integer.parseInt(parts[1]);
    } catch (NumberFormatException failure) {
      throw new FirecrackerException("<response>", -1, text);
    }
  }

  /**
   * Builds the drive body.
   *
   * @param rootfs the rootfs path
   * @return the JSON-ready map
   */
  static Map<String, Object> driveBody(String rootfs) {
    return Map.of(
        "drive_id",
        "rootfs",
        "path_on_host",
        rootfs,
        "is_root_device",
        true,
        "is_read_only",
        false,
        "cache_type",
        "Unsafe",
        "io_engine",
        "Async");
  }

  /**
   * Builds the network interface body.
   *
   * @param tapName the host TAP device
   * @param guestMac the guest MAC
   * @return the JSON-ready map
   */
  static Map<String, Object> networkBody(String tapName, String guestMac) {
    return Map.of("iface_id", "eth0", "guest_mac", guestMac, "host_dev_name", tapName);
  }

  /**
   * Builds the machine config body.
   *
   * @param vcpuCount virtual CPU count
   * @param memoryMib guest memory in MiB
   * @return the JSON-ready map
   */
  static Map<String, Object> machineBody(int vcpuCount, long memoryMib) {
    return Map.of(
        "vcpu_count", vcpuCount, "mem_size_mib", memoryMib, "smt", false, "track_dirty_pages",
        false);
  }

  /**
   * Builds the boot source body.
   *
   * @param kernelPath the kernel path
   * @return the JSON-ready map
   */
  static Map<String, Object> bootBody(String kernelPath) {
    return Map.of(
        "kernel_image_path",
        kernelPath,
        "boot_args",
        "console=ttyS0 reboot=k panic=1 pci=off init=/sbin/init root=/dev/vda rw"
            + " init_on_alloc=1 init_on_free=1 page_alloc.shuffle=1");
  }

  private void put(String apiSocket, String path, Object body) {
    String json = writeJson(body);
    byte[] request = putRequest(path, json);
    try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
      channel.connect(UnixDomainSocketAddress.of(apiSocket));
      channel.configureBlocking(true);
      channel.write(ByteBuffer.wrap(request));
      channel.shutdownOutput();
      byte[] response = readAll(channel);
      int status = statusOf(response);
      if (status != 200 && status != 204) {
        throw new FirecrackerException(
            path, status, new String(response, StandardCharsets.UTF_8));
      }
    } catch (IOException failure) {
      throw new FirecrackerException(path, failure);
    }
  }

  private String writeJson(Object body) {
    try {
      return mapper.writeValueAsString(body);
    } catch (tools.jackson.core.JacksonException failure) {
      throw new IllegalArgumentException("cannot encode Firecracker body", failure);
    }
  }

  private static byte[] readAll(SocketChannel channel) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteBuffer buffer = ByteBuffer.allocate(8192);
    int read;
    while ((read = channel.read(buffer)) > 0) {
      out.write(buffer.array(), 0, read);
      buffer.clear();
    }
    return out.toByteArray();
  }
}
