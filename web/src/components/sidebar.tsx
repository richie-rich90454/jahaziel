///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { errorText } from "../lib/api";
import {
  addDebugWatch,
  applyDebugBreakpoints,
  attachDebugSession,
  chooseDebugLaunchConfiguration,
  commitGitChanges,
  createFile,
  createFolder,
  debugCommand,
  deleteSelected,
  downloadSelected,
  evaluateDebugConsole,
  focusDiagnostic,
  loadDebugLaunchConfigurations,
  loadGitDiff,
  reconnectDebugSession,
  refreshGitSafely,
  renderFile,
  renameSelected,
  retrySetup,
  runTask,
  scheduleWorkspaceSearch,
  selectAndOpenFile,
  selectDebugStackFrame,
  setDebugArguments,
  setDebugAttachTarget,
  setDebugBreakpointLines,
  setDebugBreakpointPath,
  setDebugLanguage,
  setDebugMainClass,
  setDebugProgram,
  setDebugStopOnEntry,
  setDebugWorkingDirectory,
  showRun,
  startDebugSession,
  stopDebugSession,
  stopTask,
  toggleLanguagePack,
  updateGitMessage,
  updateGitPath,
} from "../lib/controller";
import {
  bindDisabled,
  bindHidden,
  bindText,
  bindValue,
  byId,
  effect,
  html,
  on,
} from "../lib/dom";
import { state, toast, type DapFrame, type DapVariable, type GitChange, type LanguagePack } from "../lib/store";

function guard(action: () => void | Promise<unknown>): void {
  void Promise.resolve(action()).catch((failure: unknown) => toast(errorText(failure), "error"));
}

function renderGitChanges(): void {
  const list = byId("git-changes");
  effect(() => {
    const changes = state.gitChanges;
    list.replaceChildren(...changes.map((change) => gitChangeRow(change)));
  });
}

function gitChangeRow(change: GitChange): HTMLLIElement {
  const item = document.createElement("li");
  item.className = "git-change";
  if (!change.indexStatus && !change.worktreeStatus) {
    const empty = document.createElement("span");
    empty.className = "git-empty";
    empty.textContent = change.path;
    item.append(empty);
    return item;
  }
  const path = document.createElement("button");
  path.type = "button";
  path.className = "git-path";
  path.textContent = change.path;
  path.title = change.path;
  on(path, "click", () => guard(() => loadGitDiff(change.path)));
  const code = document.createElement("span");
  code.className = "git-code";
  code.textContent = `${change.indexStatus}${change.worktreeStatus}`;
  item.append(path, code);
  if (change.indexStatus !== " " && change.indexStatus !== "?") {
    const unstage = document.createElement("button");
    unstage.type = "button";
    unstage.className = "git-action";
    unstage.textContent = "Unstage";
    on(unstage, "click", () => guard(() => updateGitPath("unstage", change.path)));
    item.append(unstage);
  }
  if (change.worktreeStatus !== " " || change.indexStatus === "?") {
    const stage = document.createElement("button");
    stage.type = "button";
    stage.className = "git-action";
    stage.textContent = "Stage";
    on(stage, "click", () => guard(() => updateGitPath("stage", change.path)));
    item.append(stage);
  }
  return item;
}

function renderLanguagePacks(): void {
  const list = byId("language-pack-list");
  effect(() => {
    const packs = state.languagePacks;
    if (packs.length === 0) {
      const empty = document.createElement("li");
      empty.className = "language-empty";
      empty.textContent = "No language packs are listed by this server.";
      list.replaceChildren(empty);
      return;
    }
    list.replaceChildren(...packs.map((pack) => languagePackRow(pack)));
  });
}

