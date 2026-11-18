package com.guslarz.invoicewatcher.eventprocessor.api.service;

import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingInput;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingResultCandidate;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public interface ProcessingHandler {
  default void validate(JsonNode payload) {}

  default Set<String> reasonCodes() {
    return Set.of();
  }

  List<ProcessingResultCandidate> handle(ProcessingInput input);
}
