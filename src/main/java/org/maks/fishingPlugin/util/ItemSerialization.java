package org.maks.fishingPlugin.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * Utility for serializing ItemStacks to Base64 and back.
 */
public final class ItemSerialization {
  private ItemSerialization() {}

  /**
   * Serializes an ItemStack into a Base64 string.
   *
   * @param item the item to serialize
   * @return base64 representation
   */
  public static String toBase64(ItemStack item) {
    try (var baos = new ByteArrayOutputStream();
         var oos = new BukkitObjectOutputStream(baos)) {
      oos.writeObject(item);
      return Base64.getEncoder().encodeToString(baos.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException("Could not serialize item", e);
    }
  }

  /**
   * Serializes an array of ItemStacks into a Base64 string.
   *
   * @param items the items to serialize
   * @return base64 representation
   */
  public static String toBase64(ItemStack[] items) {
    try (var baos = new ByteArrayOutputStream();
         var oos = new BukkitObjectOutputStream(baos)) {
      oos.writeInt(items.length);
      for (ItemStack item : items) {
        oos.writeObject(item);
      }
      return Base64.getEncoder().encodeToString(baos.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException("Could not serialize items", e);
    }
  }

  /**
   * Deserializes an ItemStack from its Base64 representation.
   *
   * @param b64 base64 string
   * @return deserialized item
   */
  public static ItemStack fromBase64(String b64) {
    byte[] data = Base64.getDecoder().decode(b64);
    try (var bais = new ByteArrayInputStream(data);
         var ois = new BukkitObjectInputStream(bais)) {
      return (ItemStack) ois.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException("Could not deserialize item", e);
    }
  }

  /**
   * Deserializes an array of ItemStacks from its Base64 representation.
   * Falls back to a single item if older data is encountered.
   *
   * @param b64 base64 string
   * @return array of deserialized items
   */
  public static ItemStack[] fromBase64List(String b64) {
    if (b64 == null || b64.isEmpty()) {
      return new ItemStack[0];
    }
    byte[] data = Base64.getDecoder().decode(b64);
    try (var bais = new ByteArrayInputStream(data);
         var ois = new BukkitObjectInputStream(bais)) {
      int len = ois.readInt();
      ItemStack[] items = new ItemStack[len];
      for (int i = 0; i < len; i++) {
        items[i] = (ItemStack) ois.readObject();
      }
      return items;
    } catch (IOException | ClassNotFoundException e) {
      // Fall back to single item format
      return new ItemStack[] { fromBase64(b64) };
    }
  }
}
