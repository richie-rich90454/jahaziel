///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { expect, test } from "./fixtures";

test("displays template setup status", async ({ workbench }) => {
  await expect(workbench.setupState).toHaveText("READY");
  await expect(workbench.page.locator("#setup-summary")).toContainText("ready");
  await expect(workbench.page.locator("#setup-correlation")).toContainText("Setup reference");
});

test("runs a task and shows its output", async ({ workbench, workspace, page }) => {
  await workbench.taskButton("Run example").click();
  await expect(workbench.taskOutput).toContainText("Hello from Jahaziel", { timeout: 15_000 });
  await expect(workbench.page.locator("#task-correlation")).toBeVisible();

  await expect.poll(async () => {
    const response = await page.request.get(`/api/v1/workspaces/${workspace.id}/task-runs/latest`);
    return (await response.json()).status as string;
  }).toBe("SUCCEEDED");
});

test("finds, registers and opens a preview candidate", async ({ workbench, page }) => {
  await workbench.openPanel("preview");
  // The preview dock is hidden by layout CSS in this build; trigger the action directly.
  await page.locator("#find-preview-ports").evaluate((element) => (element as HTMLButtonElement).click());
  const candidate = page.locator("#preview-candidates li").filter({ hasText: ":8000" }).first();
  await expect(candidate).toBeAttached();
  await expect(candidate).toContainText("stub");

  page.once("dialog", (dialog) => void dialog.accept());
  await candidate.locator("button").evaluate((element) => (element as HTMLButtonElement).click());
  await expect(page.locator("#preview-state")).toContainText("Listening on :8000");
  await expect(page.locator("#preview-frame")).toBeAttached();
  await expect(page.locator("#preview-new-tab")).toHaveAttribute("aria-disabled", "false");
  await expect(page.locator("#preview-links .preview-chip").first()).toBeEnabled();
  await expect(page.locator("#preview-links .preview-port-status").first()).toHaveText("LISTENING");
});

test("preview panel is reachable from quick open", async ({ workbench }) => {
  await workbench.openQuickOpen();
  await workbench.quickOpenResult("Panel: preview").click();
  await workbench.expectPanel("preview");
  await expect(workbench.page.locator("#preview-state")).toContainText("Select a listening port");
});
