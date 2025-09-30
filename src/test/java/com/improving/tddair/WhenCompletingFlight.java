package com.improving.tddair;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WhenCompletingFlight {

    private TddAirApplication app;
    private Member member;

    @BeforeEach
    void given() {
        app = new TddAirApplication();
        app.registerMember("donmc", "don@improving.com");

         member = app.lookupMember("donmc");
    }

    @Test
    void shouldIncreaseYtdMiles() {

        app.completeFlight("donmc", "AF38");

        assertEquals(3620, member.getYtdMiles());
    }

    @Test
    void shouldAppendYtdMilesWithMultipleFlights() {
        app.completeFlight("donmc", "AF38");
        app.completeFlight("donmc", "AF38");

        assertEquals(7240, member.getYtdMiles());
    }

    @Test
    void shouldIncreaseBalanceMiles() {
        app.completeFlight("donmc", "AF38");
        assertEquals(13620, member.getBalanceMiles());

    }

    @Test
    void shouldChangeTierFromRedToGreen() {
        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");

        assertEquals(Tier.GREEN, member.getTier());
    }

    @Test
    void shouldChangeTierFromGreenToBlue() {
        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");

        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");

        assertEquals(Tier.BLUE, member.getTier());
    }

    @Test
    void shouldChangeTierFromBlueToGold() {
        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");

        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");

        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");
        app.completeFlight("donmc", "QF191");

        assertEquals(Tier.GOLD, member.getTier());
    }
}
