package com.guslarz.invoicewatcher.eventprocessor.api.service;

import com.guslarz.invoicewatcher.eventprocessor.api.model.ReplayPreview;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ReplayRequest;

/** Applications authenticate callers before invoking execution; names are audit metadata only. */
public interface ReplayService {
  ReplayPreview preview(ReplayRequest request);

  void execute(ReplayRequest request);
}
