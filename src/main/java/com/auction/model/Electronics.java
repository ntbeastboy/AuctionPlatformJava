package AuctionPlatformJava.src.main.java.com.auction.model;

public class Electronics extends Item{
    private int warrantyMonths;
    public Electronics(String id, String name, int warrantyMonths) {
        super(id, name);
        this.warrantyMonths = warrantyMonths;
    }
    public int getwarrantyMonths() { return warrantyMonths; }
    public void setTimeWarrior(int warrantyMonths) { this.warrantyMonths = warrantyMonths;}

    @Override
    public String printInfo() {
        return this.getId() + " "  + this.getName() + " " + this.getwarrantyMonths();
    }
}
