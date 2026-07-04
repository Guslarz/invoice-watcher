package com.guslarz.invoicewatcher.eventproducer.api;

public interface EventProducer<KeyT, ValueT> {

  void send(String topic, Event<KeyT, ValueT> event);
}
