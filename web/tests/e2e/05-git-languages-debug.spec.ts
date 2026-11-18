///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { expect, test } from "./fixtures";

test("Git panel refreshes, stages, unstages and commits", async ({ workbench, page }) => {
  await workbench.openPanel("git");
  await expect(workbench.gitState).toHaveText("main · 1");
  await expect(workbench.page.locator("#git-commit-form")).toBeVisible();

  const change = workbench.gitChange("README.md");
  await expect(change).toBeVisible();
  await change.locator(".git-path").click();
  await expect(workbench.page.locator("#git-diff")).toBeVisible();
  await expect(workbench.page.locator("#git-diff")).toContainText("Changes from the E2E stub");

  await change.getByRole("button", { name: "Stage" }).click();
  await expect(change.getByRole("button", { name: "Unstage" })).toBeVisible();
  await change.getByRole("button", { name: "Unstage" }).click();
  await expect(change.getByRole("button", { name: "Stage" })).toBeVisible();
  await change.getByRole("button", { name: "Stage" }).click();

  await page.locator("#git-message").fill("Exercise Git panel");
  await expect(page.locator("#git-commit")).toBeEnabled();
  await page.locator("#git-commit").click();
  await expect(page.locator("#git-changes")).toContainText("Working tree clean.");
  await expect(page.locator("#git-message")).toHaveText("");
});

test("language packs and diagnostics render", async ({ workbench }) => {
  await workbench.openPanel("languages");
  const pyright = workbench.languagePack("Pyright");
  await expect(pyright).toBeVisible();
  await expect(pyright.locator(".language-pack-action")).toHaveText("Enable");
  await pyright.locator(".language-pack-action").click();

  await workbench.openFile("main.py");
  await expect(workbench.languageState).toContainText("python language service ready", { timeout: 20_000 });
  const diagnostic = workbench.page.locator("#language-diagnostics .language-diagnostic-error").first();
  await expect(diagnostic).toBeVisible({ timeout: 20_000 });
  await expect(diagnostic).toContainText("E2E stub diagnostic on line 1");
  await expect(diagnostic).toContainText("main.py");
});

test("debugger loads launch config, sets a breakpoint, launches and stops", async ({ workbench }) => {
  await workbench.openPanel("debug");
  await workbench.page.locator("#debug-load-launch-config").click();
  await expect(workbench.page.locator("#debug-launch-config")).toBeVisible();
  await expect(workbench.page.locator("#debug-launch-config option")).toContainText(["Python: main.py"]);
  await expect(workbench.page.locator("#debug-launch-config-status")).toContainText("supported launch configuration");

  await workbench.page.locator("#debug-start").click();
  await expect(workbench.debugState).toHaveText("RUNNING", { timeout: 20_000 });
  await expect(workbench.page.locator("#debug-stop")).toBeEnabled();

  // Launch resets the breakpoint fields to the active document.
  await workbench.page.locator("#debug-breakpoint-path").fill("main.py");
  await workbench.page.locator("#debug-breakpoint-lines").fill("1");
  await expect(workbench.page.locator("#debug-apply-breakpoints")).toBeEnabled();
  await workbench.page.locator("#debug-apply-breakpoints").click();
  await expect(workbench.page.locator("#debug-breakpoint-list")).toContainText("main.py: 1");

  await workbench.page.locator("#debug-stop").click();
  await expect(workbench.debugState).toHaveText("NOT RUNNING", { timeout: 20_000 });
  await expect(workbench.page.locator("#debug-stop")).toBeDisabled();
});

test("workbench layout adapts to narrow viewports", async ({ workbench }) => {
  await expect(workbench.root).toBeVisible();
  for (const width of [1440, 900, 600, 390]) {
    await workbench.setViewport(width, 860);
    for (const panel of ["files", "run", "git", "languages", "debug", "terminal", "preview"]) {
      await workbench.openPanel(panel);
      await expect(workbench.root).toBeVisible();
    }
    await workbench.openPanel("files");
  }
});
