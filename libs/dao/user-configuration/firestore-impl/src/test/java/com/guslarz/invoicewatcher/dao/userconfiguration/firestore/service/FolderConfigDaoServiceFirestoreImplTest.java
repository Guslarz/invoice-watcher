package com.guslarz.invoicewatcher.dao.userconfiguration.firestore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.spring.data.datastore.core.DatastoreOperations;
import com.google.cloud.spring.data.datastore.core.DatastoreTemplate;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.FolderConfigDocument;
import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FolderConfigDaoServiceFirestoreImplTest {
  private final DatastoreTemplate template = mock(DatastoreTemplate.class);
  private final DatastoreOperations transaction = mock(DatastoreOperations.class);
  private final FolderConfigDaoServiceFirestoreImpl uut =
      new FolderConfigDaoServiceFirestoreImpl(template);

  @Test
  @DisplayName("REG-006: reads and writes the folder within one transaction")
  void advancesTransactionally() {
    // given
    when(template.performTransaction(any()))
        .thenAnswer(
            call -> call.<Function<DatastoreOperations, Object>>getArgument(0).apply(transaction));
    var doc = FolderConfigDocument.builder().id("f1").name("INBOX").build();
    when(transaction.findById("f1", FolderConfigDocument.class)).thenReturn(doc);
    // when
    uut.updateScanPosition(FolderConfig.builder().id("f1").build(), new ScanPosition(7, 14));
    // then
    verify(transaction).save(doc);
    assertThat(doc.getLastUid()).isEqualTo(14);
  }

  @Test
  @DisplayName("REG-007: missing folders are never recreated")
  void ignoresRemovedFolder() {
    // given
    when(template.performTransaction(any()))
        .thenAnswer(
            call -> call.<Function<DatastoreOperations, Object>>getArgument(0).apply(transaction));
    // when
    uut.updateScanPosition(FolderConfig.builder().id("gone").build(), new ScanPosition(7, 14));
    // then
    verify(transaction, never()).save(any());
  }
}
