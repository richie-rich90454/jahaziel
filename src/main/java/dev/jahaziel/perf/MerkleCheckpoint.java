/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.perf;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Periodic hash checkpoints for an append-only audit chain. A checkpoint covers the first
 * {@code index} entries; tail verification then starts from its chain hash and touches only the
 * entries after the checkpoint, which is O(k) instead of O(n) for a full replay.
 */
public final class MerkleCheckpoint {
  public static final int INTERVAL = 1000;
  public static final String GENESIS = "genesis";
  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private final int index;
  private final String chainHash;
  private final String merkleRoot;

  public MerkleCheckpoint(int index, String chainHash, String merkleRoot) {
    if (index < 0) {
      throw new IllegalArgumentException("index must not be negative");
    }
    this.index = index;
    this.chainHash = Objects.requireNonNull(chainHash, "chainHash");
    this.merkleRoot = Objects.requireNonNull(merkleRoot, "merkleRoot");
  }

  public int index() {
    return index;
  }

  public String chainHash() {
    return chainHash;
  }

  public String merkleRoot() {
    return merkleRoot;
  }

  /** Lowercase hex SHA-256 of the UTF-8 bytes of the input. */
  public static String sha256Hex(String input) {
    Objects.requireNonNull(input, "input");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder text = new StringBuilder(bytes.length * 2);
      for (int i = 0; i < bytes.length; i++) {
        int value = bytes[i] & 0xff;
        text.append(HEX[value >>> 4]).append(HEX[value & 0x0f]);
      }
      return text.toString();
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 unavailable", failure);
    }
  }

  /** Hash of one chain link: the previous head, a separator and this entry's payload. */
  public static String chainHash(String previousHash, String payload) {
    Objects.requireNonNull(previousHash, "previousHash");
    Objects.requireNonNull(payload, "payload");
    return sha256Hex(previousHash + "|" + payload);
  }

  /** Pairwise Merkle root over already-hashed leaves; an odd leaf is carried up unchanged. */
  public static String merkleRoot(List<String> leafHashes) {
    Objects.requireNonNull(leafHashes, "leafHashes");
    if (leafHashes.isEmpty()) {
      return sha256Hex("");
    }
    List<String> level = new ArrayList<String>(leafHashes);
    while (level.size() > 1) {
      List<String> next = new ArrayList<String>();
      for (int i = 0; i < level.size(); i += 2) {
        if (i + 1 < level.size()) {
          next.add(sha256Hex(level.get(i) + "|" + level.get(i + 1)));
        } else {
          next.add(level.get(i));
        }
      }
      level = next;
    }
    return level.get(0);
  }

  /** Replays exactly {@code index} entries and returns the checkpoint covering them. */
  public static MerkleCheckpoint checkpointAt(List<String> payloads, int index) {
    Objects.requireNonNull(payloads, "payloads");
    if (index < 0 || index > payloads.size()) {
      throw new IllegalArgumentException("index outside payload range: " + index);
    }
    List<String> leaves = new ArrayList<String>();
    String hash = GENESIS;
    for (int i = 0; i < index; i++) {
      hash = chainHash(hash, payloads.get(i));
      leaves.add(hash);
    }
    return new MerkleCheckpoint(index, hash, merkleRoot(leaves));
  }

  /** Checkpoints at entry 0 and at every {@link #INTERVAL} boundary. */
  public static List<MerkleCheckpoint> build(List<String> payloads) {
    Objects.requireNonNull(payloads, "payloads");
    List<MerkleCheckpoint> result = new ArrayList<MerkleCheckpoint>();
    result.add(checkpointAt(payloads, 0));
    for (int index = INTERVAL; index <= payloads.size(); index += INTERVAL) {
      result.add(checkpointAt(payloads, index));
    }
    return result;
  }

  /** Full O(n) verification: replay every entry from genesis and compare the final hash. */
  public static boolean verifyFull(List<String> payloads, String expectedFinalHash) {
    if (payloads == null || expectedFinalHash == null) {
      return false;
    }
    return replay(GENESIS, payloads).equals(expectedFinalHash);
  }

  /** O(k) verification: replay only the tail after a trusted checkpoint and compare the hash. */
  public static boolean verifyFromCheckpoint(
      MerkleCheckpoint checkpoint, List<String> tailPayloads, String expectedFinalHash) {
    if (checkpoint == null || tailPayloads == null || expectedFinalHash == null) {
      return false;
    }
    return replay(checkpoint.chainHash(), tailPayloads).equals(expectedFinalHash);
  }

  /** Validates a checkpoint against the prefix it claims to cover (used when anchoring one). */
  public static boolean verifyCheckpoint(MerkleCheckpoint checkpoint, List<String> prefixPayloads) {
    if (checkpoint == null || prefixPayloads == null || prefixPayloads.size() != checkpoint.index()) {
      return false;
    }
    List<String> leaves = new ArrayList<String>();
    String hash = GENESIS;
    for (int i = 0; i < prefixPayloads.size(); i++) {
      hash = chainHash(hash, prefixPayloads.get(i));
      leaves.add(hash);
    }
    return hash.equals(checkpoint.chainHash()) && merkleRoot(leaves).equals(checkpoint.merkleRoot());
  }

  private static String replay(String startHash, List<String> payloads) {
    String hash = startHash;
    for (int i = 0; i < payloads.size(); i++) {
      hash = chainHash(hash, payloads.get(i));
    }
    return hash;
  }
}
