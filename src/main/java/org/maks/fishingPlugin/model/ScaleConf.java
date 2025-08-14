package org.maks.fishingPlugin.model;

/**
 * Configuration for weight scaling formulas.
 * <p>
 * EXP: multiplier = exp(a * level)
 * POLY: multiplier = (1 + a * level)^k
 * </p>
 *
 * @param mode scaling mode
 * @param a    parameter (beta for EXP, alpha for POLY)
 * @param k    exponent for POLY, unused for EXP
 */
public record ScaleConf(ScaleMode mode, double a, double k) {

  /**
   * Computes the multiplier for a given rod level.
   */
  public double mult(int level) {
    return switch (mode) {
      case EXP -> Math.exp(a * level);
      case POLY -> Math.pow(1.0 + a * level, k);
    };
  }
}
