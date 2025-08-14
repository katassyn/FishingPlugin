package org.maks.fishingPlugin.model;

import java.util.UUID;

/** Player's quest chain progress. */
public record QuestProgress(UUID playerUuid, int stage, int count) {}
