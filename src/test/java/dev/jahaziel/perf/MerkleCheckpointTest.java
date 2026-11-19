/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Chain checkpoint correctness: full replay and tail replay must agree and detect tampering. */
class MerkleCheckpointTest {
    private static List<String> payloads(int count) {
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            result.add("entry-" + i);
        }
        return result;
    }

    @Test
    void sha256MatchesKnownVector() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                MerkleCheckpoint.sha256Hex("abc"));
    }

    @Test
    void merkleRootOfOneLeafIsThatLeaf() {
        String leaf = MerkleCheckpoint.sha256Hex("x");
        assertEquals(leaf, MerkleCheckpoint.merkleRoot(Arrays.asList(leaf)));
    }

    @Test
    void checkpointAtMatchesFullReplay() {
        List<String> entries = payloads(7);
        MerkleCheckpoint checkpoint = MerkleCheckpoint.checkpointAt(entries, 7);
        assertEquals(7, checkpoint.index());
        assertTrue(MerkleCheckpoint.verifyFull(entries, checkpoint.chainHash()));
        assertTrue(MerkleCheckpoint.verifyCheckpoint(checkpoint, entries));
    }

    @Test
    void fullVerifyRejectsTamperedEntry() {
        List<String> entries = payloads(5);
        String finalHash = MerkleCheckpoint.checkpointAt(entries, 5).chainHash();
        assertTrue(MerkleCheckpoint.verifyFull(entries, finalHash));
        List<String> tampered = new ArrayList<String>(entries);
        tampered.set(2, "changed");
        assertFalse(MerkleCheckpoint.verifyFull(tampered, finalHash));
    }

    @Test
    void tailVerificationMatchesFullVerification() {
        List<String> entries = payloads(2500);
        String finalHash = MerkleCheckpoint.checkpointAt(entries, 2500).chainHash();
        List<MerkleCheckpoint> checkpoints = MerkleCheckpoint.build(entries);
        MerkleCheckpoint checkpoint = checkpoints.get(2);
        assertEquals(2000, checkpoint.index());
        List<String> tail = new ArrayList<String>(entries.subList(2000, 2500));
        assertTrue(MerkleCheckpoint.verifyFull(entries, finalHash));
        assertTrue(MerkleCheckpoint.verifyFromCheckpoint(checkpoint, tail, finalHash));
        assertTrue(
                MerkleCheckpoint.verifyCheckpoint(
                        checkpoint, new ArrayList<String>(entries.subList(0, 2000))));
    }

    @Test
    void tailVerificationRejectsTamperedTail() {
        List<String> entries = payloads(1900);
        String finalHash = MerkleCheckpoint.checkpointAt(entries, 1900).chainHash();
        List<MerkleCheckpoint> checkpoints = MerkleCheckpoint.build(entries);
        MerkleCheckpoint checkpoint = checkpoints.get(1);
        assertEquals(1000, checkpoint.index());
        List<String> tail = new ArrayList<String>(entries.subList(1000, 1900));
        tail.set(0, "changed");
        assertFalse(MerkleCheckpoint.verifyFromCheckpoint(checkpoint, tail, finalHash));
    }

    @Test
    void buildPlacesCheckpointsAtIntervals() {
        List<MerkleCheckpoint> checkpoints = MerkleCheckpoint.build(payloads(2001));
        assertEquals(3, checkpoints.size());
        assertEquals(0, checkpoints.get(0).index());
        assertEquals(1000, checkpoints.get(1).index());
        assertEquals(2000, checkpoints.get(2).index());
    }

    @Test
    void indexOutsideRangeThrows() {
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        MerkleCheckpoint.checkpointAt(payloads(3), 4);
                    }
                });
    }
}
