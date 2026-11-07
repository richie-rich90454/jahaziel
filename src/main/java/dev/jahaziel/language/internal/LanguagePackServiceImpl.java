/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import dev.jahaziel.language.api.LanguagePackInstallException;
import dev.jahaziel.language.api.LanguagePackService;
import dev.jahaziel.platform.Platform;
import dev.jahaziel.platform.Platforms;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;

/** Installs verified pinned archives only after an explicit workspace selection. */
public final class LanguagePackServiceImpl implements LanguagePackService {
  private static final long MAX_ARCHIVE_BYTES = 256L * 1024 * 1024;
  private static final long MAX_EXTRACTED_BYTES = 768L * 1024 * 1024;
  private static final int MAX_ARCHIVE_ENTRIES = 20_000;
  private static final Asset TYPESCRIPT_LANGUAGE_SERVER =
      new Asset(
          "typescript-language-server-6.0.2.tgz",
          "https://registry.npmjs.org/typescript-language-server/-/typescript-language-server-6.0.2.tgz",
          "",
          517_038,
          "npm.tar.gz",
          "Ph7Ql+xkUJvMamnkh1h+aKP6HxLR7lrWIYkPn9/LZBkJdJZgtIj7OoVhkDYtQ7zFPTPmqZHOVXaUKGyz9M2q1Q==",
          "node_modules/typescript-language-server");
  private static final Asset TYPESCRIPT_RUNTIME =
      new Asset(
          "typescript-6.0.3.tgz",
          "https://registry.npmjs.org/typescript/-/typescript-6.0.3.tgz",
          "",
          4_515_854,
          "npm.tar.gz",
          "y2TvuxSZPDyQakkFRPZHKFm+KKVqIisdg9/CZwm9ftvKXLP8NRWj38/ODjNbr43SsoXqNuAisEf1GdCxqWcdBw==",
          "node_modules/typescript");
  private static final Asset PYRIGHT =
      new Asset(
          "pyright-1.1.414.tgz",
          "https://registry.npmjs.org/pyright/-/pyright-1.1.414.tgz",
          "",
          4_226_827,
          "npm.tar.gz",
          "FPZZb51jepDX4eP7TEYDeNFtmE3WgwkkEcJpvH3/QmUSsj0EAy3LXu+xB4T/FWDejtsXlCfFr/rbWFjwuwuXww==",
          "node_modules/pyright");
  private static final String GOPLS_VERSION = "v0.24.0";
  private static final Asset GO_AMD64 =
      new Asset(
          "go1.27.3.linux-amd64.tar.gz",
          "https://dl.google.com/go/go1.27.3.linux-amd64.tar.gz",
          "65efbc80fdc1fa2cd2631cad5b481777a68e8725f34e89f50fc6ac714e379b40",
          67_000_000,
          "tar.gz",
          "",
          "toolchain");
  private static final Asset GO_ARM64 =
      new Asset(
          "go1.27.3.linux-arm64.tar.gz",
          "https://dl.google.com/go/go1.27.3.linux-arm64.tar.gz",
          "a06e99309dbcbdca1a8ccd2282c3ee146e55930886e26b4f1193269587165f49",
          64_000_000,
          "tar.gz",
          "",
          "toolchain");
  private static final List<Definition> DEFAULT_CATALOG =
      List.of(
          new Definition(
              "rust",
              "Rust Analyzer",
              "2026-09-21",
              Map.of(
                  "x86_64",
                  new Asset(
                      "rust-analyzer-x86_64-unknown-linux-gnu.gz",
                      "https://github.com/rust-lang/rust-analyzer/releases/download/2026-09-21/rust-analyzer-x86_64-unknown-linux-gnu.gz",
                      "b2d24ce2bda2ea05b1ad7c2917d205f8111775f703ccd908e6061803ae8257d0",
                      15_800_000,
                      "gzip"),
                  "aarch64",
                  new Asset(
                      "rust-analyzer-aarch64-unknown-linux-gnu.gz",
                      "https://github.com/rust-lang/rust-analyzer/releases/download/2026-09-21/rust-analyzer-aarch64-unknown-linux-gnu.gz",
                      "32a75657041a7a2ebf635c1e3968753e2639778b68dd4fb13c4b2584255f2ba8",
                      13_700_000,
                      "gzip"))),
          new Definition(
              "cpp",
              "clangd",
              "23.1.0",
              Map.of(
                  "x86_64",
                  new Asset(
                      "clangd-linux-23.1.0.zip",
                      "https://github.com/clangd/clangd/releases/download/23.1.0/clangd-linux-23.1.0.zip",
                      "e53b1a96196095faedb7642cf64964f7fb9ad4a0c1f00dd2c172a3d9dcbafdfd",
                      112_000_000,
                      "zip"))),
          new Definition(
              "java",
              "Eclipse JDT Language Server",
              "1.63.0",
              Map.of(
                  "x86_64",
                  new Asset(
                      "jdt-language-server-1.63.0-202610191322.tar.gz",
                      "https://download.eclipse.org/jdtls/milestones/1.63.0/jdt-language-server-1.63.0-202610191322.tar.gz",
                      "b1f2abe0b3cccb462cf64fb4a5587c7d09df60cf8be7748b152cbd89d018bdc4",
                      48_770_000,
                      "tar.gz"),
                  "aarch64",
                  new Asset(
                      "jdt-language-server-1.63.0-202610191322.tar.gz",
                      "https://download.eclipse.org/jdtls/milestones/1.63.0/jdt-language-server-1.63.0-202610191322.tar.gz",
                      "b1f2abe0b3cccb462cf64fb4a5587c7d09df60cf8be7748b152cbd89d018bdc4",
                      48_770_000,
                      "tar.gz"))),
          new Definition(
              "typescript",
              "TypeScript Language Server",
              "6.0.2-typescript-6.0.3",
              Map.of("x86_64", TYPESCRIPT_LANGUAGE_SERVER, "aarch64", TYPESCRIPT_LANGUAGE_SERVER),
              List.of(TYPESCRIPT_RUNTIME)),
          new Definition(
              "python", "Pyright", "1.1.414", Map.of("x86_64", PYRIGHT, "aarch64", PYRIGHT)),
          new Definition(
              "go",
              "gopls · Go 1.27.3",
              "gopls-v0.24.0-go1.27.3",
              Map.of("x86_64", GO_AMD64, "aarch64", GO_ARM64)));

  private final Path cacheRoot;
  private final Path nodeExecutable;
  private final HttpClient http;
  private final List<Definition> catalog;
  private final GoPackBuilder goPackBuilder;
  private final ReentrantLock mutation = new ReentrantLock();

