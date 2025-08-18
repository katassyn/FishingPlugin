package org.maks.fishingPlugin.model;

import org.maks.fishingPlugin.service.TreasureMapService;

/** Definition of bounty reward for a lair. */
public record BountyReward(TreasureMapService.Lair lair, String rewardData) {}
