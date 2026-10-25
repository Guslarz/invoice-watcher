package com.guslarz.invoicewatcher.mailproducer.dao.firestore.service;

import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.FolderConfigDaoService;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.repository.FolderConfigDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FolderConfigDaoServiceFirestoreImpl implements FolderConfigDaoService {

  private final FolderConfigDocumentRepository folderRepository;

  @Override
  public void updateScanPosition(FolderConfig folderConfig, ScanPosition position) {
    Optional.ofNullable(folderConfig.id()).flatMap(folderRepository::findById).ifPresentOrElse(
        doc -> {
          doc.setUidValidity(position.uidValidity());
          doc.setLastUid(position.lastUid());
          folderRepository.save(doc);
        },
        () -> log.warn("Folder {} no longer exists, its scan position is not saved", folderConfig.name()));
  }
}
