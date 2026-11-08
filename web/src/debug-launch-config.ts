///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

export interface DebugLaunchConfiguration {
  name: string;
  languageId: "node" | "python" | "java" | "cpp" | "go" | "rust";
  program: string | null;
  mainClass: string | null;
  workingDirectory: string;
  arguments: string[];
  stopOnEntry: boolean;
}

const rootKeys = new Set(["version", "configurations"]);
const configurationKeys = new Set([
  "name",
  "type",
  "request",
  "program",
  "mainClass",
  "cwd",
  "args",
  "stopOnEntry",
]);
const languageTypes = new Map<string, DebugLaunchConfiguration["languageId"]>([
  ["node", "node"],
  ["pwa-node", "node"],
  ["python", "python"],
  ["debugpy", "python"],
  ["java", "java"],
  ["cppdbg", "cpp"],
  ["go", "go"],
  ["lldb", "rust"],
]);

/** Parse only the documented, workspace-relative launch subset Jahaziel accepts. */
export function parseDebugLaunchConfigurations(content: string): DebugLaunchConfiguration[] {
  if (content.length > 128 * 1024) {
    throw new Error(".vscode/launch.json exceeds the 128 KB supported limit.");
  }
  let value: unknown;
  try {
    value = JSON.parse(content) as unknown;
  } catch {
    throw new Error(".vscode/launch.json is not valid JSON.");
  }
  if (!isRecord(value) || hasUnknownKeys(value, rootKeys) || value.version !== "0.2.0") {
    throw new Error("launch.json must use version 0.2.0 and contain only version and configurations.");
  }
  if (!Array.isArray(value.configurations) || value.configurations.length === 0 || value.configurations.length > 32) {
    throw new Error("launch.json must contain between 1 and 32 configurations.");
  }

  const names = new Set<string>();
  return value.configurations.map((item, index) => {
    const label = `configuration ${index + 1}`;
    if (!isRecord(item) || hasUnknownKeys(item, configurationKeys)) {
      throw new Error(`${label} contains unsupported fields.`);
    }
    const name = item.name;
    const languageId = typeof item.type === "string" ? languageTypes.get(item.type) : undefined;
    if (typeof name !== "string" || name.trim().length === 0 || name.length > 128 || names.has(name)) {
      throw new Error(`${label} must have a unique name of at most 128 characters.`);
    }
    if (item.request !== "launch") {
      throw new Error(`${label} must use request "launch"; attach is not supported.`);
    }
    if (!languageId) {
      throw new Error(`${label} uses an unsupported debugger type.`);
    }
    names.add(name);

    const workingDirectory =
      item.cwd === undefined ? "." : workspaceRelativePath(item.cwd, `${label} cwd`, true);
    const rawArguments = item.args === undefined ? [] : item.args;
    if (
      !Array.isArray(rawArguments) ||
      rawArguments.length > 128 ||
      rawArguments.some((argument) => typeof argument !== "string" || argument.length > 4096)
    ) {
      throw new Error(`${label} args must be an array of at most 128 strings.`);
    }
    if (item.stopOnEntry !== undefined && typeof item.stopOnEntry !== "boolean") {
      throw new Error(`${label} stopOnEntry must be a boolean.`);
    }

    if (languageId === "java") {
      if (
        typeof item.mainClass !== "string" ||
        item.mainClass.length > 512 ||
        !/^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$/.test(item.mainClass) ||
        item.program !== undefined
      ) {
        throw new Error(`${label} must define a Java mainClass and no program path.`);
      }
      return {
        name,
        languageId,
        program: null,
        mainClass: item.mainClass,
        workingDirectory,
        arguments: rawArguments as string[],
        stopOnEntry: item.stopOnEntry === true,
      };
    }
    if (item.mainClass !== undefined) {
      throw new Error(`${label} mainClass is supported only for Java.`);
    }
    const program = workspaceRelativePath(item.program, `${label} program`, false);
    return {
      name,
      languageId,
      program,
      mainClass: null,
      workingDirectory,
      arguments: rawArguments as string[],
      stopOnEntry: item.stopOnEntry === true,
    };
  });
}

function workspaceRelativePath(value: unknown, field: string, allowCurrent: boolean): string {
  if (typeof value !== "string" || value.trim().length === 0 || value.length > 4096 || value.includes("\0")) {
    throw new Error(`${field} must be a workspace-relative path.`);
  }
  const path = value.replaceAll("\\", "/");
  if (path.startsWith("/") || /^[A-Za-z]:/.test(path)) {
    throw new Error(`${field} must stay inside the workspace.`);
  }
  const segments: string[] = [];
  for (const segment of path.split("/")) {
    if (segment === "" || segment === ".") continue;
    if (segment === "..") {
      if (segments.length === 0) throw new Error(`${field} must stay inside the workspace.`);
      segments.pop();
    } else {
      segments.push(segment);
    }
  }
  if (segments.length === 0) {
    if (allowCurrent) return ".";
    throw new Error(`${field} must name a file inside the workspace.`);
  }
  return segments.join("/");
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function hasUnknownKeys(value: Record<string, unknown>, allowed: Set<string>): boolean {
  return Object.keys(value).some((key) => !allowed.has(key));
}
