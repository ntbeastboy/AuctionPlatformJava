package AuctionPlatformJava.src.main.java.com.auction.model;

public abstract class Item implements Entity{
    private String Id, name;

    public Item(String id, String name){
        this.Id = id;
        this.name = name;
    }
    public String getId() { return Id; }
    public void setId(String id) { Id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public abstract String printInfo();
}
