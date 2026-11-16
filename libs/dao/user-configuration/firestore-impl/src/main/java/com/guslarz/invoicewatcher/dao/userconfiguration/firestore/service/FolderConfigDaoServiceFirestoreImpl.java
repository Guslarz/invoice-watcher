package com.guslarz.invoicewatcher.dao.userconfiguration.firestore.service;

import com.google.cloud.spring.data.datastore.core.DatastoreTemplate;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.FolderConfigDaoService;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.FolderConfigDocument;
import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FolderConfigDaoServiceFirestoreImpl implements FolderConfigDaoService {

  @Qualifier("daoFirestoreDatastoreTemplate")
  private final DatastoreTemplate template;

  @Override
  public void updateScanPosition(FolderConfig folderConfig, ScanPosition position) {
    if (folderConfig.id() == null) {
      return;
    }
    template.performTransaction(
        tx -> {
          var doc = tx.findById(folderConfig.id(), FolderConfigDocument.class);
          if (doc != null) {
            doc.setUidValidity(position.uidValidity());
            doc.setLastUid(position.lastUid());
            tx.save(doc);
          }
          return null;
        });
  }
}
