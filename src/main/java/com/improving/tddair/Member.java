package com.improving.tddair;

public class Member {
    private String email;
    private String username;

    public Member(String username, String email) {
        this.username = username;
        this.email = email;
    }

    public String getUsername() {
        return this.username;
    }

    public String getEmail() {
        return email;
    }

    public Tier getTier() {
        return Tier.RED;
    }
}
