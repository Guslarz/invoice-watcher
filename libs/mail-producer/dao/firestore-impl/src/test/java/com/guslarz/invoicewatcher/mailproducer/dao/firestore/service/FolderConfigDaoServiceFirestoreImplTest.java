package com.guslarz.invoicewatcher.mailproducer.dao.firestore.service;

import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.document.FolderConfigDocument;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.repository.FolderConfigDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FolderConfigDaoServiceFirestoreImplTest {

  private static final ScanPosition POSITION = new ScanPosition(7, 14);

  private final FolderConfigDocumentRepository folderRepository = mock(FolderConfigDocumentRepository.class);
  private final FolderConfigDaoServiceFirestoreImpl uut = new FolderConfigDaoServiceFirestoreImpl(folderRepository);

  @Test
  @DisplayName("saves the scan position of an existing folder")
  void savesPositionOfExistingFolder() {
    // given
    FolderConfigDocument doc = FolderConfigDocument.builder().id("f1").name("INBOX").build();
    when(folderRepository.findById("f1")).thenReturn(Optional.of(doc));

    // when
    uut.updateScanPosition(folder("f1"), POSITION);

    // then
    verify(folderRepository).save(doc);
    assertThat(doc.getUidValidity()).isEqualTo(7L);
    assertThat(doc.getLastUid()).isEqualTo(14L);
  }

  @Test
  @DisplayName("ignores a folder that no longer exists")
  void ignoresMissingFolder() {
    // given
    when(folderRepository.findById("f1")).thenReturn(Optional.empty());

    // when
    uut.updateScanPosition(folder("f1"), POSITION);

    // then
    verify(folderRepository, never()).save(any());
  }

  @Test
  @DisplayName("ignores a folder without an id")
  void ignoresFolderWithoutId() {
    // when
    uut.updateScanPosition(folder(null), POSITION);

    // then
    verify(folderRepository, never()).findById(any());
    verify(folderRepository, never()).save(any());
  }

  private static FolderConfig folder(String id) {
    return FolderConfig.builder().id(id).name("INBOX").build();
  }
}
