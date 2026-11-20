/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.UUID;

import javax.crypto.Mac;

class ShareCoverageGapTest {
    private AuthorizationService allowAll() {
        AuthorizationService auth = mock(AuthorizationService.class);
        when(auth.isAllowed(any(), eq("read"), any())).thenReturn(true);
        return auth;
    }

    private AuthorizationService denyAll() {
        AuthorizationService auth = mock(AuthorizationService.class);
        when(auth.isAllowed(any(), eq("read"), any())).thenReturn(false);
        return auth;
    }

    private Principal principal(final UUID id) {
        return new Principal() {
            @Override
            public String getName() {
                return id.toString();
            }
        };
    }

    private Principal badPrincipal() {
        return new Principal() {
            @Override
            public String getName() {
                return "nope";
            }
        };
    }

    @Test
    void explicitSecretRoundTrip() {
        ShareController controller = new ShareController(allowAll(), "s3cret");
        UUID user = UUID.randomUUID();
        UUID workspace = UUID.randomUUID();
        ShareController.ShareResponse response = controller.share(workspace, principal(user));
        assertTrue(response.url().endsWith(response.token()));
        assertEquals(workspace, controller.redeem(response.token()).workspaceId());
    }

    @Test
    void blankSecretsGenerateEphemeral() {
        for (String secret : new String[] {null, "", "   "}) {
            ShareController controller = new ShareController(allowAll(), secret);
            UUID user = UUID.randomUUID();
            UUID workspace = UUID.randomUUID();
            ShareController.ShareResponse response = controller.share(workspace, principal(user));
            assertEquals(workspace, controller.redeem(response.token()).workspaceId());
        }
    }

    @Test
    void shareDenied() {
        final ShareController controller = new ShareController(denyAll(), "s3cret");
        assertThrows(
                AccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.share(UUID.randomUUID(), principal(UUID.randomUUID()));
                    }
                });
    }

    @Test
    void shareRejectsNonUuidPrincipal() {
        final ShareController controller = new ShareController(allowAll(), "s3cret");
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.share(UUID.randomUUID(), badPrincipal());
                    }
                });
    }

    @Test
    void redeemRejectsMalformed() {
        final ShareController controller = new ShareController(allowAll(), "s3cret");
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.redeem("nope");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.redeem("a.b");
                    }
                });
    }

    @Test
    void redeemRejectsBadPayload() {
        final ShareController controller = new ShareController(allowAll(), "s3cret");
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.redeem(UUID.randomUUID() + ".notanumber.00");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.redeem("not-a-uuid.123.00");
                    }
                });
    }

    @Test
    void redeemRejectsTamperedSignature() {
        ShareController controller = new ShareController(allowAll(), "s3cret");
        String token = controller.share(UUID.randomUUID(), principal(UUID.randomUUID())).token();
        char last = token.charAt(token.length() - 1);
        final String tampered = token.substring(0, token.length() - 1) + (last == '0' ? '1' : '0');
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.redeem(tampered);
                    }
                });
    }

    @Test
    void revokeRoundTrip() {
        ShareController creator = new ShareController(allowAll(), "s3cret");
        final String token = creator.share(UUID.randomUUID(), principal(UUID.randomUUID())).token();
        ShareController revoker = new ShareController(allowAll(), "s3cret");
        revoker.revoke(token, principal(UUID.randomUUID()));
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        revoker.redeem(token);
                    }
                });
    }

    @Test
    void revokeDeniedAndBogus() {
        ShareController creator = new ShareController(allowAll(), "s3cret");
        final String token = creator.share(UUID.randomUUID(), principal(UUID.randomUUID())).token();
        final ShareController denier = new ShareController(denyAll(), "s3cret");
        assertThrows(
                AccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        denier.revoke(token, principal(UUID.randomUUID()));
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        denier.revoke("bogus", principal(UUID.randomUUID()));
                    }
                });
    }

    @Test
    void signFailureSurfacesAsIllegalState() {
        final ShareController controller = new ShareController(allowAll(), "s3cret");
        try (MockedStatic<Mac> mac = Mockito.mockStatic(Mac.class)) {
            mac.when(
                            new MockedStatic.Verification() {
                                @Override
                                public void apply() throws Throwable {
                                    Mac.getInstance(anyString());
                                }
                            })
                    .thenThrow(new NoSuchAlgorithmException("boom"));
            assertThrows(
                    IllegalStateException.class,
                    new Executable() {
                        @Override
                        public void execute() {
                            controller.share(UUID.randomUUID(), principal(UUID.randomUUID()));
                        }
                    });
        }
    }
}
