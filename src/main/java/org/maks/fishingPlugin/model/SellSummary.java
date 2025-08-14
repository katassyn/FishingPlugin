package org.maks.fishingPlugin.model;

import java.util.List;

/** Summary of potential sale grouped by species and quality. */
public record SellSummary(List<Entry> entries, double totalPrice) {
    public record Entry(String key, Quality quality, int amount, double price) {}
}
