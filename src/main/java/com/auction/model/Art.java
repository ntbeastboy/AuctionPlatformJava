package AuctionPlatformJava.src.main.java.com.auction.model;

import java.time.LocalDateTime;

public class Art extends Item {
    private String artist;
    private String paintingStyle;
    private String origin;

    public Art(String id, String name, String description, double startingPrice, LocalDateTime bidStartTime, LocalDateTime bidEndTime,
               String artist, String paintingStyle, String origin) {
        super(id, name, description, startingPrice, bidStartTime, bidEndTime);
        this.artist = artist;
        this.paintingStyle = paintingStyle;
        this.origin = origin;
    }

    public String getArtist() { return artist; }

    public void setArtist(String artist) { this.artist = artist; }

    public String getPaintingStyle() { return paintingStyle; }

    public void setPaintingStyle(String paintingStyle) { this.paintingStyle = paintingStyle; }

    public String getOrigin() { return origin; }

    public void setOrigin(String origin) { this.origin = origin; }
}
