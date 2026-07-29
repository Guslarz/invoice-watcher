package com.guslarz.invoicewatcher.mailproducer.dao.jpa.service;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.FolderConfigDaoService;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.FolderConfigEntity;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.repository.FolderConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Component
@RequiredArgsConstructor
public class FolderConfigDaoServiceJpaImpl implements FolderConfigDaoService {

  private final FolderConfigRepository folderConfigRepository;

  @Override
  @Transactional
  public void updateScanTimestamp(FolderConfig folderConfig, ZonedDateTime scanTimestamp) {
    FolderConfigEntity entity = folderConfigRepository.findById(folderConfig.id()).orElseThrow();
    entity.scanTimestamp(scanTimestamp);
    folderConfigRepository.save(entity);
  }
}
