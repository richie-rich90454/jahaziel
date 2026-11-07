///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

export interface LanguagePack {
  id: string;
  name: string;
  version: string;
  downloadBytes: number;
  sha256: string;
  checksums: string[];
  supportedArchitectures: string[];
  installed: boolean;
  selected: boolean;
  availability: string;
  supportedByTemplate: boolean;
  installationNote: string;
}

export interface LanguageDiagnostic {
  path: string;
  line: number;
  column: number;
  severity: "Error" | "Warning" | "Information" | "Hint";
  message: string;
}

interface Connection {
  languageId: string;
  socket: WebSocket;
  initialized: boolean;
  initializeRequestId: number;
  activePath: string | null;
  version: number;
  changeTimer: number | null;
}

interface LspMessage {
  jsonrpc?: string;
  id?: string | number | null;
  method?: string;
  params?: Record<string, unknown>;
  result?: unknown;
  error?: { code?: number; message?: string };
}

type StatusHandler = (status: string) => void;
type DiagnosticsHandler = (diagnostics: LanguageDiagnostic[]) => void;

const languageIds = new Set(["rust", "cpp", "java", "typescript", "python", "go"]);
const MAX_DOCUMENT_BYTES = 512 * 1024;

/** Owns the optional, workspace-scoped language-service WebSocket. */
export class LanguageServices {
  private packs: LanguagePack[] = [];
  private connection: Connection | null = null;
  private activeText = "";
  private nextRequestId = 1;

  constructor(
    private readonly workspaceId: string,
    private readonly onStatus: StatusHandler,
    private readonly onDiagnostics: DiagnosticsHandler,
  ) {}

  async refresh(): Promise<LanguagePack[]> {
    this.packs = await this.request<LanguagePack[]>(`/api/v1/workspaces/${this.workspaceId}/language-packs`);
    return this.packs;
  }

  async enable(languageId: string): Promise<LanguagePack> {
    if (!languageIds.has(languageId)) throw new Error("This language service is not available in this workbench build.");
    const existingPack = this.packs.find((item) => item.id === languageId);
    if (existingPack && !existingPack.supportedByTemplate) {
      throw new Error("This language pack is not listed for this workspace template.");
    }
    const pack = await this.request<LanguagePack>(
      `/api/v1/workspaces/${this.workspaceId}/language-packs/${encodeURIComponent(languageId)}`,
      { method: "PUT" },
    );
    this.packs = this.packs.map((item) => item.id === languageId ? pack : item);
    return pack;
  }

  async disable(languageId: string): Promise<void> {
    await this.request<unknown>(
      `/api/v1/workspaces/${this.workspaceId}/language-packs/${encodeURIComponent(languageId)}`,
      { method: "DELETE" },
    );
    this.packs = this.packs.map((item) => item.id === languageId ? { ...item, selected: false } : item);
    if (this.connection?.languageId === languageId) this.close();
  }

  openDocument(languageId: string | null, path: string, text: string): void {
    if (new TextEncoder().encode(text).byteLength > MAX_DOCUMENT_BYTES) {
      this.close();
      this.onStatus("Diagnostics paused · file exceeds 512 KB");
      this.onDiagnostics([]);
      return;
    }
    if (!languageId || !languageIds.has(languageId)) {
      this.close();
      this.onStatus(languageId ? "No language server for this file type" : "Plain text");
      this.onDiagnostics([]);
      return;
    }
    const pack = this.packs.find((item) => item.id === languageId);
    if (!pack?.selected || !pack.installed) {
      this.close();
      this.onStatus(pack?.selected ? "Language service is not installed" : "Enable a language pack for diagnostics");
      this.onDiagnostics([]);
      return;
    }
    this.activeText = text;
    if (!this.connection || this.connection.languageId !== languageId || this.connection.socket.readyState > WebSocket.OPEN) {
      this.close();
      this.connect(languageId, path);
      return;
    }
    const connection = this.connection;
    if (connection.activePath === path) return;
    if (connection.activePath && connection.activePath !== path) {
      this.send(connection, "textDocument/didClose", { textDocument: { uri: this.documentUri(connection.activePath) } });
      connection.activePath = null;
    }
    connection.activePath = path;
    if (connection.initialized) this.didOpen(connection, path, text);
  }

  changeDocument(path: string, text: string): void {
    if (new TextEncoder().encode(text).byteLength > MAX_DOCUMENT_BYTES) {
      this.close();
      this.onStatus("Diagnostics paused · file exceeds 512 KB");
      return;
    }
    this.activeText = text;
    const connection = this.connection;
    if (!connection?.initialized || connection.activePath !== path) return;
    if (connection.changeTimer !== null) window.clearTimeout(connection.changeTimer);
    connection.changeTimer = window.setTimeout(() => this.flushChange(connection, path), 120);
  }

