package AuctionPlatformJava.src.main.java.com.auction.model;

import java.time.LocalDateTime;

public class Electronics extends Item {
    private int wattage;
    private String origin;
    private int warrantyMonths;
    private String serialNumber;

    public Electronics(String id, String name, String description, double startingPrice, LocalDateTime bidStartTime, LocalDateTime bidEndTime,
                       int wattage, String origin, int warrantyMonths, String serialNumber) {
        super(id, name, description, startingPrice, bidStartTime, bidEndTime);
        this.wattage = wattage;
        this.origin = origin;
        this.warrantyMonths = warrantyMonths;
        this.serialNumber = serialNumber;
    }

    public int getWattage() { return wattage; }

    public void setWattage(int wattage) { this.wattage = wattage; }

    public String getOrigin() { return origin; }

    public void setOrigin(String origin) { this.origin = origin; }

    public int getWarrantyMonths() { return warrantyMonths; }

    public void setWarrantyMonths(int warrantyMonths) { this.warrantyMonths = warrantyMonths; }

    public String getSerialNumber() { return serialNumber; }

    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
}
