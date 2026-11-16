package com.guslarz.invoicewatcher.dao.userconfiguration.api.service;

/** A stale configuration, ownership mismatch or occupied identity cannot be overwritten. */
public class ConfigurationConflictException extends RuntimeException {

  public ConfigurationConflictException() {
    super("Configuration changed or identity unavailable");
  }
}
