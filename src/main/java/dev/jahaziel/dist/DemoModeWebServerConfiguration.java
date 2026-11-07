/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/** Binds every Spring-configured demo boot to IPv4 loopback before the server starts. */
@Configuration(proxyBeanMethods = false)
@Conditional(DemoModeEnabledCondition.class)
public class DemoModeWebServerConfiguration {
  /**
   * Forces loopback after regular server properties have been applied, regardless of whether demo
   * mode came from the CLI, an environment variable, a JVM property, or Spring config data.
   */
  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  public WebServerFactoryCustomizer<ConfigurableWebServerFactory> demoModeLoopbackCustomizer() {
    return factory -> factory.setAddress(ipv4Loopback());
  }

  private static InetAddress ipv4Loopback() {
    try {
      return InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
    } catch (UnknownHostException impossible) {
      throw new ExceptionInInitializerError(impossible);
    }
  }
}
