package com.guslarz.invoicewatcher.mailproducer.dao.firestore.service;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.FolderConfigDaoService;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.repository.FolderConfigDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@Component
@RequiredArgsConstructor
public class FolderConfigDaoServiceFirestoreImpl implements FolderConfigDaoService {

  private final FolderConfigDocumentRepository folderRepository;

  @Override
  public void updateScanTimestamp(FolderConfig folderConfig, ZonedDateTime scanTimestamp) {
    folderRepository.findById(folderConfig.id()).ifPresent(doc -> {
      doc.setScanTimestamp(scanTimestamp.toString());
      folderRepository.save(doc);
    });
  }
}
