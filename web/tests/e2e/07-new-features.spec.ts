///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { expect, test } from "./fixtures";

test("theme switching persists in localStorage across a reload", async ({ workbench, page }) => {
  await expect(workbench.themeSelect()).toBeVisible();

  await workbench.themeSelect().selectOption("dark");
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  expect(await page.evaluate(() => localStorage.getItem("jahaziel:theme"))).toBe("dark");

  await workbench.themeSelect().selectOption("high-contrast");
  await expect(page.locator("html")).toHaveAttribute("data-theme", "high-contrast");
  expect(await page.evaluate(() => localStorage.getItem("jahaziel:theme"))).toBe("high-contrast");

  await page.reload();
  await expect(page.locator("#workbench-page")).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "high-contrast");
});

test("locale switching updates the lang attribute and persists", async ({ home, page }) => {
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await home.localeSelect().selectOption("es");
  await expect(page.locator("html")).toHaveAttribute("lang", "es");
  expect(await page.evaluate(() => localStorage.getItem("jahaziel:locale"))).toBe("es");

  await page.reload();
  await expect(page.locator("#home-page")).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("lang", "es");
});

test("onboarding tour appears and can be skipped", async ({ home, page }) => {
  await expect(home.tour()).toBeVisible();
  await expect(home.tourProgress()).toHaveText("Step 1 of 4");
  await expect(home.tourSkip()).toBeVisible();

  await home.tourSkip().click();
  await expect(home.tour()).toBeHidden();
  expect(await page.evaluate(() => localStorage.getItem("jahaziel:onboarding"))).toBe("done");

  await page.reload();
  await expect(page.locator("#home-page")).toBeVisible();
  await expect(home.tour()).toBeHidden();
});

test("problems panel renders language-service diagnostics", async ({ workbench, page }) => {
  await expect(workbench.problemsList()).toContainText("No problems in open documents.");

  await workbench.openPanel("languages");
  await workbench.languagePack("Pyright").locator(".language-pack-action").click();

  await workbench.openFile("main.py");
  await expect(page.locator("#language-diagnostics .language-diagnostic-error").first()).toBeVisible({
    timeout: 20_000,
  });

  const entry = workbench.problemsList().locator(".problem-entry").first();
  await expect(entry).toBeVisible({ timeout: 20_000 });
  await expect(entry).toContainText("E2E stub diagnostic on line 1");
  await expect(entry.locator(".problem-location")).toHaveText("main.py:1");
});

test("preview resizer responds to keyboard resizing", async ({ workbench }) => {
  const resizer = workbench.previewResizer();
  await expect(resizer).toBeVisible();
  await expect(resizer).toHaveAttribute("aria-valuemin", "120");

  const before = Number(await resizer.getAttribute("aria-valuenow"));
  await resizer.focus();
  await expect(resizer).toBeFocused();

  await workbench.page.keyboard.press("ArrowUp");
  await expect
    .poll(async () => Number(await resizer.getAttribute("aria-valuenow")))
    .toBe(before + 24);

  await workbench.page.keyboard.press("PageDown");
  await expect
    .poll(async () => Number(await resizer.getAttribute("aria-valuenow")))
    .toBe(before + 24 - 120);

  await workbench.page.keyboard.press("Home");
  await expect.poll(async () => Number(await resizer.getAttribute("aria-valuenow"))).toBe(120);
});

test("command palette lists Command entries and runs one", async ({ workbench, page }) => {
  await workbench.openQuickOpen();
  await expect(workbench.quickOpenCommands()).toHaveCount(6);

  await workbench.quickOpenInput.fill("Back");
  const command = workbench.quickOpenResult("Command: Back to workspaces");
  await expect(command).toBeVisible();
  await command.click();

  await expect(page.locator("#home-page")).toBeVisible();
  await expect(page.locator("#quick-open-palette")).toBeHidden();
});

test("bulk file checkboxes drive the selection toolbar", async ({ workbench }) => {
  const readme = workbench.bulkCheckbox("README.md");
  const main = workbench.bulkCheckbox("main.py");
  await expect(readme).toBeVisible();
  await expect(workbench.bulkActions()).toBeHidden();

  await readme.check();
  await expect(workbench.bulkActions()).toBeVisible();
  await expect(workbench.bulkCount()).toHaveText("1 file selected");

  await main.check();
  await expect(workbench.bulkCount()).toHaveText("2 files selected");

  await readme.uncheck();
  await expect(workbench.bulkCount()).toHaveText("1 file selected");
  await main.uncheck();
  await expect(workbench.bulkActions()).toBeHidden();
});

test("markdown preview toggle shows and hides the rendered document", async ({ workbench }) => {
  await expect(workbench.markdownToggle()).toBeHidden();

  await workbench.openFile("README.md");
  // The app reveals the toggle on the first document update, not on tab activation.
  await workbench.editEditor("# E2E markdown\n\nYour project files live in this workspace.\n");
  await expect(workbench.markdownToggle()).toBeVisible();
  await expect(workbench.markdownPreview()).toBeVisible();
  await expect(workbench.markdownPreviewBody()).toContainText("E2E markdown");
  await expect(workbench.markdownPreviewBody()).toContainText(
    "Your project files live in this workspace.",
  );

  await workbench.markdownToggle().click();
  await expect(workbench.markdownPreview()).toBeHidden();

  await workbench.markdownToggle().click();
  await expect(workbench.markdownPreview()).toBeVisible();
  await expect(workbench.markdownPreviewBody()).toContainText(
    "Your project files live in this workspace.",
  );
});

test("plugins capability view lists available and unavailable states", async ({ page }) => {
  await page.goto("/admin.html");
  await expect(page.locator("#capability-list .capability")).not.toHaveCount(0);
  await expect(page.locator("#capability-list .capability-unavailable")).not.toHaveCount(0);
});