  saveDocument(path: string): void {
    const connection = this.connection;
    if (connection?.initialized && connection.activePath === path) {
      this.flushChange(connection, path);
      this.send(connection, "textDocument/didSave", { textDocument: { uri: this.documentUri(path) } });
    }
  }

  closeDocument(path: string): void {
    if (this.connection?.activePath === path) this.close();
  }

  close(): void {
    const connection = this.connection;
    this.connection = null;
    this.activeText = "";
    this.onDiagnostics([]);
    if (!connection) return;
    if (connection.changeTimer !== null) window.clearTimeout(connection.changeTimer);
    if (connection.initialized && connection.activePath) {
      this.send(connection, "textDocument/didClose", {
        textDocument: { uri: this.documentUri(connection.activePath) },
      });
    }
    if (connection.socket.readyState === WebSocket.OPEN) {
      if (connection.initialized) this.send(connection, "shutdown", {}, this.nextRequestId++);
      this.send(connection, "exit");
      connection.socket.close(1000, "workspace changed");
    } else if (connection.socket.readyState === WebSocket.CONNECTING) {
      connection.socket.close();
    }
  }

  private connect(languageId: string, path: string): void {
    const scheme = window.location.protocol === "https:" ? "wss:" : "ws:";
    const socket = new WebSocket(
      `${scheme}//${window.location.host}/api/v1/workspaces/${this.workspaceId}/language-services/${encodeURIComponent(languageId)}`,
    );
    const connection: Connection = {
      languageId,
      socket,
      initialized: false,
      initializeRequestId: this.nextRequestId++,
      activePath: path,
      version: 1,
      changeTimer: null,
    };
    this.connection = connection;
    this.onStatus(`Connecting ${languageId} language service…`);
    socket.addEventListener("open", () => {
      if (this.connection !== connection) return;
      socket.send(JSON.stringify({
        jsonrpc: "2.0",
        id: connection.initializeRequestId,
        method: "initialize",
        params: {
          processId: null,
          clientInfo: { name: "Jahaziel Workbench", version: "0.1.0" },
          capabilities: { textDocument: { publishDiagnostics: { relatedInformation: false } } },
        },
      }));
    });
    socket.addEventListener("message", (event) => {
      if (this.connection !== connection) return;
      try { this.handleMessage(connection, JSON.parse(String(event.data)) as LspMessage); }
      catch { this.onStatus("Language service sent an invalid message"); }
    });
    socket.addEventListener("error", () => {
      if (this.connection === connection) this.onStatus("Language service connection failed");
    });
    socket.addEventListener("close", () => {
      if (this.connection === connection) {
        this.connection = null;
        this.onDiagnostics([]);
        this.onStatus("Language service disconnected");
      }
    });
  }

  private handleMessage(connection: Connection, message: LspMessage): void {
    if (message.jsonrpc !== "2.0") throw new Error("invalid JSON-RPC version");
    if (message.method === "textDocument/publishDiagnostics") {
      this.publishDiagnostics(message.params || {});
      return;
    }
    if (message.method === "jahaziel/sessionClosed") {
      this.onStatus("Workspace stopped · language service closed");
      connection.socket.close(1000, "workspace stopped");
      return;
    }
    if (message.method) {
      if (Object.hasOwn(message, "id")) this.respondToServerRequest(connection, message);
      return;
    }
    if (message.id === connection.initializeRequestId && !connection.initialized) {
      if (message.error) {
        this.onStatus(`Language service initialization failed: ${message.error.message || "unknown error"}`);
        connection.socket.close(1011, "initialization failed");
        return;
      }
      connection.initialized = true;
      this.send(connection, "initialized", {});
      this.onStatus(`${connection.languageId} language service ready`);
      if (connection.activePath) this.didOpen(connection, connection.activePath, this.activeText);
    }
  }

  private respondToServerRequest(connection: Connection, message: LspMessage): void {
    const id = message.id;
    switch (message.method) {
      case "workspace/configuration": {
        const items = Array.isArray(message.params?.items) ? message.params.items : [];
        this.sendResponse(connection, id, items.map(() => null));
        return;
      }
      case "workspace/workspaceFolders":
        this.sendResponse(connection, id, [{
          uri: `jahaziel://workspace/${this.workspaceId}/`,
          name: "workspace",
        }]);
        return;
      case "workspace/applyEdit":
        this.sendResponse(connection, id, { applied: false, failureReason: "Workspace edits require user review." });
        return;
      case "client/registerCapability":
      case "client/unregisterCapability":
      case "window/workDoneProgress/create":
      case "workspace/semanticTokens/refresh":
      case "workspace/codeLens/refresh":
      case "workspace/inlayHint/refresh":
      case "window/showMessageRequest":
        this.sendResponse(connection, id, null);
        return;
      default:
        this.sendResponse(connection, id, undefined, {
          code: -32601,
          message: "This language-service request is not supported by Jahaziel.",
        });
    }
  }

