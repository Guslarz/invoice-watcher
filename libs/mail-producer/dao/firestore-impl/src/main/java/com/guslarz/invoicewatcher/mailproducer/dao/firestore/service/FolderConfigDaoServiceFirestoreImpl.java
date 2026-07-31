package com.guslarz.invoicewatcher.mailproducer.dao.firestore.service;

import com.google.cloud.firestore.Firestore;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.FolderConfigDaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
public class FolderConfigDaoServiceFirestoreImpl implements FolderConfigDaoService {

  private static final String FOLDERS_COLLECTION = "mail-producer-folders";

  private final Firestore firestore;

  @Override
  public void updateScanTimestamp(FolderConfig folderConfig, ZonedDateTime scanTimestamp) {
    try {
      firestore.collection(FOLDERS_COLLECTION)
          .document(folderConfig.id())
          .update(Map.of("scanTimestamp", scanTimestamp.toString()))
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while updating scan timestamp", e);
    } catch (ExecutionException e) {
      throw new RuntimeException("Failed to update scan timestamp", e);
    }
  }
}
