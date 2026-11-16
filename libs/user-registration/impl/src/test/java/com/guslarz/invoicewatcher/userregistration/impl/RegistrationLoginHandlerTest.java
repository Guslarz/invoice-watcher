package com.guslarz.invoicewatcher.userregistration.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.MailServer;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.User;
import com.guslarz.invoicewatcher.oauth.google.api.GmailReconnectionRequiredException;
import com.guslarz.invoicewatcher.oauth.google.api.GoogleOauthService;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import com.guslarz.invoicewatcher.secret.api.Secret;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@ExtendWith(OutputCaptureExtension.class)
class RegistrationLoginHandlerTest {
  private final RegistrationService registration = mock(RegistrationService.class);
  private final GoogleOauthService google = mock(GoogleOauthService.class);
  private final SecretFactory secrets = mock(SecretFactory.class);
  private final OAuth2AuthorizedClientService clients = mock(OAuth2AuthorizedClientService.class);
  private final RegistrationLoginHandler uut =
      new RegistrationLoginHandler(registration, google, secrets, clients);

  @AfterEach
  void clearsSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("REG-013: expired Gmail authorization automatically continues consent during login")
  void redirectsFromLoginToConsent() throws Exception {
    // given
    var identity = identity("subject");
    var grant = grant();
    when(registration.find(identity)).thenReturn(Optional.of(user(grant)));
    when(google.accessToken(grant)).thenThrow(new GmailReconnectionRequiredException());
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    // when
    uut.onAuthenticationSuccess(request, response, authentication(identity, "google"));
    // then
    assertThat(response.getRedirectedUrl()).isEqualTo("/oauth2/authorization/gmail");
    assertThat(request.getSession().getAttribute("gmail-flow"))
        .isInstanceOf(RegistrationLoginHandler.Flow.class);
  }

  @Test
  @DisplayName("REG-012: a different Google identity cannot attach a Gmail grant")
  void rejectsDifferentCallbackAccount() throws Exception {
    // given
    var original = identity("original");
    var different = identity("different");
    var request = new MockHttpServletRequest();
    request
        .getSession()
        .setAttribute("gmail-flow", RegistrationLoginHandler.Flow.create(original, 0));
    var response = new MockHttpServletResponse();
    // when
    uut.onAuthenticationSuccess(request, response, authentication(different, "gmail"));
    // then
    assertThat(response.getRedirectedUrl()).isEqualTo("/?connection=failed");
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isSameAs(original);
    verify(registration, never()).connect(any(), anyLong(), any(), ArgumentMatchers.anyBoolean());
  }

  @Test
  @DisplayName(
      "REG-011: denied Gmail consent preserves the signed-in identity without a redirect loop")
  void restoresIdentityAfterDeniedConsent() throws Exception {
    // given
    var identity = identity("subject");
    var request = new MockHttpServletRequest();
    request
        .getSession()
        .setAttribute("gmail-flow", RegistrationLoginHandler.Flow.create(identity, 0));
    var response = new MockHttpServletResponse();
    // when
    uut.onAuthenticationFailure(
        request, response, new OAuth2AuthenticationException("access_denied"));
    // then
    assertThat(response.getRedirectedUrl()).isEqualTo("/?connection=denied");
    assertThat(request.getSession().getAttribute("gmail-flow")).isNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isSameAs(identity);
    verify(registration, never()).connect(any(), anyLong(), any(), ArgumentMatchers.anyBoolean());
  }

  @Test
  @DisplayName("REG-013: an omitted refresh token preserves the existing same-account grant")
  void preservesExistingRefreshToken() throws Exception {
    // given
    var identity = identity("subject");
    var previous = grant();
    when(registration.find(identity)).thenReturn(Optional.of(user(previous)));
    var authorized = mock(OAuth2AuthorizedClient.class);
    var access = mock(OAuth2AccessToken.class);
    when(access.getScopes()).thenReturn(Set.of("https://mail.google.com/"));
    when(authorized.getAccessToken()).thenReturn(access);
    when(clients.loadAuthorizedClient(eq("gmail"), any())).thenReturn(authorized);
    var request = new MockHttpServletRequest();
    request
        .getSession()
        .setAttribute("gmail-flow", RegistrationLoginHandler.Flow.create(identity, 0));
    var response = new MockHttpServletResponse();
    // when
    uut.onAuthenticationSuccess(request, response, authentication(identity, "gmail"));
    // then
    var capture = ArgumentCaptor.forClass(GmailOauthCredentials.class);
    verify(registration).connect(eq(identity), eq(0L), capture.capture(), eq(false));
    assertThat(capture.getValue().refreshToken()).isSameAs(previous.refreshToken());
    assertThat(response.getRedirectedUrl()).isEqualTo("/?connection=connected");
  }

  @Test
  @DisplayName("REG-008: OAuth diagnostics omit provider descriptions and exception messages")
  void reportsAuthenticationFailureWithoutSecrets(CapturedOutput output) throws Exception {
    // given
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    var failure = new OAuth2AuthenticationException(
        new OAuth2Error("invalid_client", "secret-provider-description", null),
        "secret-exception-message");
    // when
    uut.onAuthenticationFailure(request, response, failure);
    // then
    assertThat(response.getRedirectedUrl()).isEqualTo("/?connection=failed");
    assertThat(output.getAll()).contains("code=invalid_client")
        .doesNotContain("secret-provider-description", "secret-exception-message");
  }

  private OidcUser identity(String subject) throws Exception {
    var identity = mock(OidcUser.class);
    when(identity.getIssuer()).thenReturn(URI.create("https://accounts.google.com").toURL());
    when(identity.getSubject()).thenReturn(subject);
    when(identity.getEmail()).thenReturn("user@example.com");
    when(identity.getName()).thenReturn(subject);
    when(identity.getAuthorities()).thenReturn(List.of());
    when(registration.owner(identity)).thenReturn("google|" + subject);
    when(registration.email(identity)).thenReturn("user@example.com");
    return identity;
  }

  private OAuth2AuthenticationToken authentication(OidcUser identity, String client) {
    return new OAuth2AuthenticationToken(identity, identity.getAuthorities(), client);
  }

  private GmailOauthCredentials grant() {
    return new GmailOauthCredentials("user@example.com", "grant", mock(Secret.class), 0, false);
  }

  private User user(GmailOauthCredentials grant) {
    return User.builder()
        .name(grant.username())
        .ownerSubject("google|subject")
        .configurationVersion(0L)
        .scanConfigs(
            List.of(new MailScanConfig(new MailServer("imap.gmail.com", 993), grant, List.of())))
        .build();
  }
}
