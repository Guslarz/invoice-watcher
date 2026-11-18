package com.guslarz.invoicewatcher.eventprocessor.api.service;

import com.guslarz.invoicewatcher.eventprocessor.api.model.EventPublication;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingStage;
import com.guslarz.invoicewatcher.eventprocessor.api.model.RawEventRecord;
import java.util.Map;

/** Broker adapter; publication returns only after durable broker confirmation. */
public interface ProcessorTransport {
  void preflight(ProcessingStage stage);

  void publish(EventPublication publication);

  RawEventRecord read(String topic, int partition, long offset);

  Map<Integer, Long> lag(String topic, String group);
}
