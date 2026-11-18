package com.guslarz.invoicewatcher.eventprocessor.api.model;

import static com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingModelSupport.identifier;

import lombok.Builder;

@Builder(toBuilder = true)
public record ReplayRequest(
    String requestId,
    String stage,
    String processingVersion,
    int partition,
    long fromOffset,
    long toOffset,
    int limit,
    double rate,
    String operator,
    String reason) {
  public ReplayRequest {
    identifier(requestId);
    identifier(stage);
    identifier(processingVersion);
    identifier(operator);
    identifier(reason);
    if (partition < 0
        || fromOffset < 0
        || toOffset <= fromOffset
        || limit < 1
        || !Double.isFinite(rate)
        || rate <= 0
        || toOffset - fromOffset > limit) {
      throw new IllegalArgumentException("Invalid replay selection");
    }
  }
}
