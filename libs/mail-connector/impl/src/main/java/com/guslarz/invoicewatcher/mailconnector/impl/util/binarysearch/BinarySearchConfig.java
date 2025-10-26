package com.guslarz.invoicewatcher.mailconnector.impl.util.binarysearch;

import lombok.Builder;

import java.util.function.Predicate;

@Builder
public record BinarySearchConfig(
    int itemCount,
    Predicate<Integer> searchPredicate
) {
}
