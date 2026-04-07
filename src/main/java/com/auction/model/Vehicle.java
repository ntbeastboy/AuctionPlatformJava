package com.auction.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Vehicle extends Item {
    private int miles;
    private LocalDate manufacturingDate;
    private String brand;
    private String vin;
    private boolean accidentHistory;

    public Vehicle(String id, String name, String description, double startingPrice, double priceStep, LocalDateTime bidStartTime, LocalDateTime bidEndTime,
                   String sellerId, int miles, LocalDate manufacturingDate, String brand, String vin, boolean accidentHistory) {
        super(id, name, description, startingPrice, priceStep, bidStartTime, bidEndTime, sellerId);
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
