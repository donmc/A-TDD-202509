package com.improving.tddair;

public class Member {
    private String email;
    private String username;
    private int ytdMiles;
    private int balanceMiles;
    private Tier tier;

    public Member(String username, String email) {
        this.username = username;
        this.email = email;
        this.tier = Tier.RED;
        this.balanceMiles = 10000;
    }

    public String getUsername() {
        return this.username;
    }

    public String getEmail() {
        return email;
    }

    public Tier getTier() {
        return tier;
    }

    public int getYtdMiles() {
        return ytdMiles;
    }

    public int getBalanceMiles() {
        return this.balanceMiles;
    }

    public void completeFlight(Flight flight) {
        this.ytdMiles += flight.getMileage();
        this.balanceMiles += flight.getMileage();
        this.tier = Tier.getCorrespondingTierFor(ytdMiles);
    }
}
