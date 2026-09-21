/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal.devconnect;

import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.BffSessionRepository;
import dev.jahaziel.identity.persistence.OidcPendingRepository;
import dev.jahaziel.identity.persistence.UserRoleRepository;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * DevConnect wiring. Every IdP call shares one request factory with a ten-second connect and read
 * timeout, so a hung or unreachable issuer cannot stall a login.
 */
@Configuration
@EnableConfigurationProperties(DevConnectProperties.class)
public class DevConnectConfig {
  /**
   * Returns the request factory used for every IdP call.
   *
   * @return the factory, with a ten-second connect and read timeout
   */
  @Bean
  public ClientHttpRequestFactory devConnectRequestFactory() {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofSeconds(10));
    return factory;
  }

  /**
   * Returns the OIDC REST client.
   *
   * @param factory the bounded request factory
   * @return the client
   */
  @Bean
  public RestClient devConnectRestClient(ClientHttpRequestFactory devConnectRequestFactory) {
    return RestClient.builder().requestFactory(devConnectRequestFactory).build();
  }

  /**
   * Returns the DevConnect IdP client.
   *
   * @param devConnectRestClient the OIDC REST client
   * @param devConnectRequestFactory the bounded request factory, for JWKS fetches
   * @param props the DevConnect settings
   * @param clock the clock
   * @return the client
   */
  @Bean
  public DevConnectClient devConnectClient(
      RestClient devConnectRestClient,
      ClientHttpRequestFactory devConnectRequestFactory,
      DevConnectProperties props,
      Clock clock) {
    return new DevConnectClient(
        devConnectRestClient, new RestTemplate(devConnectRequestFactory), props, clock);
  }

  /**
   * Returns the DevConnect login service.
   *
   * @param props the DevConnect settings
   * @param client the IdP client
   * @param pending the half-finished login store
   * @param sessions the browser session store
   * @param users the account store
   * @param roles the role-grant store
   * @param tokens the API token issuer
   * @param clock the clock
   * @return the service
   */
  @Bean
  public DevConnectService devConnectService(
      DevConnectProperties props,
      DevConnectClient client,
      OidcPendingRepository pending,
      BffSessionRepository sessions,
      AppUserRepository users,
      UserRoleRepository roles,
      JwtService tokens,
      Clock clock) {
    return new DevConnectServiceImpl(props, client, pending, sessions, users, roles, tokens, clock);
  }
}
