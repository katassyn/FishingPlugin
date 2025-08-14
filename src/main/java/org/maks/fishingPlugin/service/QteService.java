package org.maks.fishingPlugin.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Simple one-click QTE anti-autofish mechanic.
 */
public class QteService {

  public enum ClickType {LEFT, RIGHT}

  private static class State {
    final ClickType required;
    final long expiry;
    boolean success;
    State(ClickType required, long expiry) {
      this.required = required;
      this.expiry = expiry;
    }
  }

  private final Map<UUID, State> states = new ConcurrentHashMap<>();
  private final AntiCheatService antiCheat;

  public QteService(AntiCheatService antiCheat) {
    this.antiCheat = antiCheat;
  }

  /** Start a QTE after a bite. */
  public void start(Player player) {
    ClickType req = ThreadLocalRandom.current().nextBoolean() ? ClickType.LEFT : ClickType.RIGHT;
    long expiry = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(600, 1201);
    states.put(player.getUniqueId(), new State(req, expiry));
    String msg = req == ClickType.LEFT ? "Kliknij LPM!" : "Kliknij PPM!";
    player.sendActionBar(Component.text(msg));
  }

  /** Handle a player click during the QTE window. */
  public void handleClick(Player player, ClickType click) {
    long now = System.currentTimeMillis();
    if (antiCheat.record(player.getUniqueId(), now)) {
      states.remove(player.getUniqueId());
      player.sendMessage("Wykryto makro!");
      return;
    }
    State st = states.get(player.getUniqueId());
    if (st == null) return;
    if (now > st.expiry) {
      states.remove(player.getUniqueId());
      player.sendMessage("Za późno!");
      return;
    }
    if (click == st.required) {
      st.success = true; // keep state until consume
    } else {
      states.remove(player.getUniqueId());
      player.sendMessage("Zły przycisk!");
    }
  }

  /** Consume the QTE result when the player attempts to reel in. */
  public boolean consume(Player player) {
    State st = states.remove(player.getUniqueId());
    if (st == null) return false;
    long now = System.currentTimeMillis();
    return st.success && now <= st.expiry;
  }
}
