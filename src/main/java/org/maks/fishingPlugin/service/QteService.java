package org.maks.fishingPlugin.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

/**
 * Simple QTE system requiring players to swing the crosshair left or right and
 * confirm with a left-click.
 */
public class QteService {

  public enum Direction {LEFT, RIGHT}

  private static class State {
    Direction required;
    float startYaw;
    long expiry;
    boolean success;

    State(Direction required, float startYaw, long expiry) {
      this.required = required;
      this.startYaw = startYaw;
      this.expiry = expiry;
    }
  }

  private final Map<UUID, State> states = new ConcurrentHashMap<>();
  private final double chance;
  private final long durationMs;
  private final float yawThreshold;

  /**
   * @param chance chance a QTE will be triggered when a fish bites
   * @param durationMs how long the player has to respond
   * @param yawThreshold minimum yaw change to register a successful swipe
   */
  public QteService(double chance, long durationMs, float yawThreshold) {
    this.chance = chance;
    this.durationMs = durationMs;
    this.yawThreshold = yawThreshold;
  }

  /** Start a QTE with some probability when a fish bites. */
  public void maybeStart(Player player) {
    if (ThreadLocalRandom.current().nextDouble() > chance) {
      return;
    }
    Direction dir = ThreadLocalRandom.current().nextBoolean() ? Direction.LEFT : Direction.RIGHT;
    float startYaw = player.getLocation().getYaw();
    long expiry = System.currentTimeMillis() + durationMs;
    states.put(player.getUniqueId(), new State(dir, startYaw, expiry));
    String bar = dir == Direction.LEFT ? "<<<<<<" : ">>>>>>";
    String msg = dir == Direction.LEFT ? "Click left" : "Click right";
    player.showTitle(Title.title(Component.text(bar), Component.text(msg)));
  }

  /** Handle a left-click to validate the required direction. */
  public void handleClick(Player player, float currentYaw) {
    State st = states.get(player.getUniqueId());
    if (st == null) return;
    long now = System.currentTimeMillis();
    if (now > st.expiry) {
      states.remove(player.getUniqueId());
      return;
    }
    float diff = currentYaw - st.startYaw;
    diff = (diff + 540) % 360 - 180; // normalize to [-180,180]
    if (st.required == Direction.LEFT && diff <= -yawThreshold) {
      st.success = true;
    } else if (st.required == Direction.RIGHT && diff >= yawThreshold) {
      st.success = true;
    } else {
      st.success = false;
    }
  }

  /** Mark current QTE as failed due to player movement. */
  public void fail(Player player) {
    State st = states.get(player.getUniqueId());
    if (st != null) {
      st.success = false;
    }
  }

  /** Consume the QTE result when reeling in. */
  public boolean consume(Player player) {
    State st = states.remove(player.getUniqueId());
    if (st == null) return true; // no QTE triggered
    return st.success && System.currentTimeMillis() <= st.expiry;
  }
}