  public LanguagePackServiceImpl(
      @Value("${jahaziel.storage.root:data}") String storageRoot,
      HttpClient http,
      @Value("${jahaziel.language-tools.node-executable:/usr/bin/node}") String nodeExecutable) {
    this(cachePath(storageRoot), http, DEFAULT_CATALOG, Path.of(nodeExecutable));
  }

  LanguagePackServiceImpl(Path cacheRoot, HttpClient http, List<Definition> catalog) {
    this(cacheRoot, http, catalog, Path.of("/usr/bin/node"));
  }

  LanguagePackServiceImpl(
      Path cacheRoot, HttpClient http, List<Definition> catalog, Path nodeExecutable) {
    this(cacheRoot, http, catalog, nodeExecutable, LanguagePackServiceImpl::buildGoPack);
  }

  LanguagePackServiceImpl(
      Path cacheRoot,
      HttpClient http,
      List<Definition> catalog,
      Path nodeExecutable,
      GoPackBuilder goPackBuilder) {
    this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot").toAbsolutePath().normalize();
    this.nodeExecutable =
        Objects.requireNonNull(nodeExecutable, "nodeExecutable").toAbsolutePath().normalize();
    this.http = Objects.requireNonNull(http, "http");
    this.catalog = List.copyOf(Objects.requireNonNull(catalog, "catalog"));
    this.goPackBuilder = Objects.requireNonNull(goPackBuilder, "goPackBuilder");
  }

  @Override
  public List<Pack> catalog() {
    return catalog.stream()
        .map(definition -> view(definition, installed(definition), false))
        .toList();
  }

  @Override
  public List<Pack> forWorkspace(WorkspaceId workspaceId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Set<String> selected = readSelections(workspaceId);
    return catalog.stream()
        .map(
            definition ->
                view(definition, installed(definition), selected.contains(definition.id())))
        .toList();
  }

  @Override
  public Pack select(WorkspaceId workspaceId, String languageId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Definition definition = definition(languageId);
    ensureSupported(definition);
    mutation.lock();
    try {
      ensureInstalled(definition);
      Set<String> selections = readSelections(workspaceId);
      selections.add(definition.id());
      writeSelections(workspaceId, selections);
      return view(definition, true, true);
    } finally {
      mutation.unlock();
    }
  }

  @Override
  public void deselect(WorkspaceId workspaceId, String languageId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Definition definition = definition(languageId);
    mutation.lock();
    try {
      Set<String> selections = readSelections(workspaceId);
      selections.remove(definition.id());
      writeSelections(workspaceId, selections);
    } finally {
      mutation.unlock();
    }
  }

  @Override
  public void clearWorkspace(WorkspaceId workspaceId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    mutation.lock();
    try {
      if (!Files.exists(cacheRoot, LinkOption.NOFOLLOW_LINKS)) return;
      if (!safeDirectoryChain(cacheRoot)) {
        throw new IOException("language-pack cache path is unsafe");
      }
      Path selections = cacheRoot.resolve("selections");
      if (!Files.exists(selections, LinkOption.NOFOLLOW_LINKS)) return;
      if (Files.isSymbolicLink(selections)
          || !Files.isDirectory(selections, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("workspace language selection directory is unsafe");
      }
      Path selection = selectionFile(workspaceId);
      if (Files.isSymbolicLink(selection)) {
        throw new IOException("workspace language selection path is unsafe");
      }
      Files.deleteIfExists(selection);
      Path workspaceState = cacheRoot.resolve("state").resolve(workspaceId.value().toString());
      if (Files.exists(workspaceState, LinkOption.NOFOLLOW_LINKS)) {
        if (!safeDirectoryChain(workspaceState)) {
          throw new IOException("workspace language-service state path is unsafe");
        }
        deleteTree(workspaceState);
      }
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot clear workspace language selections", failure);
    } finally {
      mutation.unlock();
    }
  }

  @Override
  public Path artifactFor(WorkspaceId workspaceId, String languageId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    if (!supportsLanguageService(languageId)) {
      throw new IllegalArgumentException("language service is not available for this language");
    }
    Definition definition = definition(languageId);
    if (!readSelections(workspaceId).contains(languageId)) {
      throw new IllegalArgumentException("language pack is not enabled for this workspace");
    }
    if (!installed(definition)) {
      throw new IllegalArgumentException("language pack is not installed");
    }
    Path version = versionDirectory(definition);
    try {
      Path artifact = locateExecutable(definition, version);
      Path realVersion = version.toRealPath();
      Path realArtifact = artifact.toRealPath();
      if (!realArtifact.startsWith(realVersion)
          || Files.isSymbolicLink(artifact)
          || !Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS)
          || ((definition.id().equals("rust")
                  || definition.id().equals("cpp")
                  || definition.id().equals("go"))
              && !Files.isExecutable(artifact))) {
        throw new IOException("language server launch artifact is unsafe or unavailable");
      }
      return realArtifact;
    } catch (IOException failure) {
      throw new UncheckedIOException("language server launch artifact is unavailable", failure);
    }
  }

  @Override
  public boolean supportsLanguageService(String languageId) {
    return "rust".equals(languageId)
        || "cpp".equals(languageId)
        || "java".equals(languageId)
        || "typescript".equals(languageId)
        || "python".equals(languageId)
        || "go".equals(languageId);
  }

