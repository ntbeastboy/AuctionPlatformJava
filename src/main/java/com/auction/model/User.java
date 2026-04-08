package com.auction.model;

public abstract class User implements Entity {
    private final String id;
    private final String username;
    private String password;

    public User(String id, String username, String password) {
        this.id = id;
        this.username = username;
        this.password = password;
    }

    @Override
    public String getId() { return id; }

    public String getUsername() { return username; }

    public String getPassword() { return password; }

    public void setPassword(String password) { this.password = password; }
}
