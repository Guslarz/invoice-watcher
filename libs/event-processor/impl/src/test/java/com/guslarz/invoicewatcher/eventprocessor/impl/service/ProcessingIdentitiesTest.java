package com.guslarz.invoicewatcher.eventprocessor.impl.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcessingIdentitiesTest {
  @Test
  @DisplayName("PROCESSOR-003: canonical identities are tenant/version scoped and punctuation safe")
  void separatesCanonicalIdentityComponents() {
    // given
    String stable =
        ProcessingIdentities.hash(
            "processing-v1", "tenant", "stage", "mail.ingested", "1", "source", "v1");
    // when
    String duplicate =
        ProcessingIdentities.hash(
            "processing-v1", "tenant", "stage", "mail.ingested", "1", "source", "v1");
    // then
    assertThat(duplicate).isEqualTo(stable);
    assertThat(stable)
        .isNotEqualTo(
            ProcessingIdentities.hash(
                "processing-v1", "other", "stage", "mail.ingested", "1", "source", "v1"));
    assertThat(ProcessingIdentities.hash("processing-v1", "a:b", "c"))
        .isNotEqualTo(ProcessingIdentities.hash("processing-v1", "a", "b:c"));
    assertThat(ProcessingIdentities.hash("processing-v1", "Żółć", "v1"))
        .isNotEqualTo(ProcessingIdentities.hash("processing-v1", "żółć", "v1"));
  }
}
