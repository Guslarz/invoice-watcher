package com.guslarz.invoicewatcher.testing.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SharedTestcontainersTest {
  @Test
  void createsIndependentUnstartedFixturesWithPinnedImages() {
    // given
    var first = SharedTestcontainers.kafka();
    var second = SharedTestcontainers.kafka();
    // when
    var datastore = SharedTestcontainers.datastore();
    // then
    assertThat(first).isNotSameAs(second);
    assertThat(first.getContainerId()).isNull();
    assertThat(second.getContainerId()).isNull();
    assertThat(datastore.getContainerId()).isNull();
    assertThat(SharedTestcontainers.image("kafka"))
        .startsWith("apache/kafka:")
        .doesNotEndWith(":latest");
    assertThat(SharedTestcontainers.image("datastore"))
        .startsWith("gcr.io/google.com/cloudsdktool/google-cloud-cli:")
        .doesNotEndWith(":latest");
  }
}