function languagePackRow(pack: LanguagePack): HTMLLIElement {
  const row = document.createElement("li");
  row.className = "language-pack";
  const name = document.createElement("span");
  name.className = "language-pack-name";
  name.textContent = `${pack.name}, ${pack.version}`;
  const meta = document.createElement("span");
  meta.className = "language-pack-meta";
  const size = pack.downloadBytes > 0 ? `${Math.ceil(pack.downloadBytes / 1024)} KB download` : "size unavailable";
  const status = !pack.supportedByTemplate
    ? "not listed for this template"
    : pack.selected ? (pack.installed ? "enabled" : "selected, missing") : pack.availability;
  meta.textContent = `${status}, ${size}${pack.installationNote ? `, ${pack.installationNote}` : ""}`;
  meta.title = `${pack.availability}; ${pack.supportedArchitectures.join(", ") || "no supported architecture"}; ${pack.checksums.join("; ")}`;
  const action = document.createElement("button");
  action.type = "button";
  action.className = "language-pack-action";
  action.textContent = pack.selected ? "Disable" : "Enable";
  action.disabled = !pack.selected && (!pack.supportedByTemplate || pack.availability !== "available");
  on(action, "click", () => guard(() => toggleLanguagePack(pack)));
  row.append(name, meta, action);
  return row;
}

function renderDiagnostics(): void {
  const panel = byId("language-diagnostics");
  effect(() => {
    const diagnostics = state.diagnostics;
    panel.replaceChildren(...diagnostics.map((diagnostic) => {
      const item = document.createElement("button");
      item.type = "button";
      item.className = `language-diagnostic language-diagnostic-${diagnostic.severity.toLowerCase()}`;
      const location = document.createElement("span");
      location.className = "language-diagnostic-location";
      location.textContent = `${diagnostic.path}:${diagnostic.line}:${diagnostic.column} `;
      item.append(location, document.createTextNode(`${diagnostic.severity}: ${diagnostic.message}`));
      item.title = `${diagnostic.path}:${diagnostic.line}:${diagnostic.column} — ${diagnostic.message}`;
      on(item, "click", () => guard(() => focusDiagnostic(diagnostic)));
      return item;
    }));
  });
}

function renderDebugLists(): void {
  const breakpoints = byId("debug-breakpoint-list");
  effect(() => {
    const entries = state.debugBreakpoints;
    breakpoints.replaceChildren(...entries.map((entry) => {
      const item = document.createElement("li");
      item.className = "debug-empty";
      item.textContent = `${entry.path}: ${entry.lines.length ? entry.lines.join(", ") : "cleared"}`;
      return item;
    }));
  });

  const stack = byId("debug-stack");
  effect(() => {
    const frames = state.debugFrames;
    if (frames.length === 0) {
      const empty = document.createElement("li");
      empty.className = "debug-empty";
      empty.textContent = "No frames are available.";
      stack.replaceChildren(empty);
      return;
    }
    stack.replaceChildren(...frames.map((frame: DapFrame) => {
      const item = document.createElement("li");
      const select = document.createElement("button");
      const source = frame.source?.path || frame.source?.name || "unknown source";
      select.textContent = `${frame.name}, ${source}${frame.line ? `:${frame.line}` : ""}`;
      select.setAttribute("aria-current", String(frame.id === state.debugFrames[0]?.id));
      on(select, "click", () => selectDebugStackFrame(frame));
      item.append(select);
      return item;
    }));
  });

  const variables = byId("debug-variables");
  effect(() => {
    const values = state.debugVariables;
    if (values.length === 0) {
      const empty = document.createElement("li");
      empty.className = "debug-empty";
      empty.textContent = "No variables in this scope.";
      variables.replaceChildren(empty);
      return;
    }
    variables.replaceChildren(...values.map((variable: DapVariable) => variableRow(variable.name, variable.value, variable.type)));
  });

  const watches = byId("debug-watches");
  effect(() => {
    const expressions = state.debugWatches;
    const values = state.debugWatchValues;
    if (expressions.length === 0) {
      const empty = document.createElement("li");
      empty.className = "debug-empty";
      empty.textContent = "Add an expression to inspect it when paused.";
      watches.replaceChildren(empty);
      return;
    }
    watches.replaceChildren(...expressions.map((expression) => variableRow(expression, values[expression] ?? "—")));
  });
}

function variableRow(name: string, value: string, title?: string): HTMLLIElement {
  const item = document.createElement("li");
  item.className = "debug-watch-row";
  const nameNode = document.createElement("span");
  nameNode.className = "debug-watch-expression";
  nameNode.textContent = name;
  const valueNode = document.createElement("span");
  valueNode.className = "debug-watch-value";
  valueNode.textContent = value;
  if (title) valueNode.title = title;
  item.append(nameNode, valueNode);
  return item;
}

