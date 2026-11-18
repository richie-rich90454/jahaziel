///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { expect, test } from "./fixtures";

test("browses the file tree including nested directories", async ({ workbench }) => {
  await expect(workbench.treeItem("README.md")).toBeVisible();
  await expect(workbench.treeItem("main.py")).toBeVisible();
  await expect(workbench.treeItem("src")).toBeVisible();
  await workbench.treeItem(".vscode").click();
  await expect(workbench.fileTree.locator(".tree-item").filter({ hasText: "launch.json" })).toBeVisible();
  await workbench.treeItem(".vscode").click();
  await expect(workbench.fileTree.locator(".tree-item").filter({ hasText: "launch.json" })).toBeHidden();
});

test("opens, edits and saves a file", async ({ workbench }) => {
  await workbench.openFile("README.md");
  await workbench.editEditor("# Saved by E2E\n\nhello\n");
  await expect(workbench.saveButton).toBeEnabled();
  await expect(workbench.saveState).toContainText("Unsaved");
  await workbench.save();
  await expect(workbench.saveState).toHaveText("Saved");
  await expect(workbench.saveButton).toBeDisabled();
});

test("detects and resolves an ETag conflict", async ({ workbench, workspace, page }) => {
  await workbench.openFile("README.md");
  await workbench.editEditor("# Local edit that must not win\n");

  const documentUrl = `/api/v1/workspaces/${workspace.id}/files/document?path=README.md`;
  const current = await page.request.get(documentUrl);
  expect(current.ok()).toBeTruthy();
  const etag = current.headers()["etag"];
  expect(etag).toBeTruthy();
  const external = await page.request.put(documentUrl, {
    headers: { "If-Match": etag! },
    data: { content: "# External update from another client\n" },
  });
  expect(external.ok()).toBeTruthy();

  await workbench.save();
  await expect(workbench.saveState).toContainText("Changed on disk");
  await expect(workbench.conflictActions).toBeVisible();

  page.once("dialog", (dialog) => void dialog.accept());
  await page.locator("#use-server-version").click();
  await expect(workbench.conflictActions).toBeHidden();
  await expect(workbench.editor).toContainText("External update from another client");
});

test("creates, renames and deletes files through the file actions", async ({ workbench, page }) => {
  page.on("dialog", (dialog) => void dialog.accept("scratch.txt"));
  await page.locator("#new-file").click();
  await expect(workbench.activeFile).toHaveText("scratch.txt");
  await expect(workbench.treeItem("scratch.txt")).toBeVisible();

  page.removeAllListeners("dialog");
  page.on("dialog", (dialog) => void dialog.accept("renamed.txt"));
  await page.locator("#rename-selected").click();
  await expect(workbench.activeFile).toHaveText("renamed.txt");

  page.removeAllListeners("dialog");
  page.on("dialog", (dialog) => void dialog.accept());
  await page.locator("#delete-selected").click();
  await expect(workbench.treeItem("renamed.txt")).toBeHidden();
});

test("searches file contents and quick-opens a result", async ({ workbench }) => {
  await workbench.search("Searchable");
  const result = workbench.searchResult("notes.txt");
  await expect(result).toBeVisible();
  await expect(result).toContainText("Searchable content");
  await result.click();
  await expect(workbench.activeFile).toHaveText("notes.txt");
});

test("quick-open palette lists panels and opens files", async ({ workbench }) => {
  await workbench.openQuickOpen();
  await expect(workbench.quickOpenResult("Panel: git")).toBeVisible();
  await workbench.quickOpenResult("Panel: git").click();
  await workbench.expectPanel("git");

  await workbench.openQuickOpen();
  await workbench.quickOpenInput.fill("main.py");
  const file = workbench.quickOpenResult("main.py");
  await expect(file).toBeVisible();
  await file.click();
  await expect(workbench.activeFile).toHaveText("main.py");
  await expect(workbench.page.locator("#quick-open-palette")).toBeHidden();
});

test("downloads the selected file", async ({ workbench, page }) => {
  await workbench.treeItem("README.md").click();
  await expect(page.locator("#download-selected")).toBeEnabled();
  const download = page.waitForEvent("download");
  await page.locator("#download-selected").click();
  expect((await download).suggestedFilename()).toBeTruthy();
});
