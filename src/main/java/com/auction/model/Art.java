package AuctionPlatformJava.src.main.java.com.auction.model;

public class Art extends Item{
    private String Artist;
    public Art(String id, String name, String artist) {
        super(id, name);
        this.Artist = artist;
    }
    public String getArtist() { return Artist; }
    public void setArtist(String artist) {this.Artist = artist; }

    @Override
    public String printInfo() {
        return this.getId() + " " + this.getName()  + " " + this.getArtist();
    }
}
