package com.auction.dto;

public class UserRemoteDto {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private Double balance;
    private Boolean banned;
    private String token;

    public UserRemoteDto() {}
    
    public UserRemoteDto(String id, String username, String email, String fullName, String role, Double balance, Boolean banned) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.balance = balance;
        this.banned = banned;
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }
    public Boolean getBanned() { return banned; }
    public void setBanned(Boolean banned) { this.banned = banned; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