  @Override
  public List<String> launchCommandFor(
      WorkspaceId workspaceId, String languageId, Path stateDirectory) {
    Objects.requireNonNull(stateDirectory, "stateDirectory");
    if (!supportsLanguageService(languageId)) {
      throw new IllegalArgumentException("language service is not available for this language");
    }
    Path artifact = artifactFor(workspaceId, languageId);
    if (languageId.equals("rust")) {
      return List.of(artifact.toString(), "--stdio");
    }
    if (languageId.equals("go")) {
      return List.of(artifact.toString(), "serve");
    }
    if (languageId.equals("cpp")) {
      return List.of(artifact.toString(), "--background-index");
    }
    if (languageId.equals("typescript") || languageId.equals("python")) {
      ensureNodeVersion(definition(languageId));
      try {
        return List.of(nodeExecutable.toRealPath().toString(), artifact.toString(), "--stdio");
      } catch (IOException failure) {
        throw new UncheckedIOException("configured Node.js executable is unavailable", failure);
      }
    }

    Definition definition = definition(languageId);
    Path stateRoot = cacheRoot.resolve("state");
    Path configuration = versionDirectory(definition).resolve("config_linux");
    Path javaBin = Path.of(System.getProperty("java.home"), "bin");
    Path java =
        Files.exists(javaBin.resolve("java"))
            ? javaBin.resolve("java")
            : javaBin.resolve("java.exe");
    try {
      Path realStateRoot = stateRoot.toRealPath();
      Path realState = stateDirectory.toRealPath();
      Path realConfiguration = configuration.toRealPath();
      Path realJava = java.toRealPath();
      if (!realState.startsWith(realStateRoot)
          || Files.isSymbolicLink(stateDirectory)
          || !Files.isDirectory(realConfiguration, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(configuration)
          || !Files.isRegularFile(realJava)
          || !Files.isExecutable(realJava)) {
        throw new IOException("JDT language server launch paths are unsafe or unavailable");
      }
      return List.of(
          realJava.toString(),
          "-Declipse.application=org.eclipse.jdt.ls.core.id1",
          "-Dosgi.bundles.defaultStartLevel=4",
          "-Declipse.product=org.eclipse.jdt.ls.core.product",
          "-Xmx768m",
          "--add-modules=ALL-SYSTEM",
          "--add-opens=java.base/java.util=ALL-UNNAMED",
          "--add-opens=java.base/java.lang=ALL-UNNAMED",
          "-jar",
          artifact.toString(),
          "-configuration",
          realConfiguration.toString(),
          "-data",
          realState.toString());
    } catch (IOException failure) {
      throw new UncheckedIOException("JDT language server is unavailable", failure);
    }
  }

  @Override
  public Map<String, String> launchEnvironmentFor(
      WorkspaceId workspaceId, String languageId, Path workspaceHome, Path stateDirectory) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(workspaceHome, "workspaceHome");
    Objects.requireNonNull(stateDirectory, "stateDirectory");
    if (!"go".equals(languageId)) return Map.of();
    Definition definition = definition(languageId);
    Path version = versionDirectory(definition);
    Path stateRoot = cacheRoot.resolve("state");
    try {
      ensurePlainDirectory(stateRoot);
      ensurePlainDirectory(stateDirectory);
      Path realVersion = version.toRealPath();
      Path realStateRoot = stateRoot.toRealPath();
      Path realState = stateDirectory.toRealPath();
      Path goRoot = version.resolve("toolchain/go").toRealPath();
      Path goExecutable = goRoot.resolve("bin/go");
      if (!realVersion.startsWith(cacheRoot.toRealPath())
          || !goRoot.startsWith(realVersion)
          || !safeDirectoryChain(version.resolve("toolchain/go"))
          || !realState.startsWith(realStateRoot)
          || Files.isSymbolicLink(version.resolve("toolchain/go"))
          || Files.isSymbolicLink(goExecutable)
          || !Files.isRegularFile(goExecutable, LinkOption.NOFOLLOW_LINKS)
          || !Files.isExecutable(goExecutable)) {
        throw new IOException("Go SDK or language-service state path is unsafe");
      }
      Path workspaceGo = realState.resolve("go");
      Path moduleCache = workspaceGo.resolve("pkg/mod");
      Path buildCache = realState.resolve("go-build-cache");
      ensurePlainDirectory(workspaceGo);
      ensurePlainDirectory(moduleCache);
      ensurePlainDirectory(buildCache);
      return Map.of(
          "GOROOT",
          goRoot.toString(),
          "GOPATH",
          workspaceGo.toString(),
          "GOMODCACHE",
          moduleCache.toString(),
          "GOCACHE",
          buildCache.toString(),
          "GOTOOLCHAIN",
          "local",
          "GOENV",
          "off",
          "GOFLAGS",
          "-mod=readonly",
          "GOPROXY",
          "https://proxy.golang.org",
          "GOSUMDB",
          "sum.golang.org",
          "PATH",
          goRoot.resolve("bin") + ":/usr/local/bin:/usr/bin:/bin");
    } catch (IOException failure) {
      throw new UncheckedIOException("Go language-service runtime is unavailable", failure);
    }
  }

