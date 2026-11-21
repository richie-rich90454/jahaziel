/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import dev.jahaziel.datatools.SearchIndex;
import dev.jahaziel.ops.AuditQueryService;
import dev.jahaziel.ops.GdprService;
import dev.jahaziel.ops.RestoreDrillService;
import dev.jahaziel.ops.ScheduledBackupService;
import dev.jahaziel.ops.SupportBundleService;
import dev.jahaziel.workspace.domain.WorkspaceId;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** CLI verbs that delegate to the operational and data-tool services. */
final class OpsCommands {
    private OpsCommands() {}

    /**
     * Dispatches the {@code ops} command group.
     *
     * @param args the full argument array; {@code args[1]} names the subcommand
     * @param out standard output
     * @param err standard error
     * @return the exit code
     * @throws IOException when a local file operation fails
     */
    static int dispatch(String[] args, PrintStream out, PrintStream err) throws IOException {
        if (args.length < 2) {
            err.println("jahaziel ops: expected a subcommand");
            printUsage(err);
            return 2;
        }
        Path jar = Cli.jarDir();
        Path data = Cli.dataDir(args, jar);
        String command = args[1];
        if ("support-bundle".equals(command)) {
            return supportBundle(jar, data, out);
        }
        if ("backup".equals(command)) {
            return backup(data, args, out, err);
        }
        if ("restore-drill".equals(command)) {
            return restoreDrill(data, out);
        }
        if ("audit".equals(command)) {
            if (args.length < 3 || !"query".equals(args[2])) {
                err.println("jahaziel ops: audit needs 'query'");
                return 2;
            }
            return auditQuery(data, args, out, err);
        }
        if ("export-user".equals(command)) {
            if (args.length < 3) {
                err.println("jahaziel ops: export-user needs a user id");
                return 2;
            }
            return exportUser(data, args[2], out, err);
        }
        if ("delete-user".equals(command)) {
            if (args.length < 3) {
                err.println("jahaziel ops: delete-user needs a user id");
                return 2;
            }
            return deleteUser(data, args[2], Cli.flag(args, "--confirm"), out, err);
        }
        if ("reindex".equals(command)) {
            return reindex(data, args, out, err);
        }
        if ("capabilities".equals(command)) {
            return capabilities(out);
        }
        err.println("jahaziel ops: unknown subcommand: " + command);
        printUsage(err);
        return 2;
    }

    private static void printUsage(PrintStream err) {
        err.println(
                "jahaziel ops: support-bundle | backup | restore-drill | audit query |"
                        + " export-user <id> | delete-user <id> --confirm 'DELETE <id>' |"
                        + " reindex [--workspace=<uuid>] | capabilities");
    }

    private static int supportBundle(Path jar, Path data, PrintStream out) {
        SupportBundleService service = new SupportBundleService(data);
        Map<String, String> config = Cli.parseDotEnv(jar.resolve(".env"));
        Map<String, String> health = new LinkedHashMap<String, String>();
        health.put("version", Cli.version());
        health.put("offline", Boolean.toString(Cli.isOffline()));
        List<String> auditTail = new AuditQueryService().auditTail(data, 50);
        Path bundle = service.createBundle(config, health, auditTail);
        out.println(
                "jahaziel: support bundle: "
                        + bundle
                        + " (verified="
                        + service.verify(bundle)
                        + ", secrets redacted)");
        return 0;
    }

    private static int backup(Path data, String[] args, PrintStream out, PrintStream err)
            throws IOException {
        String sourceFlag = Cli.flag(args, "--source");
        Path source = sourceFlag == null ? data.resolve("workspaces") : Paths.get(sourceFlag);
        if (!Files.isDirectory(source)) {
            err.println("jahaziel ops: source directory is missing: " + source);
            return 1;
        }
        ScheduledBackupService service = new ScheduledBackupService(data);
        Path archive = service.backup(source);
        out.println(
                "jahaziel: snapshot: "
                        + archive
                        + " (sha256="
                        + Cli.sha256(archive)
                        + ", RPO="
                        + ScheduledBackupService.RPO_MINUTES
                        + "m, RTO="
                        + ScheduledBackupService.RTO_MINUTES
                        + "m)");
        return 0;
    }

    private static int restoreDrill(Path data, PrintStream out) {
        RestoreDrillService.RestoreReport report = new RestoreDrillService(data).drill();
        if (!report.success()) {
            out.println("jahaziel: restore drill failed: " + report.reason());
            return 1;
        }
        out.println(
                "jahaziel: restore drill ok: "
                        + report.backup()
                        + " (files="
                        + report.files()
                        + ", elapsed="
                        + report.elapsedMillis()
                        + "ms, withinRto="
                        + report.withinRto()
                        + ")");
        return 0;
    }

