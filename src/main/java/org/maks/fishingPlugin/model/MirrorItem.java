package org.maks.fishingPlugin.model;

/**
 * Definition of a non-fish loot item stored separately from loot entries.
 * The item is stored as a Base64 encoded ItemStack.
 */
public record MirrorItem(
    String key,
    Category category,
    boolean broadcast,
    String itemBase64
) {}
