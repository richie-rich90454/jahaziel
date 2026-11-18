///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import "@xterm/xterm/css/xterm.css";
import "./app.css";
import { App, bindApp } from "./app";
import { main } from "./lib/controller";
import { render } from "./lib/dom";

const host = document.getElementById("app");
if (host) {
  render(() => App(), host);
  bindApp();
  void main();
}
