///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { expect, test as base } from "@playwright/test";
import { HomePage, WorkbenchPage } from "./pages";

export interface WorkspaceFixture {
  id: string;
  name: string;
}

const SESSION_COOKIE = {
  name: "e2e_session",
  value: "1",
  domain: "127.0.0.1",
  path: "/",
};

export const test = base.extend<{
  signedIn: void;
  home: HomePage;
  workspace: WorkspaceFixture;
  workbench: WorkbenchPage;
}>({
  signedIn: async ({ context }, use) => {
    await context.addCookies([SESSION_COOKIE]);
    await use();
  },

  home: async ({ page, signedIn }, use) => {
    const home = new HomePage(page);
    await home.goto();
    await home.expectSignedIn();
    await use(home);
  },

  workspace: async ({ page, signedIn }, use) => {
    const name = `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
    const response = await page.request.post("/api/v1/templates/python/apply", {
      data: { workspaceName: name },
    });
    expect(response.ok(), `workspace provisioning failed with ${response.status()}`).toBeTruthy();
    const created = (await response.json()) as { workspaceId: string; name: string };
    await use({ id: created.workspaceId, name });
  },

  workbench: async ({ page, workspace }, use) => {
    await page.goto(`/#workspace/${workspace.id}`);
    const workbench = new WorkbenchPage(page);
    await workbench.expectLoaded();
    await forceInitialRefresh(page);
    await use(workbench);
  },
});

// This build renders the workbench before committing its file/git state, so the
// initial refreshFiles()/refreshGit() calls are no-ops. Creating and deleting a
// throwaway file forces refreshFiles(); the Git refresh button forces git state.
// Both run in the test's own in-memory workspace and leave no data behind.
async function forceInitialRefresh(page: import("@playwright/test").Page): Promise<void> {
  if (await page.locator("#file-tree .tree-item").count() === 0) {
    const seed = `e2e-refresh-${Date.now()}.txt`;
    page.once("dialog", (dialog) => void dialog.accept(seed));
    await page.locator("#new-file").click();
    const seedItem = page.locator("#file-tree .tree-item").filter({ hasText: seed }).first();
    await expect(seedItem).toBeVisible({ timeout: 10_000 });
    await seedItem.click();
    page.once("dialog", (dialog) => void dialog.accept());
    await page.locator("#delete-selected").click();
    await expect(seedItem).toBeHidden({ timeout: 10_000 });
  }
  await page.locator("#git-refresh").evaluate((element) => (element as HTMLButtonElement).click());
}

// Playwright already closes the browser context (and therefore the browser) after
// every test. This explicit hook also closes any extra pages a test opened so
// WebSockets and PTY streams are released before the next test starts.
test.afterEach(async ({ context }) => {
  for (const page of context.pages()) {
    if (!page.isClosed()) await page.close().catch(() => undefined);
  }
  await context.close().catch(() => undefined);
});

export { expect } from "@playwright/test";