  @Override
  public Map<String, Object> initializationOptionsFor(WorkspaceId workspaceId, String languageId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    if (!"typescript".equals(languageId)) return Map.of();
    artifactFor(workspaceId, languageId);
    Path version = versionDirectory(definition(languageId));
    Path tsserver = version.resolve("node_modules/typescript/lib/tsserver.js");
    try {
      Path realVersion = version.toRealPath();
      Path realTsserver = tsserver.toRealPath();
      if (!realTsserver.startsWith(realVersion)
          || Files.isSymbolicLink(tsserver)
          || !Files.isRegularFile(tsserver, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("TypeScript server path is unsafe or unavailable");
      }
      return Map.of("tsserver", Map.of("path", realTsserver.toString()));
    } catch (IOException failure) {
      throw new UncheckedIOException("TypeScript runtime is unavailable", failure);
    }
  }

  @Override
  public CacheReport report() {
    try {
      ensureCacheRoot();
      long bytes;
      try (var paths = Files.walk(cacheRoot)) {
        bytes =
            paths
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .mapToLong(LanguagePackServiceImpl::sizeOrZero)
                .sum();
      }
      List<Pack> installed =
          catalog.stream()
              .filter(this::installed)
              .map(definition -> view(definition, true, false))
              .toList();
      return new CacheReport(bytes, installed);
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot inspect language-pack cache", failure);
    }
  }

  @Override
  public PruneReport prune(boolean dryRun) {
    mutation.lock();
    try {
      Set<String> referenced = selectedPackIds();
      List<Path> candidates = new ArrayList<>();
      for (Definition definition : catalog) {
        Path packRoot = cacheRoot.resolve(definition.id());
        if (!Files.exists(packRoot, LinkOption.NOFOLLOW_LINKS)) {
          continue;
        }
        if (!safeDirectoryChain(packRoot)) {
          throw new IOException("language-pack cache contains an unsafe path");
        }
        try (var versions = Files.list(packRoot)) {
          for (Path versionRoot : versions.toList()) {
            if (!Files.isDirectory(versionRoot, LinkOption.NOFOLLOW_LINKS)) {
              continue;
            }
            if (!safeDirectoryChain(versionRoot)) {
              throw new IOException("language-pack cache contains an unsafe path");
            }
            try (var architectures = Files.list(versionRoot)) {
              for (Path architectureRoot : architectures.toList()) {
                if (!Files.isDirectory(architectureRoot, LinkOption.NOFOLLOW_LINKS)) {
                  continue;
                }
                if (!safeDirectoryChain(architectureRoot)) {
                  throw new IOException("language-pack cache contains an unsafe path");
                }
                boolean selectedCurrentVersion =
                    referenced.contains(definition.id())
                        && versionRoot.getFileName().toString().equals(definition.version())
                        && architectureRoot.getFileName().toString().equals(architecture());
                if (!selectedCurrentVersion) {
                  candidates.add(architectureRoot);
                }
              }
            }
          }
        }
      }
      List<String> versions = new ArrayList<>();
      long bytes = 0;
      for (Path candidate : candidates) {
        bytes += treeBytes(candidate);
        versions.add(
            cacheRoot.relativize(candidate).toString().replace('\\', '/').replace('/', ':'));
      }
      if (!dryRun) {
        for (Path candidate : candidates) {
          deleteTree(candidate);
        }
        for (Definition definition : catalog) {
          removeEmptyCacheDirectories(cacheRoot.resolve(definition.id()));
        }
      }
      return new PruneReport(dryRun, bytes, List.copyOf(versions));
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot prune language-pack cache", failure);
    } finally {
      mutation.unlock();
    }
  }

  private static void removeEmptyCacheDirectories(Path packRoot) throws IOException {
    if (Files.isSymbolicLink(packRoot) || !Files.isDirectory(packRoot, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    try (var versions = Files.list(packRoot)) {
      for (Path versionRoot : versions.toList()) {
        if (Files.isSymbolicLink(versionRoot)
            || !Files.isDirectory(versionRoot, LinkOption.NOFOLLOW_LINKS)) {
          continue;
        }
        try (var architectures = Files.list(versionRoot)) {
          for (Path architectureRoot : architectures.toList()) {
            if (Files.isSymbolicLink(architectureRoot)
                || !Files.isDirectory(architectureRoot, LinkOption.NOFOLLOW_LINKS)) {
              continue;
            }
            try (var contents = Files.list(architectureRoot)) {
              if (contents.findAny().isEmpty()) Files.deleteIfExists(architectureRoot);
            }
          }
        }
        try (var contents = Files.list(versionRoot)) {
          if (contents.findAny().isEmpty()) Files.deleteIfExists(versionRoot);
        }
      }
    }
    try (var contents = Files.list(packRoot)) {
      if (contents.findAny().isEmpty()) Files.deleteIfExists(packRoot);
    }
  }

  private void ensureInstalled(Definition definition) {
    if (installed(definition)) {
      return;
    }
    Path parent = versionDirectory(definition).getParent();
    Path staging = parent.resolve(definition.version() + ".installing");
    List<Path> partialArchives = new ArrayList<>();
    try {
      ensurePlainDirectory(cacheRoot);
      ensurePlainDirectory(parent);
      if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
        deleteTree(staging);
      }
      ensurePlainDirectory(staging);
      for (Asset asset : assetsFor(definition, architecture())) {
        validateAssetArchiveName(asset);
        Path archive = parent.resolve(asset.archive() + ".part");
        partialArchives.add(archive);
        Files.deleteIfExists(archive);
        download(asset, archive);
        verify(archive, asset);
        Path installRoot = safeInstallRoot(staging, asset.installRoot());
        ensurePlainDirectory(installRoot);
        switch (asset.format()) {
          case "gzip" -> extractGzip(archive, staging.resolve("rust-analyzer"));
          case "zip" -> extractZip(archive, installRoot);
          case "tar.gz" -> extractTarGzip(archive, installRoot);
          case "npm.tar.gz" -> extractNpmTarGzip(archive, installRoot);
          default -> throw new IOException("language-pack archive format is unsupported");
        }
      }
      if (definition.id().equals("go")) {
        Path goExecutable = staging.resolve("toolchain/go/bin/go");
        Path realStaging = staging.toRealPath();
        Path realGo = goExecutable.toRealPath();
        if (!realGo.startsWith(realStaging)
            || Files.isSymbolicLink(goExecutable)
            || !Files.isRegularFile(goExecutable, LinkOption.NOFOLLOW_LINKS)) {
          throw new IOException("Go SDK executable is missing from the verified archive");
        }
        makeExecutable(realGo);
        goPackBuilder.build(realGo, staging);
      }
      Path executable = locateExecutable(definition, staging);
      if (definition.id().equals("rust")
          || definition.id().equals("cpp")
          || definition.id().equals("go")) {
        makeExecutable(executable);
      }
      Files.writeString(
          staging.resolve(".jahaziel-pack"),
          String.join("\n", markerLines(definition, architecture())) + "\n",
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE);
      Path destination = versionDirectory(definition);
      try {
        Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException atomicMoveFailure) {
        try {
          Files.move(staging, destination);
        } catch (IOException fallbackFailure) {
          fallbackFailure.addSuppressed(atomicMoveFailure);
          throw fallbackFailure;
        }
      }
    } catch (IOException failure) {
      try {
        if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
          deleteTree(staging);
        }
      } catch (IOException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw new LanguagePackInstallException("language pack installation failed", failure);
    } finally {
      for (Path archive : partialArchives) {
        try {
          Files.deleteIfExists(archive);
        } catch (IOException ignored) {
          // A stale partial archive is ignored and replaced on the next explicit attempt.
        }
      }
    }
  }

  private static void validateAssetArchiveName(Asset asset) throws IOException {
    Path path;
    try {
      path = Path.of(asset.archive());
    } catch (RuntimeException invalid) {
      throw new IOException("language-pack archive name is invalid", invalid);
    }
    if (asset.archive().isBlank()
        || path.isAbsolute()
        || path.getNameCount() != 1
        || !path.getFileName().toString().equals(asset.archive())) {
      throw new IOException("language-pack archive name is invalid");
    }
  }

  private static Path safeInstallRoot(Path staging, String relative) throws IOException {
    Path result;
    try {
      result = staging.resolve(relative).normalize();
    } catch (RuntimeException invalid) {
      throw new IOException("language-pack install path is invalid", invalid);
    }
    if (!result.startsWith(staging) || Files.isSymbolicLink(result)) {
      throw new IOException("language-pack install path is unsafe");
    }
    return result;
  }

  private void download(Asset asset, Path destination) throws IOException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(asset.url()))
            .timeout(Duration.ofMinutes(5))
            .header("Accept", "application/octet-stream")
            .GET()
            .build();
    HttpResponse<InputStream> response;
    try {
      response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("language-pack download was interrupted", interrupted);
    }
    if (response.statusCode() != 200) {
      response.body().close();
      throw new IOException("language-pack source returned HTTP " + response.statusCode());
    }
    long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
    if (declaredLength > MAX_ARCHIVE_BYTES) {
      response.body().close();
      throw new IOException("language-pack archive exceeds the download size limit");
    }
    try (InputStream input = response.body();
        OutputStream output =
            Files.newOutputStream(
                destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      byte[] buffer = new byte[64 * 1024];
      long total = 0;
      int read;
      while ((read = input.read(buffer)) >= 0) {
        total += read;
        if (total > MAX_ARCHIVE_BYTES) {
          throw new IOException("language-pack archive exceeds the download size limit");
        }
        output.write(buffer, 0, read);
      }
    }
  }

  private static void verify(Path archive, Asset asset) throws IOException {
    try {
      boolean sha512 = asset.sha256().isBlank();
      MessageDigest digest = MessageDigest.getInstance(sha512 ? "SHA-512" : "SHA-256");
      try (InputStream input = new DigestInputStream(Files.newInputStream(archive), digest)) {
        input.transferTo(OutputStream.nullOutputStream());
      }
      String actual =
          sha512
              ? Base64.getEncoder().encodeToString(digest.digest())
              : HexFormat.of().formatHex(digest.digest());
      String expected = sha512 ? asset.sha512() : asset.sha256();
      if (expected.isBlank() || !actual.equals(expected)) {
        throw new IOException("language-pack checksum does not match the pinned catalog");
      }
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("required checksum algorithm is unavailable", impossible);
    }
  }

  private static void extractGzip(Path archive, Path destination) throws IOException {
    try (InputStream input = new GZIPInputStream(Files.newInputStream(archive));
        OutputStream output =
            Files.newOutputStream(
                destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      copyBounded(input, output);
    }
  }

  private static void extractZip(Path archive, Path root) throws IOException {
    int entries = 0;
    long extracted = 0;
    try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (++entries > MAX_ARCHIVE_ENTRIES) {
          throw new IOException("language-pack archive contains too many entries");
        }
        Path target = root.resolve(entry.getName()).normalize();
        if (!target.startsWith(root) || entry.getName().isBlank()) {
          throw new IOException("language-pack archive contains an unsafe path");
        }
        if (entry.isDirectory()) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          try (OutputStream output =
              Files.newOutputStream(
                  target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            extracted += copyBounded(zip, output, MAX_EXTRACTED_BYTES - extracted);
          }
        }
        if (extracted > MAX_EXTRACTED_BYTES) {
          throw new IOException("language-pack archive expands beyond the extraction limit");
        }
        zip.closeEntry();
      }
    }
  }

