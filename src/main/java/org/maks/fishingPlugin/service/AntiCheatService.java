package org.maks.fishingPlugin.service;

import java.nio.ByteBuffer;
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
  private final Map<UUID, Boolean> flagged = new ConcurrentHashMap<>();
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
   * Record a click timestamp. Returns true if the recent intervals show low
   * variance or form a repeating pattern within tolerance.
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

    int n = deque.size();
    long[] arr = new long[n];
    int i = 0;
    double mean = 0;
    for (long d : deque) {
      arr[i++] = d;
      mean += d;
    }
    mean /= n;
    double var = 0;
    for (long d : arr) {
      double diff = d - mean;
      var += diff * diff;
    }
    var /= n;
    if (Math.sqrt(var) <= toleranceMs) {
      return true;
    }

    for (int p = 1; p <= n / 2; p++) {
      boolean ok = true;
      for (int j = p; j < n; j++) {
        if (Math.abs(arr[j] - arr[j - p]) > toleranceMs) {
          ok = false;
          break;
        }
      }
      if (ok) {
        return true;
      }
    }
    return false;
  }

  /** Reset stored samples for a player. */
  public void reset(UUID player) {
    lastClick.remove(player);
    intervals.remove(player);
    flagged.remove(player);
  }

  /** Mark player for drop penalty. */
  public void flag(UUID player) {
    flagged.put(player, Boolean.TRUE);
  }

  /** Consume penalty flag for player. */
  public boolean consumeFlag(UUID player) {
    return flagged.remove(player) != null;
  }

  /** Serialize stored samples for a player to a byte array. */
  public byte[] serialize(UUID player) {
    Long last = lastClick.get(player);
    Deque<Long> deque = intervals.get(player);
    if (last == null || deque == null || deque.isEmpty()) {
      return null;
    }
    ByteBuffer buf = ByteBuffer.allocate(8 + deque.size() * 8);
    buf.putLong(last);
    for (long d : deque) {
      buf.putLong(d);
    }
    return buf.array();
  }

  /** Restore stored samples for a player from a byte array. */
  public void deserialize(UUID player, byte[] data) {
    reset(player);
    if (data == null || data.length < 8) {
      return;
    }
    ByteBuffer buf = ByteBuffer.wrap(data);
    long last = buf.getLong();
    lastClick.put(player, last);
    Deque<Long> deque = new ArrayDeque<>();
    while (buf.remaining() >= 8) {
      deque.addLast(buf.getLong());
    }
    if (!deque.isEmpty()) {
      intervals.put(player, deque);
    }
  }
}