function renderDebugSelects(): void {
  const configSelect = byId<HTMLSelectElement>("debug-launch-config");
  effect(() => {
    const configs = state.debugConfigs;
    configSelect.replaceChildren(...configs.map((configuration) => {
      const option = document.createElement("option");
      option.value = configuration.name;
      option.textContent = `${configuration.name}, ${configuration.languageId}`;
      return option;
    }));
  });
  bindValue(configSelect, () => state.debugConfigName);
  on(configSelect, "change", () => chooseDebugLaunchConfiguration(configSelect.value));

  const targetSelect = byId<HTMLSelectElement>("debug-attach-target");
  effect(() => {
    const targets = state.debugTargets;
    if (targets.length === 0) {
      targetSelect.replaceChildren(new Option("Start a debuggable task first", ""));
      return;
    }
    targetSelect.replaceChildren(...targets.map((target) => new Option(`${target.label}, ${languages[target.languageId] ?? target.languageId}`, target.id)));
  });
  bindValue(targetSelect, () => state.debugAttachTarget);
  on(targetSelect, "change", () => setDebugAttachTarget(targetSelect.value));
}

const languages: Record<string, string> = {
  node: "Node, TypeScript",
  python: "Python",
  java: "Java",
  cpp: "C, C++",
  go: "Go",
  rust: "Rust",
};

