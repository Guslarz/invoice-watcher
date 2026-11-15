package com.guslarz.invoicewatcher.dao.userconfiguration.api.service;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;

public interface FolderConfigDaoService {

  void updateScanPosition(FolderConfig folderConfig, ScanPosition position);
}