  private static void extractTarGzip(Path archive, Path root) throws IOException {
    try (InputStream input = new GZIPInputStream(Files.newInputStream(archive))) {
      int entries = 0;
      long extracted = 0;
      while (true) {
        byte[] header = readTarBlock(input);
        if (header == null || isZeroBlock(header)) {
          return;
        }
        if (++entries > MAX_ARCHIVE_ENTRIES) {
          throw new IOException("language-pack archive contains too many entries");
        }
        verifyTarChecksum(header);
        String name = tarString(header, 0, 100);
        String prefix = tarString(header, 345, 155);
        if (!prefix.isBlank()) {
          name = prefix + "/" + name;
        }
        long size = parseTarOctal(header, 124, 12);
        int type = Byte.toUnsignedInt(header[156]);
        boolean directory = type == '5';
        Path target = tarTarget(root, name, directory);
        if (directory) {
          if (size != 0) {
            throw new IOException("language-pack archive contains an invalid directory entry");
          }
          ensurePlainDirectory(target);
        } else if (type == 0 || type == '0') {
          if (size > MAX_EXTRACTED_BYTES - extracted) {
            throw new IOException("language-pack archive expands beyond the extraction limit");
          }
          Path parent = target.getParent();
          if (parent == null) {
            throw new IOException("language-pack archive contains an invalid file path");
          }
          ensurePlainDirectory(parent);
          try (OutputStream output =
              Files.newOutputStream(
                  target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            copyExactly(input, output, size);
          }
          if ((parseTarOctal(header, 100, 8) & 0111) != 0) {
            makeExecutable(target);
          }
          extracted += size;
        } else {
          throw new IOException("language-pack archive contains an unsupported entry type");
        }
        skipTarPadding(input, size);
      }
    }
  }

  private static void extractNpmTarGzip(Path archive, Path root) throws IOException {
    try (InputStream input = new GZIPInputStream(Files.newInputStream(archive))) {
      int entries = 0;
      long extracted = 0;
      while (true) {
        byte[] header = readTarBlock(input);
        if (header == null || isZeroBlock(header)) return;
        if (++entries > MAX_ARCHIVE_ENTRIES) {
          throw new IOException("language-pack archive contains too many entries");
        }
        verifyTarChecksum(header);
        String name = tarString(header, 0, 100);
        String prefix = tarString(header, 345, 155);
        if (!prefix.isBlank()) name = prefix + "/" + name;
        long size = parseTarOctal(header, 124, 12);
        int type = Byte.toUnsignedInt(header[156]);
        boolean directory = type == '5';
        String relative;
        if (name.equals("package") || name.equals("package/")) {
          relative = ".";
        } else if (name.startsWith("package/")) {
          relative = name.substring("package/".length());
        } else {
          throw new IOException("npm archive contains a path outside its package root");
        }
        if (directory) {
          if (size != 0) {
            throw new IOException("language-pack archive contains an invalid directory entry");
          }
          if (!relative.equals(".")) ensurePlainDirectory(tarTarget(root, relative, true));
        } else if (type == 0 || type == '0') {
          if (relative.equals(".") || size > MAX_EXTRACTED_BYTES - extracted) {
            throw new IOException("language-pack archive contains an invalid file entry");
          }
          Path target = tarTarget(root, relative, false);
          Path parent = target.getParent();
          if (parent == null)
            throw new IOException("language-pack archive contains an invalid path");
          ensurePlainDirectory(parent);
          try (OutputStream output =
              Files.newOutputStream(
                  target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            copyExactly(input, output, size);
          }
          extracted += size;
        } else {
          throw new IOException("language-pack archive contains an unsupported entry type");
        }
        skipTarPadding(input, size);
      }
    }
  }

  private static byte[] readTarBlock(InputStream input) throws IOException {
    byte[] block = input.readNBytes(512);
    if (block.length == 0) {
      return null;
    }
    if (block.length != 512) {
      throw new IOException("language-pack archive contains a truncated tar header");
    }
    return block;
  }

  private static boolean isZeroBlock(byte[] block) {
    for (byte value : block) {
      if (value != 0) {
        return false;
      }
    }
    return true;
  }

  private static void verifyTarChecksum(byte[] header) throws IOException {
    long expected = parseTarOctal(header, 148, 8);
    long actual = 0;
    for (int index = 0; index < header.length; index++) {
      actual += index >= 148 && index < 156 ? (int) ' ' : Byte.toUnsignedInt(header[index]);
    }
    if (actual != expected) {
      throw new IOException("language-pack archive contains an invalid tar checksum");
    }
  }

  private static long parseTarOctal(byte[] header, int offset, int length) throws IOException {
    int end = offset + length;
    int index = offset;
    while (index < end && (header[index] == 0 || header[index] == ' ')) {
      index++;
    }
    long value = 0;
    for (; index < end && header[index] != 0 && header[index] != ' '; index++) {
      int digit = header[index] - '0';
      if (digit < 0 || digit > 7 || value > (Long.MAX_VALUE - digit) / 8) {
        throw new IOException("language-pack archive contains an invalid tar number");
      }
      value = value * 8 + digit;
    }
    return value;
  }

  private static String tarString(byte[] header, int offset, int length) {
    int end = offset;
    while (end < offset + length && header[end] != 0) {
      end++;
    }
    return new String(header, offset, end - offset, StandardCharsets.UTF_8);
  }

  private static Path tarTarget(Path root, String name, boolean directory) throws IOException {
    if (name == null || name.isBlank() || name.indexOf('\0') >= 0 || name.indexOf('\\') >= 0) {
      throw new IOException("language-pack archive contains an invalid path");
    }
    Path relative;
    try {
      relative = Path.of(name);
    } catch (RuntimeException invalid) {
      throw new IOException("language-pack archive contains an invalid path", invalid);
    }
    if (relative.isAbsolute()) {
      throw new IOException("language-pack archive contains an absolute path");
    }
    for (Path segment : relative) {
      if (segment.toString().equals("..")) {
        throw new IOException("language-pack archive contains an unsafe path");
      }
    }
    Path normalizedRoot = root.toAbsolutePath().normalize();
    Path target = normalizedRoot.resolve(relative).normalize();
    if (!target.startsWith(normalizedRoot) || (target.equals(normalizedRoot) && !directory)) {
      throw new IOException("language-pack archive contains an unsafe path");
    }
    return target;
  }

  private static void copyExactly(InputStream input, OutputStream output, long length)
      throws IOException {
    byte[] buffer = new byte[64 * 1024];
    long remaining = length;
    while (remaining > 0) {
      int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
      if (read < 0) {
        throw new IOException("language-pack archive contains a truncated file");
      }
      output.write(buffer, 0, read);
      remaining -= read;
    }
  }

  private static void skipTarPadding(InputStream input, long length) throws IOException {
    long padding = (512 - length % 512) % 512;
    byte[] buffer = new byte[512];
    while (padding > 0) {
      int read = input.read(buffer, 0, (int) Math.min(buffer.length, padding));
      if (read < 0) {
        throw new IOException("language-pack archive contains truncated padding");
      }
      padding -= read;
    }
  }

  private static long copyBounded(InputStream input, OutputStream output) throws IOException {
    return copyBounded(input, output, MAX_EXTRACTED_BYTES);
  }

  private static long copyBounded(InputStream input, OutputStream output, long limit)
      throws IOException {
    byte[] buffer = new byte[64 * 1024];
    long total = 0;
    int read;
    while ((read = input.read(buffer)) >= 0) {
      total += read;
      if (total > limit) {
        throw new IOException("language-pack archive expands beyond the extraction limit");
      }
      output.write(buffer, 0, read);
    }
    return total;
  }

  private static Path locateExecutable(Definition definition, Path root) throws IOException {
    if (definition.id().equals("typescript")) {
      return root.resolve("node_modules/typescript-language-server/lib/cli.mjs");
    }
    if (definition.id().equals("python")) {
      return root.resolve("node_modules/pyright/langserver.index.js");
    }
    if (definition.id().equals("go")) {
      return root.resolve("bin/gopls");
    }
    if (definition.id().equals("rust")) {
      return root.resolve("rust-analyzer");
    }
    if (definition.id().equals("java")) {
      try (var files = Files.walk(root, 8)) {
        return files
            .filter(Files::isRegularFile)
            .filter(
                path -> path.getFileName().toString().startsWith("org.eclipse.equinox.launcher_"))
            .filter(path -> path.getFileName().toString().endsWith(".jar"))
            .findFirst()
            .orElseThrow(
                () -> new IOException("JDT language server launcher is missing from the archive"));
      }
    }
    try (var files = Files.walk(root, 8)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().equals("clangd"))
          .findFirst()
          .orElseThrow(() -> new IOException("clangd executable is missing from the archive"));
    }
  }

  private static void buildGoPack(Path goExecutable, Path staging) throws IOException {
    Path buildRoot = staging.resolve(".go-pack-build");
    Path home = buildRoot.resolve("home");
    Path temporary = buildRoot.resolve("tmp");
    Path goPath = buildRoot.resolve("gopath");
    Path moduleCache = buildRoot.resolve("module-cache");
    Path buildCache = buildRoot.resolve("build-cache");
    Path goBin = staging.resolve("bin");
    Path goRoot = staging.resolve("toolchain/go");
    ensurePlainDirectory(home);
    ensurePlainDirectory(temporary);
    ensurePlainDirectory(goPath);
    ensurePlainDirectory(moduleCache);
    ensurePlainDirectory(buildCache);
    ensurePlainDirectory(goBin);
    Process process = null;
    try {
      ProcessBuilder builder =
          new ProcessBuilder(
                  goExecutable.toString(), "install", "golang.org/x/tools/gopls@" + GOPLS_VERSION)
              .directory(staging.toFile())
              .redirectErrorStream(true)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD);
      Map<String, String> environment = builder.environment();
      environment.clear();
      environment.put("PATH", "/usr/local/bin:/usr/bin:/bin");
      environment.put("HOME", home.toString());
      environment.put("TMPDIR", temporary.toString());
      environment.put("GOROOT", goRoot.toString());
      environment.put("GOPATH", goPath.toString());
      environment.put("GOMODCACHE", moduleCache.toString());
      environment.put("GOCACHE", buildCache.toString());
      environment.put("GOBIN", goBin.toString());
      environment.put("GOENV", "off");
      environment.put("GOTOOLCHAIN", "local");
      environment.put("CGO_ENABLED", "0");
      environment.put("GOMAXPROCS", "2");
      environment.put("GOFLAGS", "-p=2");
      environment.put("GOPROXY", "https://proxy.golang.org");
      environment.put("GOSUMDB", "sum.golang.org");
      process = builder.start();
      if (!process.waitFor(10, TimeUnit.MINUTES)) {
        stopBuildProcess(process);
        throw new IOException("building the pinned gopls language server exceeded 10 minutes");
      }
      if (process.exitValue() != 0) {
        throw new IOException(
            "building the pinned gopls language server failed with exit code "
                + process.exitValue());
      }
      Path gopls = goBin.resolve("gopls");
      if (Files.isSymbolicLink(gopls)
          || !Files.isRegularFile(gopls, LinkOption.NOFOLLOW_LINKS)
          || !gopls.toRealPath().startsWith(staging.toRealPath())) {
        throw new IOException("the pinned gopls build did not produce a safe executable");
      }
      makeExecutable(gopls);
    } catch (InterruptedException interrupted) {
      if (process != null) stopBuildProcess(process);
      Thread.currentThread().interrupt();
      throw new IOException(
          "building the pinned gopls language server was interrupted", interrupted);
    } finally {
      if (Files.exists(buildRoot, LinkOption.NOFOLLOW_LINKS)) {
        deleteTree(buildRoot);
      }
    }
  }

