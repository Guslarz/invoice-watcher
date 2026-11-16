package com.guslarz.invoicewatcher.userregistration.impl;

import com.guslarz.invoicewatcher.oauth.google.api.GoogleOauthProperties;
import com.guslarz.invoicewatcher.userregistration.api.RegistrationProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class RegistrationSecurity {

  @Bean
  ClientRegistrationRepository registrations(
      GoogleOauthProperties google, RegistrationProperties app) {
    return new InMemoryClientRegistrationRepository(
        client("google", google, app, "openid", "email", "profile"),
        client("gmail", google, app, "openid", "email", "profile", "https://mail.google.com/"));
  }

  private ClientRegistration client(
      String id, GoogleOauthProperties google, RegistrationProperties app, String... scopes) {
    return CommonOAuth2Provider.GOOGLE
        .getBuilder(id)
        .clientId(google.clientId())
        .clientSecret(google.clientSecret().getDecrypted())
        .scope(scopes)
        .redirectUri(app.publicBaseUrl().resolve("/login/oauth2/code/" + id).toString())
        .build();
  }

  @Bean
  SecurityFilterChain registrationChain(
      HttpSecurity http, ClientRegistrationRepository registrations, RegistrationLoginHandler login)
      throws Exception {
    var defaults =
        new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
    OAuth2AuthorizationRequestResolver resolver =
        new OAuth2AuthorizationRequestResolver() {
          @Override
          public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
            return customize(request, defaults.resolve(request));
          }

          @Override
          public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String id) {
            return customize(request, defaults.resolve(request, id));
          }

          private OAuth2AuthorizationRequest customize(
              HttpServletRequest request, OAuth2AuthorizationRequest auth) {
            if (auth == null || !auth.getScopes().contains("https://mail.google.com/")) {
              return auth;
            }
            var session = request.getSession(false);
            var flow =
                session == null
                    ? null
                    : (RegistrationLoginHandler.Flow) session.getAttribute("gmail-flow");
            if (flow == null || flow.expiresAt() < System.currentTimeMillis()) {
              throw new AccessDeniedException("Start Gmail connection from the application");
            }
            return OAuth2AuthorizationRequest.from(auth)
                .additionalParameters(
                    params -> {
                      params.put("access_type", "offline");
                      params.put("prompt", "consent");
                      params.put("login_hint", flow.identity().getEmail());
                    })
                .build();
          }
        };
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/session",
                        "/api/csrf",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/actuator/health/**",
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            errors -> errors.authenticationEntryPoint((req, res, failure) -> res.sendError(401)))
        .oauth2Login(
            oauth ->
                oauth
                    .authorizationEndpoint(
                        endpoint -> endpoint.authorizationRequestResolver(resolver))
                    .successHandler(login)
                    .failureHandler(login))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/logout")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
        .headers(
            headers ->
                headers.contentSecurityPolicy(
                    csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
    return http.build();
  }
}
