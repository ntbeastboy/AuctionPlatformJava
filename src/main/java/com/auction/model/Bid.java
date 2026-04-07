package AuctionPlatformJava.src.main.java.com.auction.model;

public class Bid {
    private final String bidderId;
    private final String itemId;
    private final double amount;
    private final long timestamp;

    public Bid(String bidderId, String itemId, double amount) {
        this.bidderId = bidderId;
        this.itemId = itemId;
        this.amount = amount;
        this.timestamp = System.currentTimeMillis() / 1000L;
    }

    public String getBidderId() { return bidderId; }

    public String getItemId() { return itemId; }

    public double getAmount() { return amount; }

    public long getTimestamp() { return timestamp; }
}
