package com.guslarz.invoicewatcher.mailproducer.dao.firestore.service;

import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.FolderConfigDaoService;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.repository.FolderConfigDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FolderConfigDaoServiceFirestoreImpl implements FolderConfigDaoService {

  private final FolderConfigDocumentRepository folderRepository;

  @Override
  public void updateScanPosition(FolderConfig folderConfig, ScanPosition position) {
    folderRepository.findById(folderConfig.id()).ifPresent(doc -> {
      doc.setUidValidity(position.uidValidity());
      doc.setLastUid(position.lastUid());
      folderRepository.save(doc);
    });
  }
}
