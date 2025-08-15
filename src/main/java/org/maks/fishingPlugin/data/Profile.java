package org.maks.fishingPlugin.data;

import java.util.UUID;

/** Player profile persisted in the database. */
public record Profile(
    UUID playerUuid,
    int rodLevel,
    long rodXp,
    long totalCatches,
    long totalWeightG,
    long largestCatchG) {}

