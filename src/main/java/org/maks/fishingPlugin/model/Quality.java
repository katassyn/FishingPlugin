package org.maks.fishingPlugin.model;

/**
 * Quality grades for caught fish.
 */
public enum Quality {
    S(1.5),
    A(1.2),
    B(1.0),
    C(0.8);

    private final double priceMultiplier;

    Quality(double priceMultiplier) {
        this.priceMultiplier = priceMultiplier;
    }

    /** Price multiplier applied during quick sell calculations. */
    public double priceMultiplier() {
        return priceMultiplier;
    }
}
