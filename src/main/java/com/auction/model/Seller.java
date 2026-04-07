package com.auction.model;

public class Seller extends BannableUser {
    private double balance;

    public Seller(String id, String username, String password) {
        super(id, username, password);
        this.balance = 0.0;
    }

    public double getBalance() { return balance; }

    public void addFunds(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive.");
        balance += amount;
    }

    public void withdraw(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive.");
        if (amount > balance) throw new IllegalArgumentException("Insufficient balance.");
        balance -= amount;
    }
}
