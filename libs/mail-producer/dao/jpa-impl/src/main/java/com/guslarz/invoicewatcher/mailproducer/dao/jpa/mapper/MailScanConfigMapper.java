package com.guslarz.invoicewatcher.mailproducer.dao.jpa.mapper;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailServer;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.FolderConfigEntity;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.MailScanConfigEntity;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.MailServerEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MailScanConfigMapper {

  private final MailCredentialsMapper mailCredentialsMapper;

  public MailScanConfig convert(MailScanConfigEntity entity) {
    return MailScanConfig.builder()
        .server(convert(entity.server()))
        .credentials(mailCredentialsMapper.convert(entity.credentials()))
        .folders(entity.folders().stream()
            .map(this::convert)
            .toList())
        .build();
  }

  public MailScanConfigEntity convert(MailScanConfig entity) {
    return MailScanConfigEntity.builder()
        .server(convert(entity.server()))
        .credentials(mailCredentialsMapper.convert(entity.credentials()))
        .folders(entity.folders().stream()
            .map(this::convert)
            .toList())
        .build();
  }

  private MailServer convert(MailServerEntity mailServer) {
    return MailServer.builder()
        .host(mailServer.host())
        .port(mailServer.port())
        .build();
  }

  private MailServerEntity convert(MailServer mailServer) {
    return MailServerEntity.builder()
        .host(mailServer.host())
        .port(mailServer.port())
        .build();
  }

  private FolderConfig convert(FolderConfigEntity folder) {
    return FolderConfig.builder()
        .id(folder.id() != null ? String.valueOf(folder.id()) : null)
        .name(folder.name())
        .startDate(folder.startDate())
        .uidValidity(folder.uidValidity())
        .lastUid(folder.lastUid())
        .build();
  }

  private FolderConfigEntity convert(FolderConfig folder) {
    return FolderConfigEntity.builder()
        .id(folder.id() != null ? Long.parseLong(folder.id()) : null)
        .name(folder.name())
        .startDate(folder.startDate())
        .uidValidity(folder.uidValidity())
        .lastUid(folder.lastUid())
        .build();
  }
}
