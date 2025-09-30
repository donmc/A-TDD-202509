package com.improving.tddair;

public enum Tier {
    GREEN(25000), BLUE(50000), RED(0), GOLD(75000);

    private int milesThreshold;

    Tier(int milesThreshold) {
        this.milesThreshold = milesThreshold;
    }

    public static Tier getCorrespondingTierFor(int ytdMiles) {
        if (ytdMiles >= GOLD.milesThreshold) {
            return GOLD;
        } else if (ytdMiles >= BLUE.milesThreshold) {
            return BLUE;
        } else if (ytdMiles >= GREEN.milesThreshold) {
            return GREEN;
        } else {
            return RED;
        }
    }
}
