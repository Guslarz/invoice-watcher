package com.guslarz.invoicewatcher.mailproducer.dao.api.service;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;

import java.time.ZonedDateTime;

public interface FolderConfigDaoService {

  void updateScanTimestamp(FolderConfig folderConfig, ZonedDateTime scanTimestamp);
}
