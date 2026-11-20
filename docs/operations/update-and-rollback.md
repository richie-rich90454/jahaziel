<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Update and rollback

Applies to the single jar install (`java -jar jahaziel.jar`). The jar
directory holds `.env` plus `data/`. A candidate jar is staged under
`<jar>/data/update/`; staging never touches the running jar. The swap
happens on the **next start**, when the previous jar is saved under
`<jar>/data/update/rollback/` for rollback.

## Get the candidate jar

- **Jar upload**: obtain the packaged `jahaziel.jar` and its `SHA256SUMS`
  from the Linux CI artifact and upload both beside the live jar. Keep the
  manifest next to the jar so `update` verifies it automatically.
- **Repo clone**: clone the repository at the pinned commit and build with
  the repo-local toolchain (no global installs):

  ```bash
  git clone <repo-url> && cd jahaziel
  pnpm --dir web install --frozen-lockfile
  pnpm --dir web run build
  ./mvnw -B verify -Psmoke        # Windows: .\dev.ps1 build
  # jar: target/jahaziel.jar
  ```

  Requires JDK 25, Node 24+, and pnpm 12.14.0 already on the build host;
  upload the resulting jar to the target host.

## Safe upgrade procedure

1. Record the running version: `java -jar jahaziel.jar --version`.
2. Stop the service (table below).
3. Back up **before** upgrading: `java -jar jahaziel.jar backup`. It writes
   `jahaziel-backup-<stamp>.tar.gz` beside the jar, prints a SHA-256, and
   includes `.env` and `data/`. Copy the archive off host; use
   `--no-secrets` only when the archive leaves trusted storage. See
   [Backup](./backup.md). Leave the service stopped.
4. Preview the change:
   `java -jar jahaziel.jar update --dry-run --to <candidate.jar>`. It prints
   the target version, the stage directory, and whether it will verify or
   generate `SHA256SUMS`. No files change.
5. Stage it: `java -jar jahaziel.jar update --to <candidate.jar>`. Confirm
   `java -jar jahaziel.jar update --status` shows the pending version.
6. Start the service (table below). On boot the staged jar is verified and
   swapped in, and the previous jar is saved to
   `<jar>/data/update/rollback/<old-version>/jahaziel.jar`. `update --status`
   shows the rollback target. If the staged jar fails verification it is
   discarded and the old jar keeps running — re-stage with a valid manifest.
7. Run `java -jar jahaziel.jar doctor`; every check must pass.
8. Confirm `curl -fsS http://localhost:11451/actuator/health` returns 200,
   then work through [Verification](./verification.md).

## Service restart per OS

| OS | Stop | Start |
|---|---|---|
| Linux (systemd) | `sudo systemctl stop jahaziel` | `sudo systemctl start jahaziel` |
| macOS (launchd) | `sudo launchctl bootout system/dev.jahaziel` | `sudo launchctl bootstrap system /Library/LaunchDaemons/dev.jahaziel.plist` |
| Windows (WinSW) | `net stop jahaziel` | `net start jahaziel` |

`systemctl restart jahaziel` and `launchctl kickstart -k system/dev.jahaziel`
are equivalent restarts. The launchd and WinSW templates are not
release-verified; see [acceptance](./acceptance.md).

## Rollback

1. Stop the service (table above).
2. Restore the previous jar:
   `java -jar jahaziel.jar update --rollback`. This copies
   `<jar>/data/update/rollback/<version>/jahaziel.jar` back over
   `jahaziel.jar`. The manual equivalent is to copy that file over the live
   jar yourself.
3. If the new version migrated `data/jahaziel.db`, also restore the
   pre-upgrade `.env` and `data/` from the backup archive: move the current
   `.env` and `data/` into a hold directory, then
   `java -jar jahaziel.jar restore <archive>`. Never start an old jar
   against data a newer version may have migrated.
4. Start the service, run `doctor`, confirm health returns 200, and repeat
   [Verification](./verification.md).
5. Collect a bug report (below) before trying the upgrade again.

## Bug report collection

- `java -jar jahaziel.jar support-bundle` (or `ops support-bundle`) writes
  `<jar>/data/support/support-<stamp>.zip`, verified and with secrets
  redacted. It contains a sanitized `config.txt`, `health.json`,
  `audit-tail.txt`, rotated logs, and `manifest.sha256`.
- `java -jar jahaziel.jar logs [--follow] [--level=LEVEL]` tails
  `data/logs/control-plane.log`; include the lines around the failure.
- Include the error code, correlation id, and timestamp for each failure;
  see [errors](../reference/errors.md).
- Never attach `.env`, `data/keys/`, a raw database file, or unredacted
  tokens and passwords. Confirm the bundle is redacted before sharing it.
