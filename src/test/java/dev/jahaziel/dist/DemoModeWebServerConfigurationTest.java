/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.InetAddress;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class DemoModeWebServerConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(DemoModeWebServerConfiguration.class, DemoPropertyProbe.class);

  @Test
  void doesNotAddTheDemoCustomizerWhenDemoIsDisabled() {
    runner.run(context -> assertFalse(context.containsBean("demoModeLoopbackCustomizer")));
  }

  @Test
  void jvmDemoPropertyOverridesAnAllInterfacesServerAddress() throws Exception {
    runner
        .withSystemProperties("jahaziel.demo=true", "server.address=0.0.0.0")
        .run(this::assertLoopbackCustomizer);
  }

  @Test
  void environmentAliasActivatesTheLoopbackCustomizer() throws Exception {
    runner
        .withInitializer(
            context ->
                ((ConfigurableEnvironment) context.getEnvironment())
                    .getPropertySources()
                    .addFirst(
                        new SystemEnvironmentPropertySource(
                            "testEnvironment",
                            Map.of("JAHAZIEL_DEMO", "true", "SERVER_ADDRESS", "0.0.0.0"))))
        .run(this::assertLoopbackCustomizer);
  }

  @Test
  void springConfigDemoPropertyAlsoUsesLoopback() throws Exception {
    runner
        .withPropertyValues("jahaziel.demo=true", "server.address=0.0.0.0")
        .run(this::assertLoopbackCustomizer);
  }

  @Test
  void booleanAliasesEnableLoopbackWheneverInjectedDemoModeIsEnabled() {
    for (String value : new String[] {"true", "on", "yes", "1"}) {
      runner
          .withPropertyValues("jahaziel.demo=" + value)
          .run(
              context -> {
                boolean injectedDemoMode = context.getBean("demoModeEnabled", Boolean.class);
                assertTrue(injectedDemoMode, "Spring should parse demo mode value: " + value);
                assertTrue(
                    context.containsBean("demoModeLoopbackCustomizer"),
                    "demo mode " + value + " must force loopback");
              });
    }
  }

  @SuppressWarnings("unchecked")
  private void assertLoopbackCustomizer(
      org.springframework.context.ConfigurableApplicationContext context) {
    assertTrue(context.getBean("demoModeEnabled", Boolean.class));
    assertTrue(context.containsBean("demoModeLoopbackCustomizer"));
    ConfigurableWebServerFactory factory = mock(ConfigurableWebServerFactory.class);
    WebServerFactoryCustomizer<ConfigurableWebServerFactory> customizer =
        (WebServerFactoryCustomizer<ConfigurableWebServerFactory>)
            context.getBean("demoModeLoopbackCustomizer");
    customizer.customize(factory);
    try {
      verify(factory).setAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}));
    } catch (java.net.UnknownHostException impossible) {
      throw new AssertionError("IPv4 loopback address must be valid", impossible);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class DemoPropertyProbe {
    @Bean
    Boolean demoModeEnabled(@Value("${jahaziel.demo:false}") boolean demoMode) {
      return demoMode;
    }
  }
}
