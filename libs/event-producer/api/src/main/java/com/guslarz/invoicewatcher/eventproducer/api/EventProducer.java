package com.guslarz.invoicewatcher.eventproducer.api;

public interface EventProducer<Key, Value> {

  void send(String topic, Event<Key, Value> event);
}
