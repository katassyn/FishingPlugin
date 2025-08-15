package org.maks.fishingPlugin.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Configurable multi-click QTE anti-autofish mechanic.
 */
public class QteService {

  public enum ClickType {LEFT, RIGHT}

  private static class State {
    ClickType required;
    long expiry;
    int remaining;
    boolean success;

    State(ClickType required, long expiry, int remaining) {
      this.required = required;
      this.expiry = expiry;
      this.remaining = remaining;
    }
  }

  private final Map<UUID, State> states = new ConcurrentHashMap<>();
  private final AntiCheatService antiCheat;
  private final int clicks;
  private final long windowMs;
  private final MacroAction macroAction;

  public enum MacroAction {CANCEL, REDUCE}

  public QteService(AntiCheatService antiCheat, int clicks, long windowMs,
      MacroAction macroAction) {
    this.antiCheat = antiCheat;
    this.clicks = clicks;
    this.windowMs = windowMs;
    this.macroAction = macroAction;
  }

  /** Start a QTE after a bite. */
  public void start(Player player) {
    ClickType req = ThreadLocalRandom.current().nextBoolean() ? ClickType.LEFT : ClickType.RIGHT;
    long expiry = System.currentTimeMillis() + windowMs;
    states.put(player.getUniqueId(), new State(req, expiry, clicks));
    String msg = req == ClickType.LEFT ? "Click left mouse button!" : "Click right mouse button!";
    player.sendActionBar(Component.text(msg));
  }

  /** Handle a player click during the QTE window. */
  public void handleClick(Player player, ClickType click) {
    long now = System.currentTimeMillis();
    if (antiCheat.record(player.getUniqueId(), now)) {
      if (macroAction == MacroAction.CANCEL) {
        states.remove(player.getUniqueId());
        player.sendMessage("Macro detected!");
        return;
      } else {
        antiCheat.flag(player.getUniqueId());
        player.sendMessage("Macro detected!");
      }
    }
    State st = states.get(player.getUniqueId());
    if (st == null) return;
    if (now > st.expiry) {
      states.remove(player.getUniqueId());
      player.sendMessage("Too late!");
      return;
    }
    if (st.success) return;
    if (click == st.required) {
      st.remaining--;
      if (st.remaining <= 0) {
        st.success = true; // keep state until consume
      } else {
        st.required = ThreadLocalRandom.current().nextBoolean() ? ClickType.LEFT : ClickType.RIGHT;
        st.expiry = now + windowMs;
        String msg = st.required == ClickType.LEFT ? "Click left mouse button!" : "Click right mouse button!";
        player.sendActionBar(Component.text(msg));
      }
    } else {
      states.remove(player.getUniqueId());
      player.sendMessage("Wrong button!");
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
