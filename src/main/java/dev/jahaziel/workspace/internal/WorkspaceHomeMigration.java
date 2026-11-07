/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import dev.jahaziel.workspace.persistence.VirtualMachineRepository;
import dev.jahaziel.workspace.persistence.WorkspaceEntity;
import dev.jahaziel.workspace.persistence.WorkspaceRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Preserves legacy shared-home data in each workspace home before serving file or runtime requests.
 */
@Component
public final class WorkspaceHomeMigration implements ApplicationRunner {
  private final WorkspaceRepository workspaces;
  private final VirtualMachineRepository machines;
  private final String storageRoot;

  public WorkspaceHomeMigration(
      WorkspaceRepository workspaces,
      VirtualMachineRepository machines,
      @Value("${jahaziel.storage.root:data}") String storageRoot) {
    this.workspaces = workspaces;
    this.machines = machines;
    this.storageRoot = storageRoot;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    Map<UUID, WorkspaceEntity> byId =
        workspaces.findAll().stream()
            .collect(Collectors.toMap(WorkspaceEntity::getWorkspaceId, Function.identity()));
    machines
        .findAll()
        .forEach(
            machine -> {
              WorkspaceEntity workspace = byId.get(machine.getWorkspaceId());
              if (workspace == null) {
                return;
              }
              Path legacy =
                  Path.of(
                          WorkspacePaths.legacyHomePath(
                              storageRoot,
                              new dev.jahaziel.identity.domain.UserId(workspace.getOwnerUserId())))
                      .toAbsolutePath()
                      .normalize();
              Path destination =
                  Path.of(
                          WorkspacePaths.homePath(
                              storageRoot,
                              new dev.jahaziel.workspace.domain.WorkspaceId(
                                  workspace.getWorkspaceId())))
                      .toAbsolutePath()
                      .normalize();
              Path recorded = Path.of(machine.getHomePath()).toAbsolutePath().normalize();
              if (!recorded.equals(legacy) || legacy.equals(destination)) {
                return;
              }
              try {
                Path data = Path.of(storageRoot).toAbsolutePath().normalize();
                Files.createDirectories(data);
                data = data.toRealPath();
                Path workspaceRoot = data.resolve("workspaces");
                ensurePlainDirectory(workspaceRoot);
                Path workspaceDirectory =
                    workspaceRoot.resolve(workspace.getWorkspaceId().toString());
                ensurePlainDirectory(workspaceDirectory);
                Path workspaceHome = workspaceDirectory.resolve("home");
                if (Files.isSymbolicLink(legacy)) {
                  throw new IOException("legacy workspace home is a symbolic link: " + legacy);
                }
                boolean destinationMissing =
                    !Files.exists(workspaceHome, LinkOption.NOFOLLOW_LINKS);
                boolean destinationEmpty = !destinationMissing && isEmptyDirectory(workspaceHome);
                if (Files.isDirectory(legacy, LinkOption.NOFOLLOW_LINKS)) {
                  if (destinationMissing || destinationEmpty) {
                    Path staged =
                        workspaceHome.resolveSibling(
                            workspaceHome.getFileName() + ".migration-tmp");
                    if (Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) {
                      deleteTree(staged);
                    }
                    copyTree(legacy, staged);
                    if (destinationEmpty) {
                      Files.delete(workspaceHome);
                    }
                    try {
                      Files.move(staged, workspaceHome, StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                      Files.move(staged, workspaceHome);
                    }
                  } else {
                    ensurePlainDirectory(workspaceHome);
                    mergeLegacyTree(legacy, workspaceHome);
                  }
                }
                ensurePlainDirectory(workspaceHome);
                machine.setHomePath(workspaceHome.toAbsolutePath().normalize().toString());
                machines.save(machine);
              } catch (IOException failure) {
                throw new UncheckedIOException(
                    "cannot migrate workspace home " + workspace.getWorkspaceId(), failure);
              }
            });
  }

  private static void ensurePlainDirectory(Path path) throws IOException {
    if (Files.isSymbolicLink(path)) {
      throw new IOException("workspace storage path is a symbolic link: " + path);
    }
    Files.createDirectories(path);
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("workspace storage path is not a plain directory: " + path);
    }
  }

  private static void copyTree(Path source, Path destination) throws IOException {
    Files.createDirectories(destination);
    try (var paths = Files.walk(source)) {
      for (Path item : paths.toList()) {
        if (item.equals(source)) {
          continue;
        }
        Path target = destination.resolve(source.relativize(item));
        if (Files.isSymbolicLink(item)) {
          Files.createSymbolicLink(target, Files.readSymbolicLink(item));
        } else if (Files.isDirectory(item, LinkOption.NOFOLLOW_LINKS)) {
          Files.createDirectories(target);
        } else if (Files.isRegularFile(item, LinkOption.NOFOLLOW_LINKS)) {
          Files.copy(item, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
      }
    }
  }

  private static void mergeLegacyTree(Path source, Path destination) throws IOException {
    Path normalizedDestination = destination.toAbsolutePath().normalize();
    try (var paths = Files.walk(source)) {
      for (Path item :
          paths.sorted(java.util.Comparator.comparingInt(Path::getNameCount)).toList()) {
        if (item.equals(source)) {
          continue;
        }
        Path target = normalizedDestination.resolve(source.relativize(item)).normalize();
        if (!target.startsWith(normalizedDestination)) {
          throw new IOException("legacy workspace entry escapes its destination: " + item);
        }
        if (Files.isSymbolicLink(item)) {
          Path linkTarget = Files.readSymbolicLink(item);
          if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isSymbolicLink(target)
                || !Files.readSymbolicLink(target).equals(linkTarget)) {
              throw conflict(target);
            }
          } else {
            Files.createSymbolicLink(target, linkTarget);
          }
        } else if (Files.isDirectory(item, LinkOption.NOFOLLOW_LINKS)) {
          if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(target)
                || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
              throw conflict(target);
            }
          } else {
            Files.createDirectory(target);
          }
        } else if (Files.isRegularFile(item, LinkOption.NOFOLLOW_LINKS)) {
          if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(target)
                || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || Files.mismatch(item, target) != -1) {
              throw conflict(target);
            }
          } else {
            Files.copy(item, target, StandardCopyOption.COPY_ATTRIBUTES);
          }
        } else {
          throw new IOException("legacy workspace contains an unsupported entry: " + item);
        }
      }
    }
  }

  private static IOException conflict(Path path) {
    return new IOException(
        "legacy workspace data conflicts at " + path + "; resolve it, then retry startup");
  }

  private static void deleteTree(Path root) throws IOException {
    try (var paths = Files.walk(root)) {
      for (Path item : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(item);
      }
    }
  }

  private static boolean isEmptyDirectory(Path path) throws IOException {
    if (Files.isSymbolicLink(path)) {
      throw new IOException("workspace home is a symbolic link: " + path);
    }
    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    try (var entries = Files.list(path)) {
      return entries.findAny().isEmpty();
    }
  }
}
