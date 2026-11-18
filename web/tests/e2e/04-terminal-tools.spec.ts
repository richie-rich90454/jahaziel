///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { expect, test } from "./fixtures";

test("opens a terminal, accepts input and reports Connected", async ({ workbench }) => {
  await workbench.openPanel("terminal");
  await workbench.page.locator("#terminal-new").click();
  await expect(workbench.terminalState).toHaveText("Connected", { timeout: 20_000 });
  await expect(workbench.page.locator(".xterm-rows")).toBeVisible();
  await expect(workbench.page.locator("#close-terminal")).toBeVisible();

  const marker = `JAH_E2E_${Date.now()}`;
  await workbench.page.locator(".xterm-helper-textarea").click();
  await workbench.page.keyboard.type(`echo ${marker}`);
  await workbench.page.keyboard.press("Enter");
  await expect(workbench.page.locator(".xterm-rows")).toContainText(marker, { timeout: 15_000 });
});

test("resizes the terminal surface and propagates dimensions", async ({ workbench, workspace, page }) => {
  await workbench.page.locator("#terminal-new").click();
  await expect(workbench.terminalState).toHaveText("Connected", { timeout: 20_000 });
  const sessionsUrl = `/api/v1/workspaces/${workspace.id}/terminals`;
  const before = (await (await page.request.get(sessionsUrl)).json()) as Array<{ sessionId: string; rows: number; cols: number }>;
  const session = before.at(-1)!;

  await workbench.page.locator("#terminal-surface").evaluate((surface) => {
    surface.style.width = "560px";
    surface.style.height = "110px";
  });
  await expect.poll(async () => {
    const sessions = (await (await page.request.get(sessionsUrl)).json()) as typeof before;
    const current = sessions.find((entry) => entry.sessionId === session.sessionId);
    return Boolean(current && (current.rows !== session.rows || current.cols !== session.cols));
  }, { timeout: 15_000 }).toBe(true);
});

test("reconnects a terminal after reload and closes it", async ({ workbench, page }) => {
  await workbench.page.locator("#terminal-new").click();
  await expect(workbench.terminalState).toHaveText("Connected", { timeout: 20_000 });
  const firstMarker = `JAH_BEFORE_${Date.now()}`;
  await page.locator(".xterm-helper-textarea").click();
  await page.keyboard.type(`echo ${firstMarker}`);
  await page.keyboard.press("Enter");
  await expect(page.locator(".xterm-rows")).toContainText(firstMarker);

  await page.reload();
  await expect(page.locator("#workbench-page")).toBeVisible();
  await expect(workbench.terminalState).toHaveText("Connected", { timeout: 20_000 });
  const secondMarker = `JAH_AFTER_${Date.now()}`;
  await page.locator(".xterm-helper-textarea").click();
  await page.keyboard.type(`echo ${secondMarker}`);
  await page.keyboard.press("Enter");
  await expect(page.locator(".xterm-rows")).toContainText(secondMarker);

  await page.locator("#close-terminal").click();
  await expect(page.locator("#terminal-reconnect")).toBeHidden();
  await expect(page.locator("#terminal-surface .xterm")).toHaveCount(0);
});

test("shows OpenCode tool status and can launch it", async ({ workbench }) => {
  await workbench.openPanel("terminal");
  await expect(workbench.page.locator("#opencode-status")).toContainText("OpenCode 1.2.3, ready");
  await expect(workbench.page.locator("#opencode-update")).toBeVisible();
  await expect(workbench.page.locator("#opencode-remove")).toBeVisible();

  await workbench.page.locator("#terminal-opencode").click();
  await expect(workbench.terminalState).toHaveText("Connected", { timeout: 20_000 });
});
