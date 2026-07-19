package com.guslarz.invoicewatcher.eventproducer.api;

public interface EventProducer {

  void send(String topic, Event event);
}
