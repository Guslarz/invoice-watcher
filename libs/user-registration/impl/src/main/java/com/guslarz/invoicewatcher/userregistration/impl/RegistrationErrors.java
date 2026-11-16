package com.guslarz.invoicewatcher.userregistration.impl;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.ConfigurationConflictException;
import java.time.DateTimeException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RegistrationErrors {
  @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> invalid(RuntimeException failure) {
    return Map.of(
        "error", "Invalid configuration. Check folder names, dates and connection status.");
  }

  @ExceptionHandler({ConfigurationConflictException.class, DataIntegrityViolationException.class})
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> conflict(RuntimeException failure) {
    return Map.of(
        "error", "Configuration changed or account unavailable. Reload before trying again.");
  }
}
