/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.language.api.LanguagePackInstallException;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LanguagePackServiceImplTest {
  private static final String OLD_OS = System.getProperty("os.name");
  private static final String OLD_ARCH = System.getProperty("os.arch");

  @TempDir Path temporary;

  @AfterEach
  void restoreHostProperties() {
    restore("os.name", OLD_OS);
    restore("os.arch", OLD_ARCH);
  }

  @Test
  void catalogAndWorkspaceReadsDoNotFetchOrCreatePackFiles() {
    HttpClient http = mock(HttpClient.class);
    LanguagePackServiceImpl service =
        new LanguagePackServiceImpl(
            temporary.resolve("cache"), http, testCatalog(gzip(serverBytes())));
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());

    assertEquals(
        List.of("rust", "cpp", "java"), service.catalog().stream().map(pack -> pack.id()).toList());
    assertTrue(service.forWorkspace(workspace).stream().noneMatch(pack -> pack.selected()));
    assertFalse(Files.exists(temporary.resolve("cache/rust")));
    assertFalse(Files.exists(temporary.resolve("cache/cpp")));
    assertFalse(Files.exists(temporary.resolve("cache/java")));
  }

  @Test
  void defaultGoPackIsPinnedForBothLinuxServerArchitectures() {
    System.setProperty("os.name", "Linux");
    System.setProperty("os.arch", "amd64");
    LanguagePackServiceImpl service =
        new LanguagePackServiceImpl(
            temporary.resolve("cache").toString(), mock(HttpClient.class), "/usr/bin/node");

    var x64 =
        service.catalog().stream().filter(pack -> pack.id().equals("go")).findFirst().orElseThrow();

    assertEquals("gopls-v0.24.0-go1.27.3", x64.version());
    assertEquals(67_000_000, x64.downloadBytes());
    assertEquals(List.of("aarch64", "x86_64"), x64.supportedArchitectures());
    assertEquals(
        List.of("SHA-256: 65efbc80fdc1fa2cd2631cad5b481777a68e8725f34e89f50fc6ac714e379b40"),
        x64.checksums());
    assertTrue(x64.installationNote().contains("module downloads"));

    System.setProperty("os.arch", "aarch64");
    var arm64 =
        service.catalog().stream().filter(pack -> pack.id().equals("go")).findFirst().orElseThrow();
    assertEquals(64_000_000, arm64.downloadBytes());
    assertEquals(
        List.of("SHA-256: a06e99309dbcbdca1a8ccd2282c3ee146e55930886e26b4f1193269587165f49"),
        arm64.checksums());
    assertFalse(arm64.installed());
  }

  @Test
  void goSelectionBuildsInAppPackAndUsesWorkspaceScopedGoCaches() throws Exception {
    Assumptions.assumeTrue(
        System.getProperty("os.name", "").toLowerCase().contains("linux"),
        "language packs run only on the Linux server target");
    Assumptions.assumeTrue(
        System.getProperty("os.arch", "").matches("(?i)amd64|x86_64"),
        "fixture SDK targets Linux x64");
    System.setProperty("os.name", "Linux");
    System.setProperty("os.arch", "amd64");
    byte[] archive = tarGzip("go/bin/go", "fixture SDK".getBytes(StandardCharsets.UTF_8));
    String url = "https://example.invalid/go.tar.gz";
    var asset =
        new LanguagePackServiceImpl.Asset(
            "go.tar.gz", url, sha256(archive), archive.length, "tar.gz", "", "toolchain");
    var definition =
        new LanguagePackServiceImpl.Definition("go", "gopls · Go", "test", Map.of("x86_64", asset));
    Path cache = temporary.resolve("cache");
    GoPackBuilder builder =
        (goExecutable, staging) -> {
          assertTrue(Files.isExecutable(goExecutable));
          assertTrue(goExecutable.startsWith(staging));
          Files.createDirectories(staging.resolve("bin"));
          Files.writeString(staging.resolve("bin/gopls"), "fixture gopls", StandardCharsets.UTF_8);
        };
    LanguagePackServiceImpl service =
        new LanguagePackServiceImpl(
            cache, httpReturning(archive), List.of(definition), Path.of("node"), builder);
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    Path workspaceHome = temporary.resolve("workspace");
    Files.createDirectories(workspaceHome);

    var pack = service.select(workspace, "go");
    Path state = cache.resolve("state").resolve(workspace.value().toString()).resolve("go");
    List<String> command = service.launchCommandFor(workspace, "go", state);
    Map<String, String> environment =
        service.launchEnvironmentFor(workspace, "go", workspaceHome, state);

    assertTrue(pack.installed());
    assertTrue(pack.selected());
    assertTrue(service.supportsLanguageService("go"));
    assertEquals(
        cache.resolve("go/test/x86_64/bin/gopls").toRealPath().toString(), command.getFirst());
    assertEquals("serve", command.get(1));
    assertTrue(Files.isExecutable(cache.resolve("go/test/x86_64/toolchain/go/bin/go")));
    assertTrue(Files.isRegularFile(cache.resolve("go/test/x86_64/.jahaziel-pack")));
    assertEquals(
        cache.resolve("go/test/x86_64/toolchain/go").toRealPath().toString(),
        environment.get("GOROOT"));
    assertEquals(state.resolve("go").toString(), environment.get("GOPATH"));
    assertEquals(state.resolve("go/pkg/mod").toString(), environment.get("GOMODCACHE"));
    assertEquals(state.resolve("go-build-cache").toString(), environment.get("GOCACHE"));
    assertEquals("local", environment.get("GOTOOLCHAIN"));
    assertEquals("off", environment.get("GOENV"));
    assertEquals("-mod=readonly", environment.get("GOFLAGS"));
    assertEquals("https://proxy.golang.org", environment.get("GOPROXY"));
    assertEquals("sum.golang.org", environment.get("GOSUMDB"));
    WorkspaceId anotherWorkspace = new WorkspaceId(UUID.randomUUID());
    Path anotherState =
        cache.resolve("state").resolve(anotherWorkspace.value().toString()).resolve("go");
    Map<String, String> anotherEnvironment =
        service.launchEnvironmentFor(anotherWorkspace, "go", workspaceHome, anotherState);
    assertFalse(environment.get("GOMODCACHE").equals(anotherEnvironment.get("GOMODCACHE")));
  }

  @Test
  void explicitSelectionDownloadsVerifiesAndPublishesOneSharedPack() throws Exception {
    Assumptions.assumeTrue(
        System.getProperty("os.name", "").toLowerCase().contains("linux"),
        "language packs run only on the Linux server target");
    Assumptions.assumeTrue(
        System.getProperty("os.arch", "").matches("(?i)amd64|x86_64"),
        "fixture artifact targets Linux x64");
    byte[] archive = gzip(serverBytes());
    LanguagePackServiceImpl.Definition definition = definition(archive);
    HttpClient http = httpReturning(archive);
    LanguagePackServiceImpl service =
        new LanguagePackServiceImpl(temporary.resolve("cache"), http, List.of(definition));
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());

    var selected = service.select(workspace, "rust");

    assertTrue(selected.installed());
    assertTrue(selected.selected());
    assertTrue(service.forWorkspace(workspace).getFirst().installed());
    assertTrue(service.forWorkspace(workspace).getFirst().selected());
    assertTrue(Files.isRegularFile(temporary.resolve("cache/rust/test/x86_64/rust-analyzer")));
    assertTrue(
        service.report().bytes()
            > Files.size(temporary.resolve("cache/rust/test/x86_64/rust-analyzer")));
  }

  @Test
  void badChecksumLeavesNoSelectionOrPublishedVersion() throws Exception {
    Assumptions.assumeTrue(
        System.getProperty("os.name", "").toLowerCase().contains("linux"),
        "language packs run only on the Linux server target");
    Assumptions.assumeTrue(
        System.getProperty("os.arch", "").matches("(?i)amd64|x86_64"),
        "fixture artifact targets Linux x64");
    byte[] archive = gzip(serverBytes());
    LanguagePackServiceImpl.Definition correct = definition(archive);
    LanguagePackServiceImpl.Definition wrong =
        new LanguagePackServiceImpl.Definition(
            correct.id(),
            correct.name(),
            correct.version(),
            Map.of(
                "x86_64",
                new LanguagePackServiceImpl.Asset(
                    correct.assets().get("x86_64").archive(),
                    correct.assets().get("x86_64").url(),
                    "0".repeat(64),
                    correct.assets().get("x86_64").downloadBytes(),
                    correct.assets().get("x86_64").format())));
    LanguagePackServiceImpl service =
        new LanguagePackServiceImpl(
            temporary.resolve("cache"), httpReturning(archive), List.of(wrong));
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());

    assertThrows(LanguagePackInstallException.class, () -> service.select(workspace, "rust"));

    assertFalse(Files.exists(temporary.resolve("cache/rust/test/x86_64")));
    assertFalse(Files.exists(temporary.resolve("cache/selections/" + workspace.value() + ".txt")));
    assertFalse(Files.exists(temporary.resolve("cache/rust/test.installing")));
  }

  @Test
  void pruneIsDryRunByDefaultAndRemovesOnlyUnselectedVersions() throws Exception {
    HttpClient http = mock(HttpClient.class);
    LanguagePackServiceImpl.Definition definition = definition(gzip(serverBytes()));
    Path cache = temporary.resolve("cache");
    LanguagePackServiceImpl service = new LanguagePackServiceImpl(cache, http, List.of(definition));
    String architecture = runtimeArchitecture();
    Path version = cache.resolve("rust/test/" + architecture);
    Files.createDirectories(version);
    Files.writeString(version.resolve("payload"), "cached", StandardCharsets.UTF_8);

    var dryRun = service.prune(true);
    assertTrue(dryRun.dryRun());
    assertEquals(List.of("rust:test:" + architecture), dryRun.versions());
    assertTrue(Files.exists(version.resolve("payload")));

    Files.createDirectories(cache.resolve("selections"));
    Files.writeString(cache.resolve("selections/workspace.txt"), "rust\n", StandardCharsets.UTF_8);
    assertTrue(service.prune(false).versions().isEmpty());
    assertTrue(Files.exists(version.resolve("payload")));

    Files.delete(cache.resolve("selections/workspace.txt"));
    var removed = service.prune(false);
    assertFalse(removed.dryRun());
    assertEquals(List.of("rust:test:" + architecture), removed.versions());
    assertFalse(Files.exists(version));
  }

  @Test
  void pruneReclaimsObsoleteVersionsWithoutRemovingTheSelectedCurrentVersion() throws Exception {
    HttpClient http = mock(HttpClient.class);
    LanguagePackServiceImpl.Definition definition = definition(gzip(serverBytes()));
    Path cache = temporary.resolve("cache");
    LanguagePackServiceImpl service = new LanguagePackServiceImpl(cache, http, List.of(definition));
    String architecture = runtimeArchitecture();
    Path oldVersion = cache.resolve("rust/older/" + architecture);
    Path currentVersion = cache.resolve("rust/test/" + architecture);
    Path selection = cache.resolve("selections/").resolve(UUID.randomUUID() + ".txt");
    Files.createDirectories(oldVersion);
    Files.createDirectories(currentVersion);
    Files.createDirectories(selection.getParent());
    Files.writeString(oldVersion.resolve("server"), "old", StandardCharsets.UTF_8);
    Files.writeString(currentVersion.resolve("server"), "current", StandardCharsets.UTF_8);
    Files.writeString(selection, "rust\n", StandardCharsets.UTF_8);

    var dryRun = service.prune(true);

    assertTrue(dryRun.dryRun());
    assertEquals(List.of("rust:older:" + architecture), dryRun.versions());
    assertTrue(Files.exists(oldVersion));
    assertTrue(Files.exists(currentVersion));

    var applied = service.prune(false);

    assertFalse(applied.dryRun());
    assertEquals(List.of("rust:older:" + architecture), applied.versions());
    assertFalse(Files.exists(oldVersion));
    assertTrue(Files.exists(currentVersion));
  }

  @Test
  void tarGzipPackIsVerifiedAndExtractedWithoutFollowingArchivePaths() throws Exception {
    System.setProperty("os.name", "Linux");
    System.setProperty("os.arch", "amd64");
    byte[] archive = tarGzip("bin/clangd", serverBytes(), 0755);
    LanguagePackServiceImpl.Definition definition =
        new LanguagePackServiceImpl.Definition(
            "cpp",
            "clangd",
            "tar-test",
            Map.of(
                "x86_64",
                new LanguagePackServiceImpl.Asset(
                    "clangd.tar.gz",
                    "https://example.invalid/clangd.tar.gz",
                    sha256(archive),
                    archive.length,
                    "tar.gz")));
    Path cache = temporary.resolve("cache");
    LanguagePackServiceImpl service =
        new LanguagePackServiceImpl(cache, httpReturning(archive), List.of(definition));
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());

    var selected = service.select(workspace, "cpp");

    Path executable = cache.resolve("cpp/tar-test/x86_64/bin/clangd");
    assertTrue(selected.installed());
    assertTrue(Files.isRegularFile(executable));
    if (Files.getFileStore(executable).supportsFileAttributeView("posix")) {
      assertTrue(Files.isExecutable(executable));
    }
    assertEquals("verified fake language server", Files.readString(executable));
  }

  @Test
  void tarGzipPackRejectsParentTraversalAndLeavesNoPublishedFiles() throws Exception {
    System.setProperty("os.name", "Linux");
    System.setProperty("os.arch", "amd64");
    byte[] archive = tarGzip("../../../../escape", serverBytes());
    LanguagePackServiceImpl.Definition definition =
        new LanguagePackServiceImpl.Definition(
            "cpp",
            "clangd",
            "tar-test",
            Map.of(
                "x86_64",
                new LanguagePackServiceImpl.Asset(
                    "clangd.tar.gz",
                    "https://example.invalid/clangd.tar.gz",
                    sha256(archive),
                    archive.length,
                    "tar.gz")));
    Path cache = temporary.resolve("cache");
    LanguagePackServiceImpl service =
        new LanguagePackServiceImpl(cache, httpReturning(archive), List.of(definition));
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());

    assertThrows(LanguagePackInstallException.class, () -> service.select(workspace, "cpp"));

    assertFalse(Files.exists(cache.resolve("escape")));
    assertFalse(Files.exists(cache.resolve("cpp/tar-test/x86_64")));
    assertFalse(Files.exists(cache.resolve("cpp/tar-test/tar-test.installing")));
    assertFalse(Files.exists(cache.resolve("selections/" + workspace.value() + ".txt")));
  }

  @Test
  void typescriptPackVerifiesBothArchivesAndUsesThePinnedRuntimePath() throws Exception {
    Path node = nodeExecutable();
    Assumptions.assumeTrue(node != null, "Node.js is available on PATH");
    System.setProperty("os.name", "Linux");
    System.setProperty("os.arch", "amd64");
    String serverUrl = "https://example.invalid/typescript-language-server.tgz";
    String runtimeUrl = "https://example.invalid/typescript.tgz";
    byte[] serverArchive =
        tarGzip(
            "package/lib/cli.mjs",
            "// fake TS language-server entrypoint".getBytes(StandardCharsets.UTF_8));
    byte[] runtimeArchive =
        tarGzip("package/lib/tsserver.js", "// fake TS runtime".getBytes(StandardCharsets.UTF_8));
    var serverAsset =
        new LanguagePackServiceImpl.Asset(
            "typescript-language-server.tgz",
            serverUrl,
            "",
            serverArchive.length,
            "npm.tar.gz",
            sha512(serverArchive),
            "node_modules/typescript-language-server");
    var runtimeAsset =
        new LanguagePackServiceImpl.Asset(
            "typescript.tgz",
            runtimeUrl,
            "",
            runtimeArchive.length,
            "npm.tar.gz",
            sha512(runtimeArchive),
            "node_modules/typescript");
    var definition =
        new LanguagePackServiceImpl.Definition(
            "typescript",
            "TypeScript Language Server",
            "test",
            Map.of("x86_64", serverAsset),
            List.of(runtimeAsset));
    Path cache = temporary.resolve("cache");
    var service =
        new LanguagePackServiceImpl(
            cache,
            httpReturningUrls(Map.of(serverUrl, serverArchive, runtimeUrl, runtimeArchive)),
            List.of(definition),
            node);
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());

    var selected = service.select(workspace, "typescript");
    List<String> command =
        service.launchCommandFor(
            workspace, "typescript", cache.resolve("state").resolve(workspace.value().toString()));
    var options = service.initializationOptionsFor(workspace, "typescript");

    Path installed = cache.resolve("typescript/test/x86_64/node_modules");
    assertTrue(selected.installed());
    assertTrue(selected.selected());
    assertTrue(selected.checksums().stream().allMatch(value -> value.startsWith("SHA-512: ")));
    assertTrue(Files.isRegularFile(installed.resolve("typescript-language-server/lib/cli.mjs")));
    assertTrue(Files.isRegularFile(installed.resolve("typescript/lib/tsserver.js")));
    assertEquals(node.toRealPath().toString(), command.getFirst());
    assertEquals(
        installed.resolve("typescript-language-server/lib/cli.mjs").toRealPath().toString(),
        command.get(1));
    assertEquals(
        installed.resolve("typescript/lib/tsserver.js").toRealPath().toString(),
        ((Map<?, ?>) options.get("tsserver")).get("path"));
  }

  @Test
  void npmPackRejectsPathsThatEscapeItsPackageRoot() throws Exception {
    Path node = nodeExecutable();
    Assumptions.assumeTrue(node != null, "Node.js is available on PATH");
    System.setProperty("os.name", "Linux");
    System.setProperty("os.arch", "amd64");
    byte[] archive = tarGzip("package/../../escape", serverBytes());
    String url = "https://example.invalid/pyright.tgz";
    var asset =
        new LanguagePackServiceImpl.Asset(
            "pyright.tgz",
            url,
            "",
            archive.length,
            "npm.tar.gz",
            sha512(archive),
            "node_modules/pyright");
    var definition =
        new LanguagePackServiceImpl.Definition(
            "python", "Pyright", "test", Map.of("x86_64", asset));
    Path cache = temporary.resolve("cache");
    var service =
        new LanguagePackServiceImpl(
            cache, httpReturningUrls(Map.of(url, archive)), List.of(definition), node);

    assertThrows(
        LanguagePackInstallException.class,
        () -> service.select(new WorkspaceId(UUID.randomUUID()), "python"));
    assertFalse(Files.exists(cache.resolve("escape")));
    assertFalse(Files.exists(cache.resolve("python/test/x86_64")));
  }

  @Test
  void javaLaunchCommandUsesOnlyTheSelectedVerifiedPackAndWorkspaceState() throws Exception {
    System.setProperty("os.name", "Linux");
    System.setProperty("os.arch", "amd64");
    String checksum = "a".repeat(64);
    LanguagePackServiceImpl.Definition definition =
        new LanguagePackServiceImpl.Definition(
            "java",
            "Eclipse JDT Language Server",
            "test",
            Map.of(
                "x86_64",
                new LanguagePackServiceImpl.Asset(
                    "jdtls.tar.gz",
                    "https://example.invalid/jdtls.tar.gz",
                    checksum,
                    10,
                    "tar.gz")));
    Path cache = temporary.resolve("cache");
    Path version = cache.resolve("java/test/x86_64");
    Path launcher = version.resolve("plugins/org.eclipse.equinox.launcher_test.jar");
    Path config = version.resolve("config_linux");
    Path state = cache.resolve("state").resolve(UUID.randomUUID().toString()).resolve("java");
    Files.createDirectories(launcher.getParent());
    Files.createDirectories(config);
    Files.createDirectories(state);
    Files.write(launcher, new byte[] {1, 2, 3});
    Files.writeString(
        version.resolve(".jahaziel-pack"),
        "java\ntest\n" + checksum + "\n",
        StandardCharsets.UTF_8);
    Files.createDirectories(cache.resolve("selections"));
    Files.createDirectories(cache.resolve("state"));
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    Files.writeString(cache.resolve("selections").resolve(workspace.value() + ".txt"), "java\n");
    LanguagePackServiceImpl service =
        new LanguagePackServiceImpl(cache, mock(HttpClient.class), List.of(definition));

    List<String> command = service.launchCommandFor(workspace, "java", state);

    assertTrue(command.getFirst().endsWith("java") || command.getFirst().endsWith("java.exe"));
    assertTrue(command.contains("-Declipse.application=org.eclipse.jdt.ls.core.id1"));
    assertTrue(command.contains("-Xmx768m"));
    assertTrue(command.contains(launcher.toRealPath().toString()));
    assertTrue(command.contains(config.toRealPath().toString()));
    assertTrue(command.contains(state.toRealPath().toString()));
    assertFalse(command.stream().anyMatch(argument -> argument.contains("example.invalid")));
  }

  @Test
  void javaLaunchCommandRejectsWorkspaceStateOutsideManagedCache() throws Exception {
    System.setProperty("os.name", "Linux");
    System.setProperty("os.arch", "amd64");
    String checksum = "b".repeat(64);
    LanguagePackServiceImpl.Definition definition =
        new LanguagePackServiceImpl.Definition(
            "java",
            "Eclipse JDT Language Server",
            "test",
            Map.of(
                "x86_64",
                new LanguagePackServiceImpl.Asset(
                    "jdtls.tar.gz",
                    "https://example.invalid/jdtls.tar.gz",
                    checksum,
                    10,
                    "tar.gz")));
    Path cache = temporary.resolve("cache");
    Path version = cache.resolve("java/test/x86_64");
    Path launcher = version.resolve("plugins/org.eclipse.equinox.launcher_test.jar");
    Files.createDirectories(launcher.getParent());
    Files.createDirectories(version.resolve("config_linux"));
    Files.write(launcher, new byte[] {1});
    Files.writeString(
        version.resolve(".jahaziel-pack"),
        "java\ntest\n" + checksum + "\n",
        StandardCharsets.UTF_8);
    Files.createDirectories(cache.resolve("selections"));
    Files.createDirectories(cache.resolve("state"));
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    Files.writeString(cache.resolve("selections").resolve(workspace.value() + ".txt"), "java\n");
    Path outside = temporary.resolve("outside-state");
    Files.createDirectories(outside);
    LanguagePackServiceImpl service =
        new LanguagePackServiceImpl(cache, mock(HttpClient.class), List.of(definition));

    assertThrows(
        java.io.UncheckedIOException.class,
        () -> service.launchCommandFor(workspace, "java", outside));
  }

  @Test
  void javaPackInstallationFindsTheLauncherAndPreservesItsConfiguration() throws Exception {
    System.setProperty("os.name", "Linux");
    System.setProperty("os.arch", "amd64");
    byte[] archive =
        tarGzip(
            Map.of(
                "plugins/org.eclipse.equinox.launcher_test.jar",
                new byte[] {1, 2, 3},
                "config_linux/config.ini",
                "configuration".getBytes(StandardCharsets.UTF_8)));
    LanguagePackServiceImpl.Definition definition =
        new LanguagePackServiceImpl.Definition(
            "java",
            "Eclipse JDT Language Server",
            "tar-test",
            Map.of(
                "x86_64",
                new LanguagePackServiceImpl.Asset(
                    "jdtls.tar.gz",
                    "https://example.invalid/jdtls.tar.gz",
                    sha256(archive),
                    archive.length,
                    "tar.gz")));
    Path cache = temporary.resolve("cache");
    LanguagePackServiceImpl service =
        new LanguagePackServiceImpl(cache, httpReturning(archive), List.of(definition));
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());

    var selected = service.select(workspace, "java");

    assertTrue(selected.installed());
    assertTrue(selected.selected());
    assertEquals(
        "configuration",
        Files.readString(cache.resolve("java/tar-test/x86_64/config_linux/config.ini")));
    assertTrue(
        Files.isRegularFile(
            cache.resolve("java/tar-test/x86_64/plugins/org.eclipse.equinox.launcher_test.jar")));
  }

  @Test
  void catalogRejectsUnknownLanguageIds() {
    LanguagePackServiceImpl service =
        new LanguagePackServiceImpl(
            temporary.resolve("cache"), mock(HttpClient.class), testCatalog(gzip(serverBytes())));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.deselect(new WorkspaceId(UUID.randomUUID()), "arbitrary-command"));
  }

  @Test
  void deletingAWorkspaceReleasesItsPackReferencesForPruning() throws Exception {
    HttpClient http = mock(HttpClient.class);
    LanguagePackServiceImpl.Definition definition = definition(gzip(serverBytes()));
    Path cache = temporary.resolve("cache");
    LanguagePackServiceImpl service = new LanguagePackServiceImpl(cache, http, List.of(definition));
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    String architecture = runtimeArchitecture();
    Path selection = cache.resolve("selections").resolve(workspace.value() + ".txt");
    Path installed = cache.resolve("rust/test/" + architecture);
    Path workspaceState =
        cache.resolve("state").resolve(workspace.value().toString()).resolve("go");
    Files.createDirectories(selection.getParent());
    Files.createDirectories(installed);
    Files.createDirectories(workspaceState);
    Files.writeString(selection, "rust\n", StandardCharsets.UTF_8);
    Files.writeString(installed.resolve("server"), "cached", StandardCharsets.UTF_8);
    Files.writeString(
        workspaceState.resolve("module-cache-entry"), "cached", StandardCharsets.UTF_8);

    assertTrue(service.prune(true).versions().isEmpty());
    service.clearWorkspace(workspace);

    assertFalse(Files.exists(selection));
    assertFalse(Files.exists(workspaceState));
    assertEquals(List.of("rust:test:" + architecture), service.prune(true).versions());
  }

  private static List<LanguagePackServiceImpl.Definition> testCatalog(byte[] archive) {
    return List.of(
        definition(archive),
        new LanguagePackServiceImpl.Definition("cpp", "clangd", "test", Map.of()),
        new LanguagePackServiceImpl.Definition("java", "JDTLS", "test", Map.of()));
  }

  private static String runtimeArchitecture() {
    return System.getProperty("os.arch", "").matches("(?i)aarch64|arm64") ? "aarch64" : "x86_64";
  }

  private static LanguagePackServiceImpl.Definition definition(byte[] archive) {
    return new LanguagePackServiceImpl.Definition(
        "rust",
        "Rust Analyzer",
        "test",
        Map.of(
            "x86_64",
            new LanguagePackServiceImpl.Asset(
                "rust-analyzer.gz",
                "https://example.invalid/rust-analyzer.gz",
                sha256(archive),
                archive.length,
                "gzip")));
  }

  private static HttpClient httpReturning(byte[] archive) throws Exception {
    HttpClient http = mock(HttpClient.class);
    @SuppressWarnings("unchecked")
    HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenAnswer(ignored -> new ByteArrayInputStream(archive));
    when(response.headers())
        .thenReturn(
            HttpHeaders.of(
                Map.of("Content-Length", List.of(Integer.toString(archive.length))),
                (name, value) -> true));
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
    return http;
  }

  private static HttpClient httpReturningUrls(Map<String, byte[]> archives) throws Exception {
    HttpClient http = mock(HttpClient.class);
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenAnswer(
            invocation -> {
              HttpRequest request = invocation.getArgument(0);
              byte[] archive = archives.get(request.uri().toString());
              if (archive == null) throw new IOException("unexpected archive request");
              @SuppressWarnings("unchecked")
              HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
              when(response.statusCode()).thenReturn(200);
              when(response.body()).thenAnswer(ignored -> new ByteArrayInputStream(archive));
              when(response.headers())
                  .thenReturn(
                      HttpHeaders.of(
                          Map.of("Content-Length", List.of(Integer.toString(archive.length))),
                          (name, value) -> true));
              return response;
            });
    return http;
  }

  private static byte[] serverBytes() {
    return "verified fake language server".getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] gzip(byte[] content) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
        gzip.write(content);
      }
      return bytes.toByteArray();
    } catch (IOException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static byte[] tarGzip(String path, byte[] content) {
    return tarGzip(Map.of(path, content));
  }

  private static byte[] tarGzip(String path, byte[] content, int mode) {
    return tarGzip(Map.of(path, content), mode);
  }

  private static byte[] tarGzip(Map<String, byte[]> entries) {
    return tarGzip(entries, 0644);
  }

  private static byte[] tarGzip(Map<String, byte[]> entries, int mode) {
    try {
      ByteArrayOutputStream tar = new ByteArrayOutputStream();
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        byte[] content = entry.getValue();
        byte[] header = new byte[512];
        putTarString(header, 0, 100, entry.getKey());
        putTarOctal(header, 100, 8, mode);
        putTarOctal(header, 108, 8, 0);
        putTarOctal(header, 116, 8, 0);
        putTarOctal(header, 124, 12, content.length);
        putTarOctal(header, 136, 12, 0);
        java.util.Arrays.fill(header, 148, 156, (byte) ' ');
        header[156] = '0';
        putTarString(header, 257, 6, "ustar\0");
        putTarString(header, 263, 2, "00");
        long checksum = 0;
        for (byte value : header) checksum += Byte.toUnsignedInt(value);
        putTarString(header, 148, 6, String.format(java.util.Locale.ROOT, "%06o", checksum));
        header[154] = 0;
        header[155] = ' ';
        tar.write(header);
        tar.write(content);
        int padding = (512 - content.length % 512) % 512;
        tar.write(new byte[padding]);
      }
      tar.write(new byte[1024]);
      return gzip(tar.toByteArray());
    } catch (IOException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static void putTarString(byte[] header, int offset, int length, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > length) throw new IllegalArgumentException("tar test field is too long");
    System.arraycopy(bytes, 0, header, offset, bytes.length);
  }

  private static void putTarOctal(byte[] header, int offset, int length, long value) {
    String digits = Long.toOctalString(value);
    int digitCount = length - 1;
    if (digits.length() > digitCount)
      throw new IllegalArgumentException("tar test value is too large");
    java.util.Arrays.fill(header, offset, offset + digitCount, (byte) '0');
    byte[] bytes = digits.getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(bytes, 0, header, offset + digitCount - bytes.length, bytes.length);
    header[offset + length - 1] = 0;
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static String sha512(byte[] bytes) {
    try {
      return java.util.Base64.getEncoder()
          .encodeToString(MessageDigest.getInstance("SHA-512").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static Path nodeExecutable() {
    String executable =
        System.getProperty("os.name", "").toLowerCase().contains("windows") ? "node.exe" : "node";
    String path = System.getenv("PATH");
    if (path == null) return null;
    for (String entry : path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
      Path candidate = Path.of(entry).resolve(executable);
      if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return candidate;
    }
    return null;
  }

  private static void restore(String name, String value) {
    if (value == null) {
      System.clearProperty(name);
    } else {
      System.setProperty(name, value);
    }
  }
}
