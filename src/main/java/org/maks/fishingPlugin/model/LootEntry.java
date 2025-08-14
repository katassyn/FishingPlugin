package org.maks.fishingPlugin.model;

/**
 * Definition of a loot item stored in cache/DB.
 * This is a simplified model used for the initial implementation.
 */
public record LootEntry(
    String key,
    Category category,
    double baseWeight,
    int minRodLevel,
    boolean broadcast,
    double priceBase,
    double pricePerKg,
    double payoutMultiplier,
    double qualitySWeight,
    double qualityAWeight,
    double qualityBWeight,
    double qualityCWeight,
    double minWeightG,
    double maxWeightG,
    String itemBase64
) {}
