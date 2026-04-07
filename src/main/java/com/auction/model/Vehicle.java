package AuctionPlatformJava.src.main.java.com.auction.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Vehicle extends Item {
    private int miles;
    private LocalDate manufacturingDate;
    private String brand;
    private String vin;
    private boolean accidentHistory;

    public Vehicle(String id, String name, String description, double startingPrice, LocalDateTime bidStartTime, LocalDateTime bidEndTime,
                   int miles, LocalDate manufacturingDate, String brand, String vin, boolean accidentHistory) {
        super(id, name, description, startingPrice, bidStartTime, bidEndTime);
        this.miles = miles;
        this.manufacturingDate = manufacturingDate;
        this.brand = brand;
        this.vin = vin;
        this.accidentHistory = accidentHistory;
    }

    public int getMiles() { return miles; }

    public void setMiles(int miles) { this.miles = miles; }

    public LocalDate getManufacturingDate() { return manufacturingDate; }

    public void setManufacturingDate(LocalDate manufacturingDate) { this.manufacturingDate = manufacturingDate; }

    public String getBrand() { return brand; }

    public void setBrand(String brand) { this.brand = brand; }

    public String getVin() { return vin; }

    public void setVin(String vin) { this.vin = vin; }

    public boolean hasAccidentHistory() { return accidentHistory; }

    public void setAccidentHistory(boolean accidentHistory) { this.accidentHistory = accidentHistory; }
}
