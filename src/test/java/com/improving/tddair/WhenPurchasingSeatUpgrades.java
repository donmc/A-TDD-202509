package com.improving.tddair;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class WhenPurchasingSeatUpgrades {


    private Member member;
    private String username;
    private String email;
    private TddAirApplication app;

    @BeforeEach
    public void given() {
        app = new TddAirApplication();
        username = "abc";
        email = "abc@abc.com";

        app.registerMember(username, email);

        member = app.lookupMember(username);
    }

    @Test
    void shouldIncreaseNumberOfSeats_UsingMiles() {
        int quantity = 1;
        member.purchaseSeatUpgradeUsingMiles(quantity);

        Assertions.assertEquals(1, member.getSeatUpgrades());
    }

    @Test
    void shouldDecreaseBalanceMiles_UsingMiles() {
        int quantity = 1;
        member.purchaseSeatUpgradeUsingMiles(quantity);

        Assertions.assertEquals(0, member.getBalanceMiles());
    }

    @Test
    void shouldIncreaseNumberOfSeats_UsingCC() {
        int quantity = 1;
        String ccNumber = "9999999999999999";
        SpyCas spyCas = new SpyCas();
        member.setCas(spyCas);
        member.purchaseSeatUpgradeUsingCC(quantity, ccNumber);

        Assertions.assertEquals(1, member.getSeatUpgrades());
        Assertions.assertEquals(100, spyCas.getAmountCharged());
    }
}
