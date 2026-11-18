/* Copyright 2026 The Jahaziel Authors. SPDX-License-Identifier: Apache-2.0. */
"use strict";

const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

module.exports = async function globalTeardown() {
  // Do not remove the Playwright results dir: the reporter/trace writer may
  // still be flushing attachments after global teardown has completed.
  for (const name of ["jahaziel-e2e-scratch"]) {
    const target = path.join(os.tmpdir(), name);
    if (path.dirname(target) === os.tmpdir() && path.basename(target).startsWith("jahaziel-e2e-")) {
      fs.rmSync(target, { recursive: true, force: true });
    }
  }
};
