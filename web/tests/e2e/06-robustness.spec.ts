///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { expect, test } from "./fixtures";
import { HomePage } from "./pages";

test("rage-clicking Create workspace provisions exactly one workspace", async ({ page, signedIn }) => {
  const home = new HomePage(page);
  await home.goto();
  const name = `rage-${Date.now()}`;
  await home.workspaceName.fill(name);
  await home.templateSelect.selectOption("empty");
  await home.createButton.click({ clickCount: 3, delay: 20 });
  await expect(page.locator("#workbench-page")).toBeVisible();
  await page.locator("#back-home").click();
  await expect(home.workspaceList.locator(".workspace-card").filter({ hasText: name })).toHaveCount(1);
});

test("workspace names are rendered as text, never as markup", async ({ page, signedIn }) => {
  const payload = `<img src=x onerror="window.__pwned=1">`;
  const response = await page.request.post("/api/v1/templates/empty/apply", { data: { workspaceName: payload } });
  expect(response.ok()).toBeTruthy();
  const home = new HomePage(page);
  await home.goto();
  await expect(home.workspaceCard(payload)).toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { __pwned?: boolean }).__pwned)).toBeUndefined();
  expect(await home.workspaceCard(payload).locator("img").count()).toBe(0);
});

test("a failing API surfaces an error toast without breaking the page", async ({ page, signedIn }) => {
  const home = new HomePage(page);
  await home.goto();
  // loadTemplates() has no catch, but the workspace form does toast failures.
  await page.route("**/api/v1/templates/*/apply", async (route) => {
    await route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ message: "boom" }) });
  });
  await home.workspaceName.fill(`failing-${Date.now()}`);
  await home.createButton.click();
  await expect(page.locator("#home-page .toast-error")).toContainText("500 boom");
  await expect(page.getByRole("heading", { name: /Make room/ })).toBeVisible();
});

test("a slow API still settles into a usable home page", async ({ page, signedIn }) => {
  await page.route("**/api/v1/me/workspaces", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 400));
    await route.continue();
  });
  const home = new HomePage(page);
  await home.goto();
  await home.expectRendered();
  await expect(page.locator("#loading-state")).toHaveText(/synced/i);
});

test("invalid input is rejected by the form and never sent", async ({ page, signedIn }) => {
  const home = new HomePage(page);
  await home.goto();
  await home.expectSignedIn();
  let provisioningCalls = 0;
  await page.route("**/api/v1/templates/*/apply", async (route) => {
    provisioningCalls += 1;
    await route.continue();
  });
  await home.createButton.click();
  expect(provisioningCalls).toBe(0);
  await expect(page.locator("#home-page")).toBeVisible();
});
