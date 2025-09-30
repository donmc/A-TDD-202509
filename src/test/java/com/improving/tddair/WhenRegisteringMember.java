package com.improving.tddair;

import io.cucumber.java.Before;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class WhenRegisteringMember {

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
    public void shouldRegisterMember() {
        assertNotNull(member);
    }

    @Test
    public void shouldHaveCorrectUsername() {
        assertEquals(username, member.getUsername());
    }

    @Test
    public void shouldHaveCorrectEmail() {
        assertEquals(email, member.getEmail());
    }

    @Test
    public void shouldNotHaveDuplicateUsername() {
        Assertions.assertThrows(DuplicateUsernameException.class, () -> {
            app.registerMember(username, email);
        });
    }

    @Test
    void shouldHaveRedStatus() {
        Tier expectedTier = Tier.RED;
        assertEquals(expectedTier, member.getTier());
    }

    @Test
    void shouldHave0YtdMiles() {
        assertEquals(0, member.getYtdMiles());
    }

    @Test
    void shouldHave10000BonusBalanceMiles() {
        assertEquals(10000, member.getBalanceMiles());
    }

}
