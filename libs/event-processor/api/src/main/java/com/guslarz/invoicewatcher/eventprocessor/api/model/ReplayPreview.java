package com.guslarz.invoicewatcher.eventprocessor.api.model;

import java.util.List;

public record ReplayPreview(int count, List<Long> offsets, double estimatedSeconds) {
  public ReplayPreview {
    offsets = List.copyOf(offsets);
  }
}
