package com.auction.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public interface ItemFactory {
  Item create(
      String type,
      String id,
      String name,
      String description,
      double startingPrice,
      double priceStep,
      LocalDateTime bidStartTime,
      LocalDateTime bidEndTime,
      String sellerId,
      Map<String, Object> attributes);

  static ItemFactory defaultFactory() {
    return DefaultItemFactory.INSTANCE;
  }

  final class DefaultItemFactory implements ItemFactory {
    private static final DefaultItemFactory INSTANCE = new DefaultItemFactory();

    private DefaultItemFactory() {}

    @Override
    public Item create(
        String type,
        String id,
        String name,
        String description,
        double startingPrice,
        double priceStep,
        LocalDateTime bidStartTime,
        LocalDateTime bidEndTime,
        String sellerId,
        Map<String, Object> attributes) {
      Map<String, Object> attrs = attributes == null ? Map.of() : attributes;
      Item item =
          switch (type == null ? "Other" : type) {
            case "Art" ->
                new Art(
                    id,
                    name,
                    description,
                    startingPrice,
                    priceStep,
                    bidStartTime,
                    bidEndTime,
                    sellerId,
                    str(attrs, "artist"),
                    str(attrs, "paintingStyle"),
                    str(attrs, "origin"));
            case "Electronics" ->
                new Electronics(
                    id,
                    name,
                    description,
                    startingPrice,
                    priceStep,
                    bidStartTime,
                    bidEndTime,
                    sellerId,
                    integer(attrs, "wattage"),
                    str(attrs, "origin"),
                    integer(attrs, "warrantyMonths"),
                    str(attrs, "serialNumber"));
            case "Vehicle" ->
                new Vehicle(
                    id,
                    name,
                    description,
                    startingPrice,
                    priceStep,
                    bidStartTime,
                    bidEndTime,
                    sellerId,
                    integer(attrs, "miles"),
                    date(attrs, "manufacturingDate"),
                    str(attrs, "brand"),
                    str(attrs, "vin"),
                    bool(attrs, "accidentHistory"));
            case "AuctionItem" ->
                new AuctionItem(
                    id,
                    name,
                    description,
                    startingPrice,
                    priceStep,
                    bidStartTime,
                    bidEndTime,
                    sellerId,
                    str(attrs, "category"),
                    str(attrs, "condition"));
            default ->
                new Other(
                    id,
                    name,
                    description,
                    startingPrice,
                    priceStep,
                    bidStartTime,
                    bidEndTime,
                    sellerId);
          };
      item.setImageDataList(stringList(attrs, "imageDataList"));
      if (item.getImageDataList().isEmpty()) item.setImageData(str(attrs, "imageData"));
      return item;
    }

    private static String str(Map<String, Object> attrs, String key) {
      Object value = attrs.get(key);
      return value == null ? null : value.toString();
    }

    private static java.util.List<String> stringList(Map<String, Object> attrs, String key) {
      Object value = attrs.get(key);
      if (value instanceof java.util.List<?> list) {
        return list.stream()
            .filter(v -> v != null && !v.toString().isBlank())
            .map(Object::toString)
            .toList();
      }
      return java.util.List.of();
    }

    private static int integer(Map<String, Object> attrs, String key) {
      Object value = attrs.get(key);
      if (value instanceof Number n) return n.intValue();
      if (value == null || value.toString().isBlank()) return 0;
      return Integer.parseInt(value.toString());
    }

    private static boolean bool(Map<String, Object> attrs, String key) {
      Object value = attrs.get(key);
      if (value instanceof Boolean b) return b;
      return value != null && Boolean.parseBoolean(value.toString());
    }

    private static LocalDate date(Map<String, Object> attrs, String key) {
      String value = str(attrs, key);
      return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }
  }
}
