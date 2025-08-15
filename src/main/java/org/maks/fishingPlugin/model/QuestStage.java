package org.maks.fishingPlugin.model;

/**
 * Definition of a single quest stage.
 *
 * <p>The stage now contains additional metadata such as title and lore as well
 * as flexible goal and reward types. Rewards can be money, commands or items
 * serialized as base64.</p>
 */
public record QuestStage(
    int stage,
    String title,
    String lore,
    GoalType goalType,
    int goal,
    RewardType rewardType,
    double reward,
    String rewardData) {

  /** Types of goals that a quest stage can have. */
  public enum GoalType {
    /** Catch a certain number of fish. */
    CATCH
  }

  /** Types of rewards that can be granted for a quest stage. */
  public enum RewardType {
    /** Money reward deposited to the player's balance. */
    MONEY,
    /** A command executed as console with %player% placeholder. */
    COMMAND,
    /** An item serialized as Base64 and given to the player. */
    ITEM
  }
}

