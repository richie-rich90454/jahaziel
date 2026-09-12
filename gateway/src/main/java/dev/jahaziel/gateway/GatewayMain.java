/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway;

import dev.jahaziel.gateway.auth.JwtKeys;
import dev.jahaziel.gateway.auth.JwtVerifier;
import dev.jahaziel.gateway.proxy.GatewayServer;
import dev.jahaziel.gateway.proxy.LoopbackTargetResolver;
import dev.jahaziel.gateway.routing.HostParser;
import dev.jahaziel.gateway.routing.WorkspaceDirectory;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;

/**
 * Gateway entry point. Wires the parser, verifier, directory, and relay, then serves. The directory
 * starts empty; the control plane pushes routes out of band.
 */
public final class GatewayMain {
  private GatewayMain() {}

  /**
   * Starts the gateway.
   *
   * @param args --port, --domain, and --jwt-pub with values
   */
  public static void main(String[] args) {
    int port = integer(args, "--port", 8081);
    String domain = string(args, "--domain", "code.jahaziel.dev");
    Path keyPath = Path.of(string(args, "--jwt-pub", "/etc/jahaziel/tls/jwt.pub"));
    RSAPublicKey key = JwtKeys.loadPublicKey(keyPath);
    JwtVerifier verifier =
        new JwtVerifier(key, "https://code.jahaziel.dev", "jahaziel-code-server");
    GatewayServer server =
        new GatewayServer(
            new HostParser(domain),
            verifier,
            new WorkspaceDirectory(),
            new LoopbackTargetResolver());
    System.out.println("Gateway serving " + domain + " on port " + port);
    server.serve(port);
  }

  private static String string(String[] args, String name, String fallback) {
    for (int i = 0; i + 1 < args.length; i++) {
      if (args[i].equals(name)) {
        return args[i + 1];
      }
    }
    return fallback;
  }

  private static int integer(String[] args, String name, int fallback) {
    for (int i = 0; i + 1 < args.length; i++) {
      if (args[i].equals(name)) {
        return Integer.parseInt(args[i + 1]);
      }
    }
    return fallback;
  }
}
