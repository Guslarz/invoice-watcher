package com.guslarz.invoicewatcher.eventprocessor.api.model;

import static com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingModelSupport.identifier;

import com.guslarz.invoicewatcher.eventprocessor.api.exception.ProcessingFailure;
import java.util.List;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

/** Handler-produced evidence proposed for publication as one stage result. */
@Builder(toBuilder = true)
public record ProcessingResultCandidate(
    String localId,
    EventContract contract,
    String method,
    ProcessingOutcome outcome,
    List<String> reasonCodes,
    JsonNode evidence) {
  public ProcessingResultCandidate {
    identifier(localId);
    identifier(method);
    if (contract == null || outcome == null || evidence == null || evidence.isNull()) {
      throw new ProcessingFailure(ProcessingFailureReason.INVALID_RESULT, false);
    }
    reasonCodes = List.copyOf(reasonCodes);
    reasonCodes.forEach(ProcessingModelSupport::identifier);
  }

  @Override
  public String toString() {
    return "ProcessingResultCandidate[redacted]";
  }
}
