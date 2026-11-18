///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { expect, type Locator, type Page } from "@playwright/test";

export class HomePage {
  readonly page: Page;
  readonly loginLink: Locator;
  readonly userBadge: Locator;
  readonly userEmail: Locator;
  readonly workspaceName: Locator;
  readonly templateSelect: Locator;
  readonly createButton: Locator;
  readonly workspaceList: Locator;

  constructor(page: Page) {
    this.page = page;
    this.loginLink = page.locator("#login-link");
    this.userBadge = page.locator("#user-badge");
    this.userEmail = page.locator("#user-email");
    this.workspaceName = page.locator("#workspace-name");
    this.templateSelect = page.locator("#template-select");
    this.createButton = page.locator("#create-workspace");
    this.workspaceList = page.locator("#workspace-list");
  }

  async goto(): Promise<void> {
    await this.page.goto("/");
    await this.page.locator("#home-page").waitFor({ state: "visible" });
  }

  async expectRendered(): Promise<void> {
    await expect(this.page.getByRole("heading", { name: /Make room/ })).toBeVisible();
    await expect(this.page.getByRole("heading", { name: /Create a workspace/ })).toBeVisible();
    await expect(this.workspaceList).toBeAttached();
  }

  async expectSignedIn(email = "e2e@example.invalid"): Promise<void> {
    await expect(this.userBadge).toBeVisible();
    await expect(this.loginLink).toBeHidden();
    await expect(this.userEmail).toHaveText(email);
    await expect(this.page.locator("#loading-state")).toHaveText(/synced/i);
  }

  async expectSignedOut(): Promise<void> {
    await expect(this.loginLink).toBeVisible();
    await expect(this.userBadge).toBeHidden();
  }

  async signIn(): Promise<void> {
    await this.loginLink.click();
    await this.expectSignedIn();
    await expect(this.page).toHaveURL(/\/$/);
  }

  async createWorkspace(name: string, template = "python"): Promise<void> {
    await this.workspaceName.fill(name);
    await this.templateSelect.selectOption(template);
    await this.createButton.click();
    await this.page.locator("#workbench-page").waitFor({ state: "visible" });
    await this.expectHashWorkspace();
  }

  async expectHashWorkspace(): Promise<string> {
    await expect(this.page).toHaveURL(/#workspace\/[0-9a-f-]+$/i);
    return new URL(this.page.url()).hash.split("/")[1];
  }

  workspaceCard(name: string): Locator {
    return this.workspaceList.locator(".workspace-card").filter({ hasText: name }).first();
  }

  async expectWorkspaceListed(name: string): Promise<void> {
    await expect(this.workspaceCard(name)).toBeVisible({ timeout: 10_000 });
  }

  async openWorkspace(name: string): Promise<void> {
    await this.workspaceCard(name).getByRole("button", { name: "Open workspace" }).click();
    await this.page.locator("#workbench-page").waitFor({ state: "visible" });
  }
}

export class WorkbenchPage {
  readonly page: Page;
  readonly root: Locator;
  readonly title: Locator;
  readonly state: Locator;
  readonly fileTree: Locator;
  readonly searchInput: Locator;
  readonly searchResults: Locator;
  readonly activeFile: Locator;
  readonly editor: Locator;
  readonly saveState: Locator;
  readonly saveButton: Locator;
  readonly conflictActions: Locator;
  readonly taskOutput: Locator;
  readonly setupState: Locator;
  readonly gitState: Locator;
  readonly languageState: Locator;
  readonly debugState: Locator;
  readonly terminalState: Locator;
  readonly quickOpenInput: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.locator("#workbench-page");
    this.title = page.locator("#workbench-title");
    this.state = page.locator("#workbench-state");
    this.fileTree = page.locator("#file-tree");
    this.searchInput = page.locator("#workspace-search");
    this.searchResults = page.locator("#search-results");
    this.activeFile = page.locator("#active-file");
    this.editor = page.locator("#editor-surface .cm-content").first();
    this.saveState = page.locator("#save-state");
    this.saveButton = page.locator("#save-file");
    this.conflictActions = page.locator("#save-conflict-actions");
    this.taskOutput = page.locator("#task-output");
    this.setupState = page.locator("#setup-state");
    this.gitState = page.locator("#git-state");
    this.languageState = page.locator("#language-state");
    this.debugState = page.locator("#debug-state");
    this.terminalState = page.locator("#terminal-state");
    this.quickOpenInput = page.locator("#quick-open-input");
  }

  async expectLoaded(): Promise<void> {
    await expect(this.root).toBeVisible();
    await expect(this.fileTree).toBeVisible();
    await expect(this.title).not.toHaveText("Workspace");
  }

  async openPanel(panel: string): Promise<void> {
    const nav = this.page.locator(`.workbench-panel-nav [data-panel="${panel}"]`);
    if (await nav.isVisible()) {
      await nav.click();
    } else {
      // The nav strip is mobile-only in the workbench CSS; quick open is universal.
      await this.openQuickOpen();
      await this.quickOpenResult(`Panel: ${panel}`).click();
    }
    await expect(nav).toHaveAttribute("aria-pressed", "true");
  }

  async expectPanel(panel: string): Promise<void> {
    await expect(this.page.locator(`.workbench-panel-nav [data-panel="${panel}"]`)).toHaveAttribute("aria-pressed", "true");
  }

  async backHome(): Promise<void> {
    await this.page.locator("#back-home").click();
    await this.page.locator("#home-page").waitFor({ state: "visible" });
    await expect(this.root).toBeHidden();
  }

  treeItem(name: string): Locator {
    return this.fileTree.locator(".tree-item").filter({ hasText: name }).first();
  }

  async openFile(path: string): Promise<void> {
    const name = path.split("/").pop() ?? path;
    await this.treeItem(name).click();
    await expect(this.activeFile).toHaveText(name);
    await expect(this.editor).toBeVisible();
  }

  async editEditor(content: string): Promise<void> {
    await this.editor.click();
    await this.page.keyboard.press("ControlOrMeta+A");
    await this.page.keyboard.insertText(content);
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }

  async search(query: string): Promise<void> {
    await this.searchInput.fill(query);
    await expect(this.searchResults).toBeVisible();
  }

  searchResult(text: string): Locator {
    return this.searchResults.locator(".search-result").filter({ hasText: text }).first();
  }

  async openQuickOpen(): Promise<void> {
    await this.page.keyboard.press("ControlOrMeta+P");
    await expect(this.page.locator("#quick-open-palette")).toBeVisible();
    await expect(this.quickOpenInput).toBeFocused();
  }

  quickOpenResult(text: string): Locator {
    return this.page.locator("#quick-open-results button").filter({ hasText: text }).first();
  }

  taskButton(label: string): Locator {
    return this.page.locator("#task-list button").filter({ hasText: label }).first();
  }

  languagePack(name: string): Locator {
    return this.page.locator(".language-pack").filter({ hasText: name }).first();
  }

  gitChange(path: string): Locator {
    return this.page.locator("#git-changes .git-change").filter({ hasText: path }).first();
  }

  async setViewport(width: number, height: number): Promise<void> {
    await this.page.setViewportSize({ width, height });
  }
}
