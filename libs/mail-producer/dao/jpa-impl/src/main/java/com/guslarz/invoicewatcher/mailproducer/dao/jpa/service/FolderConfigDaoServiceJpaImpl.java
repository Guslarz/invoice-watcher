package com.guslarz.invoicewatcher.mailproducer.dao.jpa.service;

import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.FolderConfigDaoService;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.FolderConfigEntity;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.repository.FolderConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FolderConfigDaoServiceJpaImpl implements FolderConfigDaoService {

  private final FolderConfigRepository folderConfigRepository;

  @Override
  @Transactional
  public void updateScanPosition(FolderConfig folderConfig, ScanPosition position) {
    FolderConfigEntity entity = folderConfigRepository.findById(Long.parseLong(folderConfig.id())).orElseThrow();
    entity.uidValidity(position.uidValidity());
    entity.lastUid(position.lastUid());
    folderConfigRepository.save(entity);
  }
}
