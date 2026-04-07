package AuctionPlatformJava.src.main.java.com.auction.model;

public class Bidder extends BannableUser {
    private double balance;

    public Bidder(String id, String username, String password) {
        super(id, username, password);
        this.balance = 0.0;
    }

    public double getBalance() { return balance; }

    public void addFunds(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive.");
        balance += amount;
    }
}
