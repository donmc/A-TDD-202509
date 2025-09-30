package com.improving.tddair;

public enum Tier {
    GREEN(25000), BLUE(50000), RED(0);

    private int milesThreshold;

    Tier(int milesthreshold) {
        this.milesThreshold = milesthreshold;
    }

    public static Tier getCorrespondingTierFor(int ytdMiles) {
        if (ytdMiles >= BLUE.milesThreshold) {
            return BLUE;
        } else if (ytdMiles >= GREEN.milesThreshold) {
            return GREEN;
        } else {
            return RED;
        }
    }
}
