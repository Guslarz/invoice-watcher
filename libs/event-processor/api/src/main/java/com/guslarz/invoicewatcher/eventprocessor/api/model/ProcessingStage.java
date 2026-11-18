package com.guslarz.invoicewatcher.eventprocessor.api.model;

import static com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingModelSupport.identifier;

import com.guslarz.invoicewatcher.eventprocessor.api.service.ProcessingHandler;
import java.util.List;
import java.util.Set;
import lombok.Builder;

@Builder(toBuilder = true)
public record ProcessingStage(
    String name,
    String processingVersion,
    String inputTopic,
    String resultTopic,
    String quarantineTopic,
    String replayTopic,
    Set<EventContract> inputs,
    Set<EventContract> outputs,
    ProcessingHandler handler) {
  public ProcessingStage {
    identifier(name);
    identifier(processingVersion);
    for (String topic : List.of(inputTopic, resultTopic, quarantineTopic, replayTopic)) {
      if (!ProcessingModelSupport.TOPIC.matcher(topic).matches()) {
        throw new IllegalArgumentException("Invalid stage topic");
      }
    }
    if (Set.of(inputTopic, resultTopic, quarantineTopic, replayTopic).size() != 4
        || inputs.isEmpty()
        || outputs.isEmpty()
        || handler == null) {
      throw new IllegalArgumentException("Invalid stage declaration");
    }
    inputs = Set.copyOf(inputs);
    outputs = Set.copyOf(outputs);
  }
}