  private static void stopBuildProcess(Process process) {
    process.descendants().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    process.destroyForcibly();
    try {
      process.waitFor(5, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private static void makeExecutable(Path file) throws IOException {
    try {
      Files.setPosixFilePermissions(
          file,
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE));
    } catch (UnsupportedOperationException ignored) {
      if (!file.toFile().setExecutable(true, false)) {
        throw new IOException("could not mark language server executable");
      }
    }
  }

  private Set<String> readSelections(WorkspaceId workspaceId) {
    Path file = selectionFile(workspaceId);
    try {
      ensurePlainDirectory(cacheRoot);
      if (Files.exists(file.getParent(), LinkOption.NOFOLLOW_LINKS)) {
        ensurePlainDirectory(file.getParent());
      }
      if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
        return new java.util.HashSet<>();
      }
      if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("workspace language selection is not a regular file");
      }
      Set<String> selections =
          new java.util.HashSet<>(Files.readAllLines(file).stream().map(String::strip).toList());
      selections.removeIf(
          id -> catalog.stream().noneMatch(definition -> definition.id().equals(id)));
      return selections;
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot read workspace language selections", failure);
    }
  }

  private void writeSelections(WorkspaceId workspaceId, Set<String> selections) {
    Path file = selectionFile(workspaceId);
    Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
    try {
      ensurePlainDirectory(file.getParent());
      if (Files.isSymbolicLink(file) || Files.isSymbolicLink(temporary)) {
        throw new IOException("workspace language selection path is unsafe");
      }
      Files.deleteIfExists(temporary);
      Files.write(
          temporary,
          selections.stream().sorted().toList(),
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE);
      try {
        Files.move(
            temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException failure) {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw new UncheckedIOException("cannot save workspace language selections", failure);
    }
  }

  private Set<String> selectedPackIds() throws IOException {
    ensurePlainDirectory(cacheRoot);
    Path selectionRoot = cacheRoot.resolve("selections");
    if (!Files.exists(selectionRoot, LinkOption.NOFOLLOW_LINKS)) {
      return Set.of();
    }
    if (Files.isSymbolicLink(selectionRoot)
        || !Files.isDirectory(selectionRoot, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("language-pack selection directory is unsafe");
    }
    Set<String> selected = new java.util.HashSet<>();
    try (var files = Files.list(selectionRoot)) {
      for (Path file : files.toList()) {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
          continue;
        }
        for (String id : Files.readAllLines(file)) {
          if (catalog.stream().anyMatch(definition -> definition.id().equals(id.strip()))) {
            selected.add(id.strip());
          }
        }
      }
    }
    return selected;
  }

  private Pack view(Definition definition, boolean installed, boolean selected) {
    String architecture = architecture();
    Asset asset = definition.assets().get(architecture);
    List<Asset> assets = assetsFor(definition, architecture);
    String availability =
        !operatingSystemIsLinux()
            ? "Linux server required"
            : !assets.isEmpty() ? "available" : "not published for " + architecture;
    if ("available".equals(availability) && isNodeLanguage(definition)) {
      try {
        ensureNodeVersion(definition);
      } catch (IllegalArgumentException unavailable) {
        availability = unavailable.getMessage();
      }
    }
    return new Pack(
        definition.id(),
        definition.name(),
        definition.version(),
        assets.stream().mapToLong(Asset::downloadBytes).sum(),
        asset == null ? "" : asset.sha256(),
        assets.stream().map(Asset::checksumDisplay).toList(),
        definition.assets().keySet().stream().sorted().toList(),
        installed,
        selected,
        availability,
        true,
        definition.id().equals("go")
            ? "Go SDK download only; verified Go module downloads and temporary build space are additional."
            : "");
  }

  private boolean installed(Definition definition) {
    if (assetsFor(definition, architecture()).isEmpty()) {
      return false;
    }
    Path version = versionDirectory(definition);
    if (!safeDirectoryChain(version)) {
      return false;
    }
    Path marker = version.resolve(".jahaziel-pack");
    try {
      if (Files.isSymbolicLink(version) || Files.isSymbolicLink(marker)) {
        return false;
      }
      if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
          || !Files.readAllLines(marker).equals(markerLines(definition, architecture()))
          || !Files.isRegularFile(
              locateExecutable(definition, version), LinkOption.NOFOLLOW_LINKS)) {
        return false;
      }
      if (definition.id().equals("typescript")) {
        Path tsserver = version.resolve("node_modules/typescript/lib/tsserver.js");
        return !Files.isSymbolicLink(tsserver)
            && Files.isRegularFile(tsserver, LinkOption.NOFOLLOW_LINKS);
      }
      if (definition.id().equals("go")) {
        Path goExecutable = version.resolve("toolchain/go/bin/go");
        return !Files.isSymbolicLink(goExecutable)
            && Files.isRegularFile(goExecutable, LinkOption.NOFOLLOW_LINKS)
            && Files.isExecutable(goExecutable);
      }
      return true;
    } catch (IOException failure) {
      return false;
    }
  }

  private void ensureSupported(Definition definition) {
    if (!operatingSystemIsLinux() || !definition.assets().containsKey(architecture())) {
      throw new IllegalArgumentException(
          "language pack is unavailable on this server architecture");
    }
    if (isNodeLanguage(definition)) ensureNodeVersion(definition);
  }

  private boolean nodeExecutableAvailable() {
    try {
      Path realNode = nodeExecutable.toRealPath();
      return Files.isRegularFile(realNode) && Files.isExecutable(realNode);
    } catch (IOException failure) {
      return false;
    }
  }

  private void ensureNodeVersion(Definition definition) {
    if (!nodeExecutableAvailable()) {
      throw new IllegalArgumentException(
          "Node.js is required for this language service; configure "
              + "jahaziel.language-tools.node-executable");
    }
    Process process;
    try {
      process =
          new ProcessBuilder(nodeExecutable.toString(), "--version")
              .redirectErrorStream(true)
              .start();
      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new IllegalArgumentException(
            "configured Node.js executable did not report a version");
      }
      String version;
      try (InputStream output = process.getInputStream()) {
        version = new String(output.readNBytes(128), StandardCharsets.UTF_8).strip();
      }
      if (process.exitValue() != 0) {
        throw new IllegalArgumentException("configured Node.js executable could not be started");
      }
      if (version.startsWith("v")) version = version.substring(1);
      String minimum = definition.id().equals("typescript") ? "22.22.2" : "14.0.0";
      if (!isVersionAtLeast(version, minimum)) {
        throw new IllegalArgumentException(
            definition.id().equals("typescript")
                ? "TypeScript language service requires Node.js 22.22.2 or newer"
                : "Pyright requires Node.js 14.0.0 or newer");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalArgumentException(
          "checking the configured Node.js version was interrupted", interrupted);
    } catch (IOException failure) {
      throw new IllegalArgumentException(
          "could not verify the configured Node.js executable", failure);
    }
  }

  private static boolean isNodeLanguage(Definition definition) {
    return definition.id().equals("typescript") || definition.id().equals("python");
  }

  private static boolean isVersionAtLeast(String actual, String minimum) {
    if (!actual.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
        || !minimum.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
      return false;
    }
    String[] actualParts = actual.split("\\.");
    String[] minimumParts = minimum.split("\\.");
    for (int index = 0; index < 3; index++) {
      int comparison =
          Integer.compare(
              Integer.parseInt(actualParts[index]), Integer.parseInt(minimumParts[index]));
      if (comparison != 0) return comparison > 0;
    }
    return true;
  }

  private Asset asset(Definition definition) {
    Asset asset = definition.assets().get(architecture());
    if (asset == null) {
      throw new IllegalArgumentException(
          "language pack is unavailable on this server architecture");
    }
    return asset;
  }

  private static List<Asset> assetsFor(Definition definition, String architecture) {
    Asset primary = definition.assets().get(architecture);
    if (primary == null) return List.of();
    List<Asset> assets = new ArrayList<>();
    assets.add(primary);
    assets.addAll(definition.supplementalAssets());
    return List.copyOf(assets);
  }

  private static List<String> markerLines(Definition definition, String architecture) {
    List<String> lines = new ArrayList<>();
    lines.add(definition.id());
    lines.add(definition.version());
    for (Asset asset : assetsFor(definition, architecture)) {
      lines.add(asset.checksumDescription());
    }
    return List.copyOf(lines);
  }

  private Definition definition(String id) {
    if (id == null) {
      throw new IllegalArgumentException("language id is required");
    }
    return catalog.stream()
        .filter(definition -> definition.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unknown language id"));
  }

  private Path versionDirectory(Definition definition) {
    return cacheRoot.resolve(definition.id()).resolve(definition.version()).resolve(architecture());
  }

  private Path selectionFile(WorkspaceId id) {
    return cacheRoot.resolve("selections").resolve(id.value() + ".txt");
  }

  private void ensureCacheRoot() throws IOException {
    ensurePlainDirectory(cacheRoot);
  }

  private static void ensurePlainDirectory(Path path) throws IOException {
    Path absolute = path.toAbsolutePath().normalize();
    Path current = absolute.getRoot();
    if (current == null) {
      throw new IOException("language-pack storage path is not absolute");
    }
    for (Path component : absolute) {
      current = current.resolve(component);
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isSymbolicLink(current)
            || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
          throw new IOException("language-pack storage path is not a plain directory");
        }
      } else {
        Files.createDirectory(current);
      }
    }
  }

