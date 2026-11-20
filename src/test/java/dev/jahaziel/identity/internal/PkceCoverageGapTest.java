/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import dev.jahaziel.identity.internal.devconnect.Pkce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class PkceCoverageGapTest {
    @Test
    void generatorsAndChallenge() {
        assertEquals(43, Pkce.verifier().length());
        assertEquals(43, Pkce.state().length());
        assertEquals(43, Pkce.nonce().length());
        assertEquals(Pkce.challenge("verifier"), Pkce.challenge("verifier"));
        assertTrue(!Pkce.challenge("a").equals(Pkce.challenge("b")));
    }

    @Test
    void challengeWithoutShaThrowsState() {
        try (MockedStatic<MessageDigest> digest = mockStatic(MessageDigest.class)) {
            digest.when(
                            new MockedStatic.Verification() {
                                @Override
                                public void apply() throws Throwable {
                                    MessageDigest.getInstance("SHA-256");
                                }
                            })
                    .thenThrow(new NoSuchAlgorithmException("gone"));
            assertThrows(
                    IllegalStateException.class,
                    new Executable() {
                        @Override
                        public void execute() {
                            Pkce.challenge("verifier");
                        }
                    });
        }
    }
}
