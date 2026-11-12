/* Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import assert from "node:assert/strict";
import test from "node:test";
import { parseDebugLaunchConfigurations } from "../src/debug-launch-config.ts";

function launchFile(...configurations) {
  return JSON.stringify({ version: "0.2.0", configurations });
}

test("parses supported launch configurations with safe defaults", () => {
  const [node, java] = parseDebugLaunchConfigurations(
    launchFile(
      { name: "Node app", type: "pwa-node", request: "launch", program: "src/main.ts", args: ["--watch"] },
      { name: "Java app", type: "java", request: "launch", mainClass: "example.Main", cwd: "server" },
    ),
  );

  assert.deepEqual(node, {
    name: "Node app",
    request: "launch",
    languageId: "node",
    program: "src/main.ts",
    mainClass: null,
    workingDirectory: ".",
    arguments: ["--watch"],
    stopOnEntry: false,
  });
  assert.equal(java.languageId, "java");
  assert.equal(java.mainClass, "example.Main");
  assert.equal(java.workingDirectory, "server");
});

test("accepts server-target attach configurations and rejects arbitrary attach targets", () => {
  const [attach] = parseDebugLaunchConfigurations(
    launchFile({ name: "Attach to node task", type: "pwa-node", request: "attach" }),
  );
  assert.deepEqual(attach, {
    name: "Attach to node task",
    request: "attach",
    languageId: "node",
    program: null,
    mainClass: null,
    workingDirectory: ".",
    arguments: [],
    stopOnEntry: false,
  });
  for (const target of [
    { processId: 1234 },
    { hostName: "example.com", port: 5005 },
    { command: "/bin/sh" },
  ]) {
    assert.throws(
      () => parseDebugLaunchConfigurations(launchFile({ name: "Attach", type: "node", request: "attach", ...target })),
      /attach entries may define only/,
    );
  }
});

test("rejects unsupported launch fields and unknown adapter types", () => {
  assert.throws(
    () => parseDebugLaunchConfigurations(launchFile({ name: "Node", type: "node", request: "launch", program: "app.js", env: { TOKEN: "secret" } })),
    /unsupported fields/,
  );
  assert.throws(
    () => parseDebugLaunchConfigurations(launchFile({ name: "Shell", type: "shell", request: "launch", program: "app.js" })),
    /unsupported debugger type/,
  );
});

test("rejects path escapes and executable-shaped fields", () => {
  for (const program of ["../outside.js", "/etc/passwd", "C:\\outside.js"]) {
    assert.throws(
      () => parseDebugLaunchConfigurations(launchFile({ name: "Node", type: "node", request: "launch", program })),
      /workspace/,
    );
  }
  assert.throws(
    () => parseDebugLaunchConfigurations(launchFile({ name: "Node", type: "node", request: "launch", program: ".", command: "/bin/sh" })),
    /unsupported fields/,
  );
});

test("rejects duplicate names, malformed versions, and oversized documents", () => {
  assert.throws(
    () => parseDebugLaunchConfigurations(launchFile(
      { name: "Same", type: "node", request: "launch", program: "a.js" },
      { name: "Same", type: "node", request: "launch", program: "b.js" },
    )),
    /unique name/,
  );
  assert.throws(
    () => parseDebugLaunchConfigurations(JSON.stringify({ version: "1.0.0", configurations: [] })),
    /version 0.2.0/,
  );
  assert.throws(() => parseDebugLaunchConfigurations(" ".repeat(128 * 1024 + 1)), /128 KB/);
});
