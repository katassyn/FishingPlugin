package org.maks.fishingPlugin.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects highly regular click intervals indicative of macros.
 */
public class AntiCheatService {

  private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
  private final Map<UUID, Deque<Long>> intervals = new ConcurrentHashMap<>();
  private final int sampleSize;
  private final long toleranceMs;

  public AntiCheatService(int sampleSize, long toleranceMs) {
    this.sampleSize = sampleSize;
    this.toleranceMs = toleranceMs;
  }

  public AntiCheatService() {
    this(5, 30); // defaults
  }

  /**
   * Record a click timestamp. Returns true if the recent intervals are
   * suspiciously constant (difference between max and min below tolerance).
   */
  public boolean record(UUID player, long timestamp) {
    Long last = lastClick.put(player, timestamp);
    if (last == null) {
      return false; // first sample
    }
    long delta = timestamp - last;
    Deque<Long> deque = intervals.computeIfAbsent(player, k -> new ArrayDeque<>());
    deque.addLast(delta);
    if (deque.size() > sampleSize) {
      deque.removeFirst();
    }
    if (deque.size() < sampleSize) {
      return false;
    }
    long min = Long.MAX_VALUE;
    long max = Long.MIN_VALUE;
    for (long d : deque) {
      if (d < min) min = d;
      if (d > max) max = d;
    }
    return max - min <= toleranceMs;
  }

  /** Reset stored samples for a player. */
  public void reset(UUID player) {
    lastClick.remove(player);
    intervals.remove(player);
  }
}

