package org.maks.fishingPlugin.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Configurable multi-click QTE anti-autofish mechanic.
 */
public class QteService {

  public enum ClickType {LEFT, RIGHT}

  private static class State {
    ClickType required;
    long start;
    long expiry;
    int remaining;
    boolean success;

    State(ClickType required, long start, long expiry, int remaining) {
      this.required = required;
      this.start = start;
      this.expiry = expiry;
      this.remaining = remaining;
    }
  }

  private final Map<UUID, State> states = new ConcurrentHashMap<>();
  private final AntiCheatService antiCheat;
  private final JavaPlugin plugin;
  private final int clicks;
  private final long startDelayMin;
  private final long startDelayMax;
  private final long windowMin;
  private final long windowMax;
  private final MacroAction macroAction;

  public enum MacroAction {CANCEL, REDUCE}

  public QteService(JavaPlugin plugin, AntiCheatService antiCheat, int clicks,
      long startDelayMin, long startDelayMax, long windowMin, long windowMax,
      MacroAction macroAction) {
    this.plugin = plugin;
    this.antiCheat = antiCheat;
    this.clicks = clicks;
    this.startDelayMin = startDelayMin;
    this.startDelayMax = startDelayMax;
    this.windowMin = windowMin;
    this.windowMax = windowMax;
    this.macroAction = macroAction;
  }

  /** Start a QTE after a bite. */
  public void start(Player player) {
    long delay = ThreadLocalRandom.current().nextLong(startDelayMin, startDelayMax + 1);
    long window = ThreadLocalRandom.current().nextLong(windowMin, windowMax + 1);
    ClickType req = ThreadLocalRandom.current().nextBoolean() ? ClickType.LEFT : ClickType.RIGHT;
    long start = System.currentTimeMillis() + delay;
    long expiry = start + window;
    states.put(player.getUniqueId(), new State(req, start, expiry, clicks));
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      String msg = req == ClickType.LEFT ? "Click left mouse button!" : "Click right mouse button!";
      player.sendActionBar(Component.text(msg));
    }, delay / 50L);
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
    if (now < st.start) return;
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
        long window = ThreadLocalRandom.current().nextLong(windowMin, windowMax + 1);
        st.start = now;
        st.expiry = now + window;
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