export function sidebarMarkup(): string {
  return `
    <aside class="file-sidebar" aria-label="Workspace files">
      <div class="panel-heading"><div><span class="panel-glyph">▤</span><span>Explorer</span></div><span class="panel-meta">Workspace</span></div>
      <div class="project-root"><span class="root-caret">⌄</span><span id="root-name">Project</span></div>
      <div class="workspace-search" role="search">
        <input id="workspace-search" type="search" placeholder="Search workspace…" aria-label="Search workspace files and contents" autocomplete="off" />
        <ul id="search-results" class="search-results" aria-live="polite" hidden></ul>
      </div>
      <div class="file-actions" aria-label="File actions">
        <button id="new-file" type="button" title="New file">＋<span>File</span></button>
        <button id="new-folder" type="button" title="New folder">▱<span>Folder</span></button>
        <button id="download-selected" type="button" title="Download selected file" aria-label="Download selected file" disabled>↓</button>
        <button id="rename-selected" type="button" title="Rename selected">↗</button>
        <button id="delete-selected" type="button" title="Delete selected">×</button>
      </div>
      <ul id="file-tree" class="file-tree"></ul>

      <section class="task-panel" aria-label="Workspace setup and run tasks">
        <div class="panel-heading"><div><span class="panel-glyph">▶</span><span>Run</span></div><span id="setup-state" class="panel-meta">Pending</span></div>
        <div class="setup-row"><span id="setup-summary">Template setup is queued and will start shortly.</span><button id="setup-retry" class="text-action" type="button" hidden>Retry</button></div>
        <div id="setup-correlation" class="operation-ref" hidden></div>
        <pre id="setup-output" class="task-output" hidden></pre>
        <div id="task-list" class="task-list"></div>
        <button id="task-stop" class="text-action" type="button" hidden>Stop task</button>
        <div id="task-correlation" class="operation-ref" hidden></div>
        <pre id="task-output" class="task-output" hidden></pre>
        <section class="run-history-panel" aria-label="Run history">
          <h3>Run history</h3>
          <ul id="run-history" class="debug-list"></ul>
        </section>
      </section>

      <section class="debug-panel" aria-label="Debugger">
        <div class="panel-heading"><div><span class="panel-glyph">◆</span><span>Debug</span></div><span id="debug-state" class="panel-meta" role="status">NOT RUNNING</span></div>
        <div class="debug-config-toolbar">
          <button id="debug-load-launch-config" class="button button-quiet" type="button">Load launch.json</button>
          <label id="debug-launch-config-field" class="debug-field" for="debug-launch-config" hidden>Launch configuration
            <select id="debug-launch-config" hidden disabled></select>
          </label>
          <span id="debug-launch-config-status" class="debug-config-status" role="status" aria-live="polite"></span>
        </div>
        <div class="debug-launch-form">
          <label class="debug-field" for="debug-language">Language
            <select id="debug-language">
              <option value="node">Node, TypeScript</option>
              <option value="python">Python</option>
              <option value="java">Java</option>
              <option value="cpp">C, C++</option>
              <option value="go">Go</option>
              <option value="rust">Rust</option>
            </select>
          </label>
          <label id="debug-program-field" class="debug-field" for="debug-program">Program path
            <input id="debug-program" type="text" maxlength="4096" placeholder="src/index.ts" autocomplete="off" />
          </label>
          <label id="debug-main-class-field" class="debug-field" for="debug-main-class" hidden>Java main class
            <input id="debug-main-class" type="text" maxlength="512" placeholder="com.example.Main" autocomplete="off" />
          </label>
          <label class="debug-field" for="debug-working-directory">Working directory
            <input id="debug-working-directory" type="text" maxlength="4096" value="." autocomplete="off" />
          </label>
          <label class="debug-field" for="debug-arguments">Arguments, JSON string array
            <input id="debug-arguments" type="text" maxlength="8192" value="[]" autocomplete="off" spellcheck="false" />
          </label>
          <label class="debug-check"><input id="debug-stop-on-entry" type="checkbox" /> Stop on entry</label>
        </div>
        <div class="debug-attach-form">
          <label class="debug-field" for="debug-attach-target">Attach to a running task
            <select id="debug-attach-target" aria-label="Running workspace debug target"></select>
          </label>
          <button id="debug-attach" class="button button-quiet" type="button" disabled>Attach</button>
        </div>
        <div class="debug-actions">
          <button id="debug-start" class="button btn btn-primary" type="button">Start debugging</button>
          <button id="debug-reconnect" class="button button-quiet" type="button" hidden>Reconnect</button>
          <button id="debug-stop" class="button button-quiet" type="button" disabled>Stop</button>
        </div>
        <div class="debug-controls" role="group" aria-label="Debugger controls">
          <button type="button" data-debug-command="continue" disabled>Continue</button>
          <button type="button" data-debug-command="pause" disabled>Pause</button>
          <button type="button" data-debug-command="next" disabled>Step over</button>
          <button type="button" data-debug-command="stepIn" disabled>Step in</button>
          <button type="button" data-debug-command="stepOut" disabled>Step out</button>
        </div>
        <section class="debug-subpanel" aria-label="Breakpoints">
          <h3>Breakpoints</h3>
          <p class="debug-breakpoint-hint">Tap or click a line number in the editor to toggle a breakpoint.</p>
          <label class="debug-field" for="debug-breakpoint-path">Workspace file
            <input id="debug-breakpoint-path" type="text" maxlength="4096" placeholder="Open a workspace file" autocomplete="off" />
          </label>
          <div class="debug-inline-form">
            <label class="debug-field" for="debug-breakpoint-lines">Line numbers
              <input id="debug-breakpoint-lines" type="text" maxlength="512" placeholder="12, 24" autocomplete="off" />
            </label>
            <button id="debug-apply-breakpoints" type="button" disabled>Set</button>
          </div>
          <ul id="debug-breakpoint-list" class="debug-list"></ul>
        </section>
        <div class="debug-inspect-grid">
          <section class="debug-subpanel" aria-label="Call stack">
            <h3>Call stack</h3>
            <ul id="debug-stack" class="debug-list"><li class="debug-empty">Pause to inspect frames.</li></ul>
          </section>
          <section class="debug-subpanel" aria-label="Variables">
            <h3>Variables</h3>
            <ul id="debug-variables" class="debug-list"><li class="debug-empty">No frame selected.</li></ul>
          </section>
        </div>
        <section class="debug-subpanel" aria-label="Watches">
          <h3>Watches</h3>
          <form id="debug-watch-form" class="debug-inline-form">
            <label class="debug-field" for="debug-watch-expression">Expression
              <input id="debug-watch-expression" type="text" maxlength="512" placeholder="user.name" autocomplete="off" />
            </label>
            <button type="submit" disabled>Add</button>
          </form>
          <ul id="debug-watches" class="debug-list"><li class="debug-empty">Add an expression to inspect it when paused.</li></ul>
        </section>
        <section class="debug-subpanel" aria-label="Debug console">
          <h3>Debug console</h3>
          <pre id="debug-console" class="debug-console" aria-live="polite">Debugger output will appear here.</pre>
          <form id="debug-console-form" class="debug-inline-form">
            <label class="debug-field" for="debug-console-input">Evaluate
              <input id="debug-console-input" type="text" maxlength="4096" placeholder="Expression or command" autocomplete="off" />
            </label>
            <button type="submit" disabled>Send</button>
          </form>
        </section>
      </section>

      <section class="language-panel" aria-label="Language services">
        <div class="panel-heading"><div><span class="panel-glyph">λ</span><span>Languages</span></div><span id="language-state" class="panel-meta">Optional</span></div>
        <ul id="language-pack-list" class="language-pack-list"></ul>
        <div id="language-diagnostics" class="language-diagnostics" aria-live="polite"></div>
      </section>

      <section class="git-panel" aria-label="Git source control">
        <div class="panel-heading"><div><span class="panel-glyph">⑂</span><span>Source control</span></div><div class="git-heading-actions"><span id="git-state" class="panel-meta">Git</span><button id="git-refresh" class="text-action" type="button" aria-label="Refresh Git status">Refresh</button></div></div>
        <ul id="git-changes" class="git-changes"></ul>
        <form id="git-commit-form" class="git-commit-form">
          <textarea id="git-message" maxlength="512" rows="2" placeholder="Commit message" aria-label="Commit message"></textarea>
          <button id="git-commit" class="button button-save" type="submit" disabled>Commit</button>
        </form>
        <pre id="git-diff" class="git-diff" hidden></pre>
      </section>
      <div class="sidebar-foot"><span class="tiny-pulse"></span>Files stored on server</div>
    </aside>
  `;
}

