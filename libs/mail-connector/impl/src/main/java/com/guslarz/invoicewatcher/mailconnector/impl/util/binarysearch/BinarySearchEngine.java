package com.guslarz.invoicewatcher.mailconnector.impl.util.binarysearch;

public class BinarySearchEngine {

  public int getIndexEqualOrGreaterThan(BinarySearchConfig config) {
    int low = -1;
    int high = config.itemCount();
    while (high - low > 1) {
      int mid = (high + low) / 2;
      if (config.searchPredicate().test(mid)) {
        high = mid;
      } else {
        low = mid;
      }
    }
    return high;
  }
}
