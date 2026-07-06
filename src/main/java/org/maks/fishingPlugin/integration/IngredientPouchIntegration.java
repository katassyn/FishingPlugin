package org.maks.fishingPlugin.integration;

import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reflective bridge to IngredientPouchPlugin.
 * Allows the fishing plugin to send crafting drops straight into the pouch.
 */
public final class IngredientPouchIntegration {

  private static final String POUCH_PLUGIN_NAME = "IngredientPouchPlugin";
  private static final String POUCH_PLUGIN_CLASS =
      "com.maks.ingredientpouchplugin.IngredientPouchPlugin";
  private static final String POUCH_API_CLASS = "com.maks.ingredientpouchplugin.api.PouchAPI";
  private static final String POUCH_MAPPINGS_CLASS =
      "com.maks.mycraftingplugin2.integration.PouchItemMappings";

  private static Object pouchApi;
  private static Method updateItemQuantity;
  private static Method updatePouchGui;
  private static Method resolvePouchItemId;
  private static boolean ready;
  private static Logger logger;

  private IngredientPouchIntegration() {
  }

  /**
   * Attempt to hook into IngredientPouchPlugin. This uses reflection so the dependency stays
   * optional at compile time.
   *
   * @param plugin owning plugin (used for logging).
   */
  public static void initialize(JavaPlugin plugin) {
    logger = plugin.getLogger();
    if (ready) {
      return;
    }

    try {
      Plugin pouchPlugin = Bukkit.getPluginManager().getPlugin(POUCH_PLUGIN_NAME);
      if (pouchPlugin == null) {
        logger.info("IngredientPouchPlugin not found. Craft drops will go to inventory.");
        return;
      }
      Class<?> pluginClass = Class.forName(POUCH_PLUGIN_CLASS);
      if (!pluginClass.isInstance(pouchPlugin)) {
        logger.warning("IngredientPouchPlugin detected but class mismatch. Skipping integration.");
        return;
      }

      Method getApi = pluginClass.getMethod("getAPI");
      pouchApi = getApi.invoke(pouchPlugin);

      Class<?> apiClass = Class.forName(POUCH_API_CLASS);
      updateItemQuantity = apiClass.getMethod(
          "updateItemQuantity", String.class, String.class, int.class);
      updatePouchGui = apiClass.getMethod("updatePouchGUI", Player.class, String.class);

      Class<?> mappingsClass = Class.forName(POUCH_MAPPINGS_CLASS);
      resolvePouchItemId = mappingsClass.getMethod("getPouchItemId", String.class);

      ready = pouchApi != null && updateItemQuantity != null && resolvePouchItemId != null;
      if (ready) {
        logger.info("IngredientPouch integration enabled.");
      } else {
        logger.warning("IngredientPouch integration failed: API methods missing.");
      }
    } catch (Exception ex) {
      logWarning("Failed to initialize IngredientPouch integration: " + ex.getMessage());
      pouchApi = null;
      updateItemQuantity = null;
      updatePouchGui = null;
      resolvePouchItemId = null;
      ready = false;
    }
  }

  /**
   * @return true when the bridge is ready to use.
   */
  public static boolean isReady() {
    return ready;
  }

  /**
   * Try to push the supplied item into the player's ingredient pouch.
   *
   * @param player recipient player.
   * @param item   crafted item to store.
   * @return true when the item was accepted by the pouch.
   */
  public static boolean tryStore(Player player, ItemStack item) {
    if (!ready || player == null || item == null || item.getAmount() <= 0) {
      return false;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null || !meta.hasDisplayName()) {
      return false;
    }

    try {
      String displayName = meta.getDisplayName();
      String pouchId = (String) resolvePouchItemId.invoke(null, displayName);
      if (pouchId == null || pouchId.isEmpty()) {
        return false;
      }

      Object result = updateItemQuantity.invoke(
          pouchApi, player.getUniqueId().toString(), pouchId, item.getAmount());
      boolean success = result instanceof Boolean && (Boolean) result;
      if (success && updatePouchGui != null) {
        updatePouchGui.invoke(pouchApi, player, pouchId);
      }
      return success;
    } catch (Exception ex) {
      logWarning("Failed to store item in IngredientPouch: " + ex.getMessage());
      return false;
    }
  }

  private static void logWarning(String message) {
    if (logger != null) {
      logger.warning(message);
    } else {
      Bukkit.getLogger().warning(message);
    }
  }
}

