/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.internal.devconnect.DevConnectProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DevConnectPropertiesCoverageGapTest {
  @Test
  void accessorsRoundTrip() {
    DevConnectProperties props = new DevConnectProperties();
    assertEquals(null, props.getAuthIssuer());
    props.setAuthIssuer("https://idp.example.com");
    assertEquals("https://idp.example.com", props.getAuthIssuer());
    props.setRedirectUri("https://app/cb");
    assertEquals("https://app/cb", props.getRedirectUri());
    props.setClientId("client");
    assertEquals("client", props.getClientId());
    props.setClientSecret("secret");
    assertEquals("secret", props.getClientSecret());
    props.setResource("resource");
    assertEquals("resource", props.getResource());
    props.setSessionTtl(Duration.ofDays(1));
    assertEquals(Duration.ofDays(1), props.getSessionTtl());
    props.setTokenTtl(Duration.ofMinutes(2));
    assertEquals(Duration.ofMinutes(2), props.getTokenTtl());
    props.setPendingTtl(Duration.ofMinutes(3));
    assertEquals(Duration.ofMinutes(3), props.getPendingTtl());
  }

  @Test
  void configuredNeedsEveryField() {
    assertFalse(full().configured() && missingIssuer().configured());
    assertFalse(missingRedirect().configured());
    assertFalse(missingClientId().configured());
    assertFalse(missingClientSecret().configured());
    assertFalse(missingResource().configured());
    assertTrue(full().configured());
    DevConnectProperties blank = full();
    blank.setClientId("  ");
    assertFalse(blank.configured());
  }

  @Test
  void issuerBaseTrimsSlashesAndBlanks() {
    DevConnectProperties props = new DevConnectProperties();
    assertEquals("", props.issuerBase());
    props.setAuthIssuer("  ");
    assertEquals("", props.issuerBase());
    props.setAuthIssuer("https://idp.example.com/");
    assertEquals("https://idp.example.com", props.issuerBase());
    props.setAuthIssuer("  https://idp.example.com// ");
    assertEquals("https://idp.example.com", props.issuerBase());
  }

  private static DevConnectProperties full() {
    DevConnectProperties props = new DevConnectProperties();
    props.setAuthIssuer("https://idp.example.com");
    props.setRedirectUri("https://app/cb");
    props.setClientId("client");
    props.setClientSecret("secret");
    props.setResource("resource");
    return props;
  }

  private static DevConnectProperties missingIssuer() {
    DevConnectProperties props = full();
    props.setAuthIssuer(null);
    return props;
  }

  private static DevConnectProperties missingRedirect() {
    DevConnectProperties props = full();
    props.setRedirectUri(" ");
    return props;
  }

  private static DevConnectProperties missingClientId() {
    DevConnectProperties props = full();
    props.setClientId(null);
    return props;
  }

  private static DevConnectProperties missingClientSecret() {
    DevConnectProperties props = full();
    props.setClientSecret("");
    return props;
  }

  private static DevConnectProperties missingResource() {
    DevConnectProperties props = full();
    props.setResource(null);
    return props;
  }
}
