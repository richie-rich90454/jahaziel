/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.domain;

import java.util.UUID;

/** Fixed local identity used only by the explicitly enabled demo mode. */
public final class DemoIdentity {
  public static final UserId USER_ID =
      new UserId(UUID.fromString("00000000-0000-4000-8000-000000000001"));

  private DemoIdentity() {}
}