  private boolean safeDirectoryChain(Path directory) {
    Path absolute = directory.toAbsolutePath().normalize();
    if (!absolute.startsWith(cacheRoot)) {
      return false;
    }
    Path current = cacheRoot;
    if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
        && (Files.isSymbolicLink(current)
            || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))) {
      return false;
    }
    for (Path component : cacheRoot.relativize(absolute)) {
      current = current.resolve(component);
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
          && (Files.isSymbolicLink(current)
              || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))) {
        return false;
      }
    }
    return true;
  }

  private static Path cachePath(String storageRoot) {
    if (storageRoot == null || storageRoot.isBlank()) {
      throw new IllegalArgumentException("storageRoot must not be blank");
    }
    return Path.of(storageRoot).toAbsolutePath().normalize().resolve("language-packs");
  }

  private static long treeBytes(Path root) throws IOException {
    try (var paths = Files.walk(root)) {
      return paths
          .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
          .mapToLong(LanguagePackServiceImpl::sizeOrZero)
          .sum();
    }
  }

  private static long sizeOrZero(Path file) {
    try {
      return Files.size(file);
    } catch (IOException ignored) {
      return 0;
    }
  }

  private static void deleteTree(Path root) throws IOException {
    if (Files.isSymbolicLink(root)) {
      Files.delete(root);
      return;
    }
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        if (Files.isSymbolicLink(path)) {
          Files.delete(path);
        } else {
          Files.deleteIfExists(path);
        }
      }
    }
  }

  private static boolean operatingSystemIsLinux() {
    return Platforms.detect(null).os() == Platform.Os.LINUX;
  }

  private static String architecture() {
    return switch (Platforms.detect(null).arch()) {
      case X64 -> "x86_64";
      case ARM64 -> "aarch64";
    };
  }

  record Definition(
      String id,
      String name,
      String version,
      Map<String, Asset> assets,
      List<Asset> supplementalAssets) {
    Definition(String id, String name, String version, Map<String, Asset> assets) {
      this(id, name, version, assets, List.of());
    }

    Definition {
      assets = Map.copyOf(assets);
      supplementalAssets = List.copyOf(supplementalAssets);
    }
  }

  record Asset(
      String archive,
      String url,
      String sha256,
      long downloadBytes,
      String format,
      String sha512,
      String installRoot) {
    Asset(String archive, String url, String sha256, long downloadBytes, String format) {
      this(archive, url, sha256, downloadBytes, format, "", "");
    }

    Asset {
      Objects.requireNonNull(archive, "archive");
      Objects.requireNonNull(url, "url");
      Objects.requireNonNull(sha256, "sha256");
      Objects.requireNonNull(format, "format");
      Objects.requireNonNull(sha512, "sha512");
      Objects.requireNonNull(installRoot, "installRoot");
      if (sha256.isBlank() == sha512.isBlank()) {
        throw new IllegalArgumentException("asset must provide exactly one pinned checksum");
      }
    }

    String checksumDescription() {
      return sha256.isBlank() ? "SHA-512:" + sha512 : sha256;
    }

    String checksumDisplay() {
      return sha256.isBlank() ? "SHA-512: " + sha512 : "SHA-256: " + sha256;
    }
  }
}