export function bindSidebar(): void {
  bindFileTree();
  bindRunPanel();
  bindDebugPanel();
  bindGitPanel();
  renderLanguagePacks();
  renderDiagnostics();
  renderDebugLists();
  renderDebugSelects();
  renderGitChanges();
  bindText(byId("language-state"), () => state.languageState);
}

function bindFileTree(): void {
  const tree = byId("file-tree");
  effect(() => {
    const files = state.files;
    if (files.length === 0) {
      const empty = document.createElement("p");
      empty.className = "tree-empty";
      empty.textContent = "This workspace is empty.";
      tree.replaceChildren(empty);
      return;
    }
    tree.replaceChildren(...files.map((entry) => renderFile(entry)));
  });
  const search = byId<HTMLInputElement>("workspace-search");
  on(search, "input", () => scheduleWorkspaceSearch(search.value));
  on(search, "keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      scheduleWorkspaceSearch(search.value, true);
    }
    if (event.key === "Escape") {
      search.value = "";
      scheduleWorkspaceSearch("");
    }
  });
  const results = byId("search-results");
  effect(() => {
    const matches = state.searchResults;
    results.replaceChildren(...matches.map((match) => {
      const item = document.createElement("li");
      const open = document.createElement("button");
      open.type = "button";
      open.className = "search-result";
      const path = document.createElement("span");
      path.className = "search-result-path";
      path.textContent = `${match.path}${match.line ? `:${match.line}` : ""}`;
      const preview = document.createElement("small");
      preview.textContent = match.preview;
      open.append(path, preview);
      on(open, "click", () => selectAndOpenFile(match.path));
      item.append(open);
      return item;
    }));
  });
  bindHidden(results, () => !state.searchVisible);
  on(byId("new-file"), "click", () => guard(createFile));
  on(byId("new-folder"), "click", () => guard(createFolder));
  on(byId("rename-selected"), "click", () => guard(renameSelected));
  on(byId("delete-selected"), "click", () => guard(deleteSelected));
  on(byId("download-selected"), "click", downloadSelected);
  bindDisabled(byId<HTMLButtonElement>("download-selected"), () => state.downloadDisabled);
  bindText(byId("root-name"), () => state.rootName);
}

