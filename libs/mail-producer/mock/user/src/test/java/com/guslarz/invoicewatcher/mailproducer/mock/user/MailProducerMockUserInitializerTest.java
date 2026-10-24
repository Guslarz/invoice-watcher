package com.guslarz.invoicewatcher.mailproducer.mock.user;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.UserDaoService;
import com.guslarz.invoicewatcher.secret.api.Secret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MailProducerMockUserInitializerTest {

  private final UserDaoService userDaoService = mock(UserDaoService.class);
  private final MailProducerMockUserInitializer uut = new MailProducerMockUserInitializer(
      new MailProducerMockUserProperties(true, "imap.example.com", 993, "jan@example.com",
          mock(Secret.class), List.of("INBOX", "Invoices")),
      userDaoService
  );

  @Test
  @DisplayName("USER-031: resets the mock user's folders to a start date ten days back and no position")
  void resetsFoldersToStartDateWithoutPosition() {
    // given
    final ZonedDateTime tenDaysAgo = ZonedDateTime.now().minusDays(10);

    // when
    uut.upsertMockUser();

    // then
    ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
    verify(userDaoService).upsertUserByName(user.capture());
    Collection<FolderConfig> folders = user.getValue().scanConfigs().getFirst().folders();
    assertThat(folders).extracting(FolderConfig::name).containsExactly("INBOX", "Invoices");
    assertThat(folders).allSatisfy(folder -> {
      assertThat(folder.startDate()).isCloseTo(tenDaysAgo, within(1, ChronoUnit.MINUTES));
      assertThat(folder.uidValidity()).isNull();
      assertThat(folder.lastUid()).isNull();
    });
  }
}