    private static int auditQuery(Path data, String[] args, PrintStream out, PrintStream err) {
        String format = Cli.flag(args, "--format");
        String useFormat =
                format == null ? "csv" : format.trim().toLowerCase(java.util.Locale.ROOT);
        if (!"csv".equals(useFormat) && !"json".equals(useFormat)) {
            err.println("jahaziel ops: --format must be csv or json");
            return 2;
        }
        AuditQueryService service = new AuditQueryService();
        List<AuditQueryService.AuditEntry> entries = service.readDatabase(data);
        AuditQueryService.ChainStatus chain = service.verifyChain(entries, "genesis");
        AuditQueryService.AuditFilter filter = new AuditQueryService.AuditFilter();
        String actor = Cli.flag(args, "--actor");
        String action = Cli.flag(args, "--action");
        if (actor != null) {
            filter.actor(actor);
        }
        if (action != null) {
            filter.action(action);
        }
        List<AuditQueryService.AuditEntry> found = service.query(entries, filter);
        if ("json".equals(useFormat)) {
            out.println(service.toJson(found));
        } else {
            out.print(service.toCsv(found));
        }
        out.println("# chain=" + (chain.valid() ? "intact" : "broken") + " - " + chain.detail());
        return 0;
    }

    private static int exportUser(Path data, String userId, PrintStream out, PrintStream err) {
        if (!simpleId(userId)) {
            err.println("jahaziel ops: userId must be a simple identifier");
            return 2;
        }
        GdprService service = new GdprService(data);
        // GdprService resolves the profile and audits from the real stores when these are empty.
        Path archive =
                service.exportUser(
                        userId,
                        new LinkedHashMap<String, String>(),
                        new ArrayList<AuditQueryService.AuditEntry>());
        out.println("jahaziel: GDPR export: " + archive + " (userId=" + userId + ")");
        return 0;
    }

    private static int deleteUser(
            Path data, String userId, String confirmation, PrintStream out, PrintStream err) {
        if (!simpleId(userId)) {
            err.println("jahaziel ops: userId must be a simple identifier");
            return 2;
        }
        String expected = "DELETE " + userId;
        if (confirmation == null || !confirmation.equals(expected)) {
            err.println("jahaziel ops: typed confirmation required: " + expected);
            return 2;
        }
        GdprService service = new GdprService(data);
        String pseudonym =
                service.deleteUser(
                        userId, confirmation, new ArrayList<AuditQueryService.AuditEntry>());
        out.println("jahaziel: deleted " + userId + " (retained as " + pseudonym + ")");
        return 0;
    }

    private static int reindex(Path data, String[] args, PrintStream out, PrintStream err) {
        String workspaceFlag = Cli.flag(args, "--workspace");
        List<UUID> ids = new ArrayList<UUID>();
        if (workspaceFlag != null && !workspaceFlag.trim().isEmpty()) {
            try {
                ids.add(UUID.fromString(workspaceFlag.trim()));
            } catch (IllegalArgumentException invalid) {
                err.println("jahaziel ops: --workspace must be a UUID");
                return 2;
            }
        } else {
            Path workspaces = data.resolve("workspaces");
            if (Files.isDirectory(workspaces)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(workspaces)) {
                    for (Path entry : stream) {
                        try {
                            ids.add(UUID.fromString(entry.getFileName().toString()));
                        } catch (IllegalArgumentException notAWorkspace) {
                            // Ignore non-workspace directories.
                        }
                    }
                } catch (IOException failure) {
                    err.println("jahaziel ops: cannot list workspaces: " + failure.getMessage());
                    return 1;
                }
            }
        }
        if (ids.isEmpty()) {
            out.println("jahaziel: reindex: no workspaces found");
            return 0;
        }
        SearchIndex index = new SearchIndex(data, Arrays.asList(".git", "node_modules", "*.tmp"));
        for (int i = 0; i < ids.size(); i++) {
            UUID id = ids.get(i);
            int updated = index.rebuild(new WorkspaceId(id));
            out.println("jahaziel: reindexed " + id + " (" + updated + " files)");
        }
        return 0;
    }

    private static int capabilities(PrintStream out) {
        String[] names = {"opencode", "ripgrep", "language-packs", "updates"};
        if (Cli.isOffline()) {
            for (int i = 0; i < names.length; i++) {
                out.println("capability: " + names[i] + " - unavailable (offline)");
            }
        } else {
            for (int i = 0; i < names.length; i++) {
                out.println("capability: " + names[i] + " - available");
            }
        }
        return 0;
    }

    private static boolean simpleId(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]+");
    }
}
