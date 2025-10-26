package com.guslarz.invoicewatcher.mailconnector.impl.binarysearch;

import com.guslarz.invoicewatcher.mailconnector.impl.util.binarysearch.BinarySearchConfig;
import com.guslarz.invoicewatcher.mailconnector.impl.util.binarysearch.BinarySearchEngine;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BinarySearchEngineTest {

  @ParameterizedTest
  @MethodSource("shouldGetIndexEqualOrGreaterThan_args")
  void shouldGetIndexEqualOrGreaterThan(int[] array, int key, int expectedIndex) {
    // given
    BinarySearchEngine uut = new BinarySearchEngine();
    BinarySearchConfig config = BinarySearchConfig.builder()
        .itemCount(array.length)
        .searchPredicate(index -> key <= array[index])
        .build();

    // when
    int result = uut.getIndexEqualOrGreaterThan(config);

    // then
    assertThat(result).isEqualTo(expectedIndex);
  }

  private static Stream<Arguments> shouldGetIndexEqualOrGreaterThan_args() {
    return Stream.of(
        Arguments.of(new int[]{ 1, 2, 3 }, 0, 0),
        Arguments.of(new int[]{ 1, 2, 3 }, 4, 3),
        Arguments.of(new int[]{ 1, 2, 3 }, 2, 1)
    );
  }
}