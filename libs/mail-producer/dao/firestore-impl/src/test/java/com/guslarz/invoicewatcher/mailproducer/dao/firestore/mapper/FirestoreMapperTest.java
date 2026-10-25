package com.guslarz.invoicewatcher.mailproducer.dao.firestore.mapper;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.document.UserDocument;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FirestoreMapperTest {

  private final FirestoreMapper uut = new FirestoreMapper(mock(SecretFactory.class));

  @Test
  @DisplayName("maps a user without scan configs to a user with none")
  void mapsUserWithoutScanConfigs() {
    // given
    UserDocument doc = UserDocument.builder().id("jan@example.com").name("jan@example.com").build();

    // when
    User user = uut.toUser(doc, List.of());

    // then
    assertThat(user.scanConfigs()).isEmpty();
  }
}
