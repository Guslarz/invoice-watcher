package com.guslarz.invoicewatcher.mailproducer.dao.jpa.service;

import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.FolderConfigEntity;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.repository.FolderConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FolderConfigDaoServiceJpaImplTest {

  private static final ScanPosition POSITION = new ScanPosition(7, 14);

  private final FolderConfigRepository folderConfigRepository = mock(FolderConfigRepository.class);
  private final FolderConfigDaoServiceJpaImpl uut = new FolderConfigDaoServiceJpaImpl(folderConfigRepository);

  @Test
  @DisplayName("saves the scan position of an existing folder")
  void savesPositionOfExistingFolder() {
    // given
    FolderConfigEntity entity = FolderConfigEntity.builder().id(3L).name("INBOX").build();
    when(folderConfigRepository.findById(3L)).thenReturn(Optional.of(entity));

    // when
    uut.updateScanPosition(folder("3"), POSITION);

    // then
    verify(folderConfigRepository).save(entity);
    assertThat(entity.uidValidity()).isEqualTo(7L);
    assertThat(entity.lastUid()).isEqualTo(14L);
  }

  @Test
  @DisplayName("ignores a folder that no longer exists")
  void ignoresMissingFolder() {
    // given
    when(folderConfigRepository.findById(3L)).thenReturn(Optional.empty());

    // when
    uut.updateScanPosition(folder("3"), POSITION);

    // then
    verify(folderConfigRepository, never()).save(any());
  }

  @Test
  @DisplayName("ignores a folder without an id")
  void ignoresFolderWithoutId() {
    // when
    uut.updateScanPosition(folder(null), POSITION);

    // then
    verify(folderConfigRepository, never()).findById(any());
    verify(folderConfigRepository, never()).save(any());
  }

  private static FolderConfig folder(String id) {
    return FolderConfig.builder().id(id).name("INBOX").build();
  }
}
