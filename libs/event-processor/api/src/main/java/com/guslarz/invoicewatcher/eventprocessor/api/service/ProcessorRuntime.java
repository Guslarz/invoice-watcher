package com.guslarz.invoicewatcher.eventprocessor.api.service;

import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingDisposition;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingStage;
import com.guslarz.invoicewatcher.eventprocessor.api.model.RawEventRecord;

public interface ProcessorRuntime {
  ProcessingDisposition process(ProcessingStage stage, RawEventRecord record);

  void recover(ProcessingStage stage);
}
