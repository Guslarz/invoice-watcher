package com.guslarz.invoicewatcher.mailproducer.dao.jpa.service;

import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.FolderConfigDaoService;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.FolderConfigEntity;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.repository.FolderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FolderConfigDaoServiceJpaImpl implements FolderConfigDaoService {

  private final FolderConfigRepository folderConfigRepository;

  @Override
  @Transactional
  public void updateScanPosition(FolderConfig folderConfig, ScanPosition position) {
    Optional<FolderConfigEntity> entity = id(folderConfig).flatMap(folderConfigRepository::findById);
    if (entity.isEmpty()) {
      log.warn("Folder {} no longer exists, its scan position is not saved", folderConfig.name());
      return;
    }
    entity.get().uidValidity(position.uidValidity());
    entity.get().lastUid(position.lastUid());
    folderConfigRepository.save(entity.get());
  }

  private static Optional<Long> id(FolderConfig folderConfig) {
    try {
      return Optional.ofNullable(folderConfig.id()).map(Long::parseLong);
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}
