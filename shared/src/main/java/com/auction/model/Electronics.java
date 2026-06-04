package com.auction.model;

import java.time.LocalDateTime;

public class Electronics extends Item {
  private int wattage;
  private String origin;
  private int warrantyMonths;
  private String serialNumber;

  public Electronics(
      String id,
      String name,
      String description,
      double startingPrice,
      double priceStep,
      LocalDateTime bidStartTime,
      LocalDateTime bidEndTime,
      String sellerId,
      int wattage,
      String origin,
      int warrantyMonths,
      String serialNumber) {
    super(id, name, description, startingPrice, priceStep, bidStartTime, bidEndTime, sellerId);
    this.wattage = wattage;
    this.origin = origin;
    this.warrantyMonths = warrantyMonths;
    this.serialNumber = serialNumber;
  }

  public int getWattage() {
    return wattage;
  }

  public void setWattage(int wattage) {
    this.wattage = wattage;
  }

  public String getOrigin() {
    return origin;
  }

  public void setOrigin(String origin) {
    this.origin = origin;
  }

  public int getWarrantyMonths() {
    return warrantyMonths;
  }

  public void setWarrantyMonths(int warrantyMonths) {
    this.warrantyMonths = warrantyMonths;
  }

  public String getSerialNumber() {
    return serialNumber;
  }

  public void setSerialNumber(String serialNumber) {
    this.serialNumber = serialNumber;
  }

  @Override
  public String getTypeName() {
    return "Electronics";
  }
}