  private publishDiagnostics(params: Record<string, unknown>): void {
    if (typeof params.uri !== "string") return;
    const path = this.pathFromUri(params.uri);
    if (!path || path !== this.connection?.activePath) return;
    const entries = Array.isArray(params.diagnostics) ? params.diagnostics : [];
    const diagnostics = entries.flatMap((entry): LanguageDiagnostic[] => {
      if (entry === null || typeof entry !== "object") return [];
      const item = entry as Record<string, unknown>;
      const range = item.range as { start?: { line?: number; character?: number } } | undefined;
      const start = range?.start;
      const severity = item.severity === 1 ? "Error" : item.severity === 2 ? "Warning" : item.severity === 3 ? "Information" : "Hint";
      return [{
        path,
        line: Number.isInteger(start?.line) ? Number(start?.line) + 1 : 1,
        column: Number.isInteger(start?.character) ? Number(start?.character) + 1 : 1,
        severity,
        message: typeof item.message === "string" ? item.message : "Language diagnostic",
      }];
    });
    this.onDiagnostics(diagnostics);
  }

  private didOpen(connection: Connection, path: string, text: string): void {
    connection.version = 1;
    this.send(connection, "textDocument/didOpen", {
      textDocument: {
        uri: this.documentUri(path),
        languageId: this.documentLanguageId(connection.languageId, path),
        version: connection.version,
        text,
      },
    });
  }

  private documentLanguageId(languageId: string, path: string): string {
    const lower = path.toLowerCase();
    if (languageId === "typescript") {
      if (lower.endsWith(".tsx")) return "typescriptreact";
      if (lower.endsWith(".jsx")) return "javascriptreact";
      if (/\.(js|mjs|cjs)$/.test(lower)) return "javascript";
    }
    return languageId;
  }

  private flushChange(connection: Connection, path: string): void {
    if (connection.changeTimer !== null) window.clearTimeout(connection.changeTimer);
    connection.changeTimer = null;
    if (this.connection !== connection || !connection.initialized || connection.activePath !== path) return;
    connection.version += 1;
    this.send(connection, "textDocument/didChange", {
      textDocument: { uri: this.documentUri(path), version: connection.version },
      contentChanges: [{ text: this.activeText }],
    });
  }

  private send(connection: Connection, method: string, params?: unknown, id?: string | number): void {
    if (connection.socket.readyState !== WebSocket.OPEN) return;
    const message: Record<string, unknown> = { jsonrpc: "2.0", method };
    if (id !== undefined) message.id = id;
    if (params !== undefined) message.params = params;
    connection.socket.send(JSON.stringify(message));
  }

  private sendResponse(connection: Connection, id: string | number | null | undefined, result: unknown, error?: { code: number; message: string }): void {
    if (connection.socket.readyState !== WebSocket.OPEN) return;
    const message: Record<string, unknown> = { jsonrpc: "2.0", id: id ?? null };
    if (error) message.error = error;
    else message.result = result;
    connection.socket.send(JSON.stringify(message));
  }

  private documentUri(path: string): string {
    const encoded = path.split("/").map((segment) => encodeURIComponent(segment)).join("/");
    return `jahaziel://workspace/${this.workspaceId}/${encoded}`;
  }

  private pathFromUri(value: string): string | null {
    try {
      const uri = new URL(value);
      if (uri.protocol !== "jahaziel:" || uri.hostname !== "workspace") return null;
      const segments = uri.pathname.split("/").filter(Boolean).map((segment) => decodeURIComponent(segment));
      if (segments.shift() !== this.workspaceId || segments.length === 0 || segments.some((part) => part === "." || part === ".." || part.includes("\\"))) return null;
      return segments.join("/");
    } catch { return null; }
  }

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    let response: Response;
    try {
      response = await fetch(path, {
        credentials: "same-origin",
        ...init,
      });
    } catch {
      throw new Error("The Jahaziel server could not be reached.");
    }
    if (!response.ok) {
      let detail = "Request failed";
      try {
        const body = await response.json() as { message?: string; code?: string };
        detail = body.message || body.code || detail;
      } catch { /* Keep the response status. */ }
      throw new Error(`${response.status} ${detail}`);
    }
    if (response.status === 204) return null as T;
    return await response.json() as T;
  }
}
