package org.maks.fishingPlugin.util;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToDoubleFunction;

/**
 * Generic weighted random picker without soft caps.
 */
public final class WeightedPicker<T> {
  private final List<T> entries;
  private final ToDoubleFunction<T> weightFn;

  public WeightedPicker(List<T> entries, ToDoubleFunction<T> weightFn) {
    this.entries = List.copyOf(entries);
    this.weightFn = weightFn;
  }

  /**
   * Picks a random entry using roulette-wheel selection.
   */
  public T pick(ThreadLocalRandom rnd) {
    double sum = 0.0;
    for (T e : entries) {
      sum += Math.max(0.0, weightFn.applyAsDouble(e));
    }
    double r = rnd.nextDouble(sum);
    for (T e : entries) {
      double w = Math.max(0.0, weightFn.applyAsDouble(e));
      if ((r -= w) <= 0) {
        return e;
      }
    }
    return entries.get(entries.size() - 1);
  }
}
