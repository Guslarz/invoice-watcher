package com.guslarz.invoicewatcher.dao.eventprocessing.api.model;

import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingEntry;

public record ProcessingAdmission(ProcessingAdmissionDecision decision, ProcessingEntry entry) {}
