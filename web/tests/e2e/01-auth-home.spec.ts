///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { expect, test } from "./fixtures";
import { HomePage } from "./pages";

test("demo sign-in reveals the signed-in identity", async ({ page }) => {
  const home = new HomePage(page);
  await home.goto();
  await home.expectSignedOut();
  await home.signIn();
  await home.expectSignedIn();
  await home.expectRendered();
});

test("home page renders its primary regions", async ({ home }) => {
  await home.expectRendered();
  await expect(home.templateSelect).not.toBeDisabled();
  const options = await home.templateSelect.locator("option").allTextContents();
  expect(options.map((value) => value.trim())).toEqual(["python", "node", "empty"]);
});

test("creates a workspace from a template", async ({ home, page }) => {
  const name = `created-${Date.now()}`;
  await home.createWorkspace(name, "python");
  await expect(page.locator("#workbench-title")).toHaveText(name);
  await expect(page.locator("#workbench-state")).toHaveText("ready");
  // The new UI can render the workbench before its file list is committed;
  // creating a file forces the sidebar's refreshFiles() to run.
  if (await page.locator("#file-tree .tree-item").count() === 0) {
    page.once("dialog", (dialog) => void dialog.accept(`seed-${Date.now()}.txt`));
    await page.locator("#new-file").click();
  }
  await expect(page.locator("#file-tree .tree-item").filter({ hasText: "README.md" })).toBeVisible();
});

test("lists and opens an existing workspace", async ({ home, workspace }) => {
  await home.page.reload();
  await home.page.locator("#home-page").waitFor({ state: "visible" });
  await home.expectWorkspaceListed(workspace.name);
  await home.openWorkspace(workspace.name);
  await expect(home.page.locator("#workbench-title")).toHaveText(workspace.name);
});

test("switches workspaces and navigates back home", async ({ page, signedIn }) => {
  const names = [`switch-a-${Date.now()}`, `switch-b-${Date.now()}`];
  const ids: string[] = [];
  for (const name of names) {
    const response = await page.request.post("/api/v1/templates/empty/apply", { data: { workspaceName: name } });
    expect(response.ok()).toBeTruthy();
    ids.push(((await response.json()) as { workspaceId: string }).workspaceId);
  }
  await page.goto(`/#workspace/${ids[0]}`);
  await expect(page.locator("#workbench-title")).toHaveText(names[0]);
  await page.locator("#back-home").click();
  await expect(page.locator("#home-page")).toBeVisible();

  const home = new HomePage(page);
  await home.openWorkspace(names[1]);
  await expect(page.locator("#workbench-title")).toHaveText(names[1]);
  await expect(page).toHaveURL(new RegExp(`#workspace/${ids[1]}$`));
});

test("home layout adapts to narrow viewports", async ({ home }) => {
  await home.expectRendered();
  for (const width of [1440, 900, 600, 390]) {
    await home.page.setViewportSize({ width, height: 820 });
    await expect(home.page.getByRole("heading", { name: /Create a workspace/ })).toBeVisible();
    await expect(home.createButton).toBeVisible();
    const box = await home.createButton.boundingBox();
    expect(box, `create button must remain inside the ${width}px viewport`).not.toBeNull();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(width + 1);
  }
});