function bindRunPanel(): void {
  bindText(byId("setup-state"), () => state.setupState);
  bindText(byId("setup-summary"), () => state.setupSummary);
  bindHidden(byId("setup-retry"), () => state.setupRetryHidden);
  bindText(byId("setup-correlation"), () => state.setupCorrelation);
  bindHidden(byId("setup-correlation"), () => state.setupCorrelationHidden);
  bindText(byId("setup-output"), () => state.setupOutput);
  bindHidden(byId("setup-output"), () => state.setupOutputHidden);

  const taskList = byId("task-list");
  effect(() => {
    const tasks = state.tasks;
    if (tasks.length === 0) {
      const empty = document.createElement("span");
      empty.className = "setup-row";
      empty.textContent = "No runnable tasks found in this workspace.";
      taskList.replaceChildren(empty);
      return;
    }
    taskList.replaceChildren(...tasks.map((task) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "task-button";
      button.textContent = task.port ? `${task.label}  :${task.port}` : task.label;
      on(button, "click", () => guard(() => runTask(task)));
      return button;
    }));
  });
  bindText(byId("task-output"), () => state.taskOutput);
  bindHidden(byId("task-output"), () => state.taskOutputHidden);
  bindText(byId("task-correlation"), () => state.taskCorrelation);
  bindHidden(byId("task-correlation"), () => state.taskCorrelationHidden);
  bindHidden(byId("task-stop"), () => state.taskStopHidden);
  bindDisabled(byId<HTMLButtonElement>("task-stop"), () => state.taskStopDisabled);
  on(byId("task-stop"), "click", () => guard(stopTask));
  on(byId("setup-retry"), "click", () => guard(retrySetup));

  const history = byId("run-history");
  effect(() => {
    const runs = state.runHistory;
    if (runs.length === 0) {
      const empty = document.createElement("li");
      empty.className = "debug-empty";
      empty.textContent = "No runs yet.";
      history.replaceChildren(empty);
      return;
    }
    history.replaceChildren(...runs.map((run) => {
      const item = document.createElement("li");
      item.className = "debug-empty";
      item.textContent = `${run.label}, ${run.status}${run.exitCode === null ? "" : `, exit ${run.exitCode}`}`;
      item.title = run.id;
      on(item, "click", () => showRun(run));
      return item;
    }));
  });
}

