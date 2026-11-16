package com.guslarz.invoicewatcher.userregistration.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.MailServer;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.User;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.RegistrationDao;
import com.guslarz.invoicewatcher.oauth.google.api.GoogleOauthService;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import com.guslarz.invoicewatcher.secret.api.Secret;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class RegistrationServiceTest {
  private final RegistrationDao dao = mock(RegistrationDao.class);
  private final RegistrationService uut =
      new RegistrationService(dao, mock(GoogleOauthService.class));

  @Test
  @DisplayName("REG-001: only a verified Google identity can own registration")
  void rejectsUnverifiedIdentity() throws Exception {
    // given
    var identity = mock(OidcUser.class);
    when(identity.getEmailVerified()).thenReturn(false);
    // when
    var failure = catchThrowable(() -> uut.email(identity));
    // then
    assertThat(failure).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(dao);
  }

  @Test
  @DisplayName("REG-004: oversized folder collections change nothing")
  void boundsFolderConfiguration() throws Exception {
    // given
    var identity = identity();
    var grant =
        new GmailOauthCredentials(identity.getEmail(), "grant", mock(Secret.class), 0, false);
    // when
    var failure =
        catchThrowable(
            () ->
                uut.register(
                    identity,
                    grant,
                    Collections.nCopies(
                        33, new RegistrationService.FolderInput(null, "INBOX", null))));
    // then
    assertThat(failure).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(dao);
  }

  @Test
  @DisplayName(
      "REG-005: changing a retained folder start date is rejected without resetting progress")
  void rejectsEditingExistingStartDate() throws Exception {
    // given
    var identity = identity();
    var folder =
        FolderConfig.builder()
            .id("f1")
            .name("INBOX")
            .startDate(ZonedDateTime.parse("2026-01-01T00:00:00Z"))
            .uidValidity(7L)
            .lastUid(42L)
            .build();
    var current =
        user(
            new GmailOauthCredentials(identity.getEmail(), "g", mock(Secret.class), 0, false),
            folder);
    Mockito.when(
            dao.update(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.anyLong(),
                ArgumentMatchers.any()))
        .thenAnswer(call -> call.<UnaryOperator<User>>getArgument(3).apply(current));
    // when
    var failure =
        catchThrowable(
            () ->
                uut.edit(
                    identity,
                    0,
                    List.of(
                        new RegistrationService.FolderInput(
                            "f1", "INBOX", "2026-02-01T00:00:00Z"))));
    // then
    assertThat(failure).isInstanceOf(IllegalArgumentException.class);
    assertThat(folder.lastUid()).isEqualTo(42);
  }

  @Test
  @DisplayName(
      "REG-013: omitted callback refresh tokens cannot overwrite a concurrently rotated token")
  void preservesLatestTokenInsideTransaction() throws Exception {
    // given
    var identity = identity();
    var latest = mock(Secret.class);
    var credentials = new GmailOauthCredentials(identity.getEmail(), "g", latest, 4, false);
    var current = user(credentials, FolderConfig.builder().id("f1").name("INBOX").build());
    Mockito.when(
            dao.update(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.anyLong(),
                ArgumentMatchers.any()))
        .thenAnswer(call -> call.<UnaryOperator<User>>getArgument(3).apply(current));
    // when
    var updated =
        uut.connect(
            identity, 0, credentials.toBuilder().refreshToken(mock(Secret.class)).build(), false);
    // then
    var stored = (GmailOauthCredentials) updated.scanConfigs().getFirst().credentials();
    assertThat(stored.refreshToken()).isSameAs(latest);
    assertThat(stored.revision()).isEqualTo(5);
  }

  @Test
  @DisplayName(
      "REG-007 REG-014 USER-001: provider revocation failure does not retain a removed"
          + " registration")
  void deletesLocallyWhenProviderRevocationFails() throws Exception {
    // given
    var identity = identity();
    var current =
        user(
            new GmailOauthCredentials(identity.getEmail(), "g", mock(Secret.class), 0, false),
            FolderConfig.builder().name("INBOX").build());
    when(dao.find(identity.getEmail(), uut.owner(identity))).thenReturn(Optional.of(current));
    // when
    boolean revoked = uut.disconnect(identity, 0, true);
    // then
    assertThat(revoked).isFalse();
    Mockito.verify(dao).delete(identity.getEmail(), uut.owner(identity), 0);
  }

  @Test
  @DisplayName(
      "REG-014 USER-001: disconnect removes mailboxes while preserving the registration owner")
  void disconnectsMailboxWithoutDeletingRegistration() throws Exception {
    // given
    var identity = identity();
    var current =
        user(
            new GmailOauthCredentials(identity.getEmail(), "g", mock(Secret.class), 0, false),
            FolderConfig.builder().name("INBOX").build());
    when(dao.find(identity.getEmail(), uut.owner(identity))).thenReturn(Optional.of(current));
    var updated = new AtomicReference<User>();
    when(dao.update(any(), any(), anyLong(), any()))
        .thenAnswer(
            call -> {
              var result = call.<UnaryOperator<User>>getArgument(3).apply(current);
              updated.set(result);
              return result;
            });
    // when
    uut.disconnect(identity, 0, false);
    // then
    assertThat(updated.get().scanConfigs()).isEmpty();
    assertThat(updated.get().ownerSubject()).isEqualTo(current.ownerSubject());
    Mockito.verify(dao, Mockito.never()).delete(any(), any(), anyLong());
  }

  private User user(GmailOauthCredentials grant, FolderConfig folder) {
    return User.builder()
        .name(grant.username())
        .ownerSubject("https://accounts.google.com|subject")
        .configurationVersion(0L)
        .scanConfigs(
            List.of(
                new MailScanConfig(new MailServer("imap.gmail.com", 993), grant, List.of(folder))))
        .build();
  }

  private OidcUser identity() throws Exception {
    var identity = mock(OidcUser.class);
    when(identity.getIssuer()).thenReturn(new URL("https://accounts.google.com"));
    when(identity.getEmailVerified()).thenReturn(true);
    when(identity.getEmail()).thenReturn("user@example.com");
    when(identity.getSubject()).thenReturn("subject");
    return identity;
  }
}
