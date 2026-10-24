package com.guslarz.invoicewatcher.mailproducer.dao.api.service;

import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;

public interface FolderConfigDaoService {

  void updateScanPosition(FolderConfig folderConfig, ScanPosition position);
}
