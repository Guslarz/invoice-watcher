package com.guslarz.invoicewatcher.dao.userconfiguration.jpa.service;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.FolderConfigDaoService;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.repository.FolderConfigRepository;
import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FolderConfigDaoServiceJpaImpl implements FolderConfigDaoService {

  private final FolderConfigRepository folderConfigRepository;

  @Override
  @Transactional
  public void updateScanPosition(FolderConfig folderConfig, ScanPosition position) {
    id(folderConfig)
        .ifPresent(
            folderId ->
                folderConfigRepository.advance(
                    folderId, position.uidValidity(), position.lastUid()));
  }

  private static Optional<Long> id(FolderConfig folderConfig) {
    try {
      return Optional.ofNullable(folderConfig.id()).map(Long::parseLong);
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}
