package com.guslarz.invoicewatcher.secret.helper.app;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnnamedVariablesTest {

  @Test
  void unnamedVariablesPassCheckstyle() {
    // given
    Object value = "value";

    // when
    String _ = "unused";
    List<String> result = List.of(value).stream()
        .map(_ -> switch (value) {
          case String _ -> "string";
          default -> "other";
        })
        .toList();

    // then
    assertThat(result).containsExactly("string");
  }
}