function bindDebugPanel(): void {
  bindText(byId("debug-state"), () => state.debugState);
  bindText(byId("debug-launch-config-status"), () => state.debugConfigStatus);
  bindHidden(byId("debug-launch-config-field"), () => state.debugConfigFieldHidden);
  bindHidden(byId<HTMLSelectElement>("debug-launch-config"), () => state.debugConfigSelectHidden);
  bindDisabled(byId<HTMLSelectElement>("debug-launch-config"), () => state.debugConfigSelectDisabled);

  const language = byId<HTMLSelectElement>("debug-language");
  bindValue(language, () => state.debugLanguage);
  bindDisabled(language, () => state.debugLanguageDisabled);
  on(language, "change", () => setDebugLanguage(language.value));
  bindHidden(byId("debug-program-field"), () => state.debugProgramFieldHidden);
  bindHidden(byId("debug-main-class-field"), () => state.debugMainClassFieldHidden);
  const program = byId<HTMLInputElement>("debug-program");
  bindValue(program, () => state.debugProgram);
  bindDisabled(program, () => state.debugProgramDisabled);
  on(program, "input", () => setDebugProgram(program.value));
  const mainClass = byId<HTMLInputElement>("debug-main-class");
  bindValue(mainClass, () => state.debugMainClass);
  bindDisabled(mainClass, () => state.debugMainClassDisabled);
  on(mainClass, "input", () => setDebugMainClass(mainClass.value));
  const workingDirectory = byId<HTMLInputElement>("debug-working-directory");
  bindValue(workingDirectory, () => state.debugWorkingDirectory);
  bindDisabled(workingDirectory, () => state.debugWorkingDirectoryDisabled);
  on(workingDirectory, "input", () => setDebugWorkingDirectory(workingDirectory.value));
  const args = byId<HTMLInputElement>("debug-arguments");
  bindValue(args, () => state.debugArguments);
  bindDisabled(args, () => state.debugArgumentsDisabled);
  on(args, "input", () => setDebugArguments(args.value));
  const stopOnEntry = byId<HTMLInputElement>("debug-stop-on-entry");
  effect(() => { stopOnEntry.checked = state.debugStopOnEntry; });
  bindDisabled(stopOnEntry, () => state.debugStopOnEntryDisabled);
  on(stopOnEntry, "change", () => setDebugStopOnEntry(stopOnEntry.checked));

  bindDisabled(byId<HTMLButtonElement>("debug-attach"), () => state.debugAttachDisabled);
  bindDisabled(byId<HTMLSelectElement>("debug-attach-target"), () => state.debugAttachTargetDisabled);
  bindDisabled(byId<HTMLButtonElement>("debug-start"), () => state.debugStartDisabled);
  bindDisabled(byId<HTMLButtonElement>("debug-load-launch-config"), () => state.debugLoadLaunchConfigDisabled);
  bindDisabled(byId<HTMLButtonElement>("debug-stop"), () => state.debugStopDisabled);
  bindHidden(byId("debug-reconnect"), () => state.debugReconnectHidden);
  bindDisabled(byId<HTMLButtonElement>("debug-apply-breakpoints"), () => state.debugApplyBreakpointsDisabled);

  on(byId("debug-load-launch-config"), "click", () => guard(loadDebugLaunchConfigurations));
  on(byId("debug-start"), "click", () => guard(() => startDebugSession()));
  on(byId("debug-attach"), "click", () => guard(attachDebugSession));
  on(byId("debug-reconnect"), "click", () => guard(reconnectDebugSession));
  on(byId("debug-stop"), "click", () => guard(async () => { await stopDebugSession(true); }));
  on(byId("debug-apply-breakpoints"), "click", () => guard(() => applyDebugBreakpoints()));

  for (const button of Array.from(document.querySelectorAll<HTMLButtonElement>("[data-debug-command]"))) {
    const command = button.dataset.debugCommand ?? "";
    bindDisabled(button, () => state.debugControls[command] ?? true);
    on(button, "click", () => debugCommand(command));
  }

  const breakpointPath = byId<HTMLInputElement>("debug-breakpoint-path");
  bindValue(breakpointPath, () => state.debugBreakpointPath);
  on(breakpointPath, "input", () => setDebugBreakpointPath(breakpointPath.value));
  const breakpointLines = byId<HTMLInputElement>("debug-breakpoint-lines");
  bindValue(breakpointLines, () => state.debugBreakpointLines);
  on(breakpointLines, "input", () => setDebugBreakpointLines(breakpointLines.value));

  on(byId("debug-watch-form"), "submit", (event) => addDebugWatch(event as SubmitEvent));
  on(byId("debug-console-form"), "submit", (event) => evaluateDebugConsole(event as SubmitEvent));
  bindDisabled(byId<HTMLButtonElement>("debug-watch-form").querySelector<HTMLButtonElement>("button")!, () => state.debugWatchSubmitDisabled);
  bindDisabled(byId<HTMLButtonElement>("debug-console-form").querySelector<HTMLButtonElement>("button")!, () => state.debugConsoleSubmitDisabled);
  bindText(byId("debug-console"), () => state.debugConsole);
}

function bindGitPanel(): void {
  const stateNode = byId("git-state");
  effect(() => {
    const value = state.gitState;
    if (!value.includes(" · ")) {
      stateNode.textContent = value;
      return;
    }
    const [branch, count = ""] = value.split(" · ");
    const dot = document.createElement("span");
    dot.textContent = "·";
    dot.style.display = "none";
    stateNode.replaceChildren(document.createTextNode(`${branch} `), dot, document.createTextNode(` ${count}`));
  });
  on(byId("git-refresh"), "click", refreshGitSafely);
  const message = byId<HTMLTextAreaElement>("git-message");
  bindValue(message, () => state.gitMessage);
  on(message, "input", () => updateGitMessage(message.value));
  bindDisabled(byId<HTMLButtonElement>("git-commit"), () => state.gitCommitDisabled);
  bindHidden(byId("git-commit-form"), () => state.gitFormHidden);
  bindText(byId("git-diff"), () => state.gitDiff);
  bindHidden(byId("git-diff"), () => state.gitDiffHidden);
  on(byId("git-commit-form"), "submit", (event) => guard(() => commitGitChanges(event as SubmitEvent)));
}
