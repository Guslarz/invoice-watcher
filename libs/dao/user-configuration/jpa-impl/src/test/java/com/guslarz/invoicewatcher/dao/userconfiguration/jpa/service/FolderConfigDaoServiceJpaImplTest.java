package com.guslarz.invoicewatcher.dao.userconfiguration.jpa.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.repository.FolderConfigRepository;
import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FolderConfigDaoServiceJpaImplTest {
  private final FolderConfigRepository repository = mock(FolderConfigRepository.class);
  private final FolderConfigDaoServiceJpaImpl uut = new FolderConfigDaoServiceJpaImpl(repository);

  @Test
  @DisplayName("REG-006: advances only an existing position without saving a stale entity")
  void usesConditionalPositionUpdate() {
    // given
    var folder = FolderConfig.builder().id("3").name("INBOX").build();
    // when
    uut.updateScanPosition(folder, new ScanPosition(7, 14));
    // then
    verify(repository).advance(3, 7, 14);
    verifyNoMoreInteractions(repository);
  }

  @Test
  @DisplayName("REG-007: cannot recreate a removed folder")
  void acceptsMissingRowWithoutSaving() {
    // given
    when(repository.advance(3, 7, 14)).thenReturn(0);
    // when
    uut.updateScanPosition(FolderConfig.builder().id("3").build(), new ScanPosition(7, 14));
    // then
    verify(repository).advance(3, 7, 14);
    verifyNoMoreInteractions(repository);
  }
}
