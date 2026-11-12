<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Configuration

Last verified against version 0.1.0-SNAPSHOT.

`.env` lives beside the jar. Real environment variables always win over
`.env` values. Login stays off until all five `BASIS_*` values are set.

| Key | Default | Meaning |
|---|---|---|
| `BASIS_AUTH_ISSUER` | — | DevConnect IdP issuer URL |
| `BASIS_REDIRECT_URI` | — | Registered callback URL |
| `BASIS_CLIENT_ID` | — | Confidential client id |
| `BASIS_CLIENT_SECRET` | — | Confidential client secret (`init` generates one) |
| `BASIS_RESOURCE` | — | API audience |
| `PORT` | `11451` | API port (`--port` overrides) |
| `JAHAZIEL_GATEWAY_PORT` | `8081` | Legacy standalone gateway socket (`--gateway-port` overrides; off unless enabled) |
| `JAHAZIEL_DOMAIN` | `localhost` | Public domain for gateway URLs and token issuer (`--domain` overrides; browsers resolve `*.localhost` to loopback, so local runs need no setup; set a real domain with DNS for LAN/server runs) |
| `JAHAZIEL_LOG_LEVEL` | `INFO` | Root log level |
| `JAHAZIEL_DEMO` | `false` | Legacy demo flag (prefer `--demo`) |
| `JAHAZIEL_BACKEND` | `local` | `local` is the only selectable backend in this release; Firecracker is retained for separate verification and is not selectable |
| `JAHAZIEL_LANGUAGE_TOOLS_NODE_EXECUTABLE` | `/usr/bin/node` | Existing Node.js executable used by explicitly enabled TypeScript and Python language services; Jahaziel never installs it |

Flags: `--port=N`, `--gateway-port=N` (default `8081`),
`--domain=HOST`, `--data-dir=PATH`, `--demo`. See the
[CLI reference](../reference/cli.md).

## Debug adapters

Debugging is disabled for a language until an administrator configures a
compatible Debug Adapter Protocol (DAP) adapter. Jahaziel does not download or
install debugger adapters. Add the catalog to the external `application.yml`
in the directory from which the jar is launched, and provide an absolute Linux
executable path plus the exact fixed arguments verified for that adapter:

```yaml
jahaziel:
  debug:
    adapters:
      node:
        version: "<administrator-pinned-version>"
        transport: STDIO
        command:
          - "/absolute/path/to/verified-node-dap-adapter"
          - "<verified-stdio-argument>"
```

Replace both placeholders with the installed adapter's pinned version and
documented command. Configure only adapters installed and maintained by the
server administrator. The browser cannot select an executable, adapter
arguments, or transport. Add entries using `python`, `java`, `cpp`, `go`, or
`rust` for the other supported language IDs.

`STDIO` is the default and recommended transport. If an adapter supports only
TCP, set `transport: TCP` and include `{port}` in one fixed command argument;
Jahaziel substitutes an available local port. Verify that the adapter binds
only to loopback before enabling TCP. A DAP adapter runs under the Jahaziel
service account with the same trust boundary as workspace processes.

After configuring adapters, restart Jahaziel and open the **Debug** panel in a
running workspace. The Python template creates a workspace-local `.venv` and
installs a pinned `debugpy` package during its visible setup operation. Setup
needs access to PyPI. Configure the server-side Python DAP adapter with a
pinned `debugpy` installation for the same supported release.

The starter templates mark their long-running preview tasks as debuggable.
Python and Java receive dynamically assigned loopback debug ports from
Jahaziel. Node uses its process-attach support. Delve and LLDB attach to live
task process ids for Go, C/C++, and Rust. Linux Yama policy may restrict a
debugger process from attaching to a sibling process, even when both run as
the same service account. Confirm that the server host permits the selected
adapter to attach; see the [Linux kernel Yama documentation](https://docs.kernel.org/admin-guide/LSM/Yama.html).

The workbench can load a deliberately small `.vscode/launch.json` subset. The
document must have version `0.2.0` and a `configurations` array. Launch
entries may use only `name`, `type`, `request`, `program`, `mainClass`,
`cwd`, `args`, and `stopOnEntry`; `request` must be `launch`. Attach entries
may contain only `name`, `type`, and `request`; `request` must be `attach`.
Types map as follows: `node` or `pwa-node`, `python` or `debugpy`, `java`,
`cppdbg`, `go`, and `lldb` (Rust). Java launch uses `mainClass`; other
launch types use `program`. `cwd` and `program` must be workspace-relative
paths. Attach entries select a live Jahaziel task target and cannot specify a
process id, host, port, executable, or command. The loader rejects environment
variables, pre-launch tasks, arbitrary commands, unknown fields, duplicate
names, and absolute or escaping paths.

Select **Load .vscode/launch.json**, then choose a supported entry. Launch
values populate the launch form and are validated again by Jahaziel. Attach
entries select the matching language's live task target. This is a narrow
compatibility subset, not full VS Code launch configuration support.
