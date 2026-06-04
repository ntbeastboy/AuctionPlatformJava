package com.auction.repository;

import com.auction.exception.StaleObjectException;
import com.auction.model.Art;
import com.auction.model.AuctionItem;
import com.auction.model.AuctionStatus;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Other;
import com.auction.model.Vehicle;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteItemRepository implements ItemRepository {

  private final Connection conn;

  public SqliteItemRepository(DatabaseManager dbManager) {
    this.conn = dbManager.getConnection();
  }

  @Override
  public void save(Item item) {
    String sql =
        """
            INSERT OR REPLACE INTO items (
                id, name, description, starting_price, current_price, price_step,
                bid_start_time, bid_end_time, seller_id, status, current_winner_id,
                item_type, category, item_condition, artist, painting_style, origin,
                wattage, warranty_months, serial_number, miles, manufacturing_date,
                brand, vin, accident_history, image_data, version
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      setItemParams(ps, item);
      ps.setLong(27, item.getVersion());
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to save item: " + e.getMessage(), e);
    }
  }

  @Override
  public Optional<Item> findById(String id) {
    String sql = "SELECT * FROM items WHERE id = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, id);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) return Optional.of(mapRow(rs));
      return Optional.empty();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find item: " + e.getMessage(), e);
    }
  }

  @Override
  public void delete(String id) {
    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM items WHERE id = ?")) {
      ps.setString(1, id);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to delete item: " + e.getMessage(), e);
    }
  }

  @Override
  public List<Item> findAll() {
    List<Item> result = new ArrayList<>();
    try (Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM items")) {
      while (rs.next()) result.add(mapRow(rs));
      return result;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find all items: " + e.getMessage(), e);
    }
  }

  @Override
  public void update(Item item) {
    // Optimistic concurrency control: only update if the on-disk version
    // matches the in-memory version we read. Increment version on success.
    // This guards against lost updates and rolled-back prices even across
    // multiple JVMs sharing the same database file.
    String sql =
        """
            UPDATE items SET
                name = ?, description = ?, starting_price = ?, current_price = ?,
                price_step = ?, bid_start_time = ?, bid_end_time = ?, seller_id = ?,
                status = ?, current_winner_id = ?, item_type = ?, category = ?,
                item_condition = ?, artist = ?, painting_style = ?, origin = ?,
                wattage = ?, warranty_months = ?, serial_number = ?, miles = ?,
                manufacturing_date = ?, brand = ?, vin = ?, accident_history = ?,
                image_data = ?,
                version = version + 1
            WHERE id = ? AND version = ?
        """;
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      setUpdateParams(ps, item);
      ps.setString(26, item.getId());
      ps.setLong(27, item.getVersion());
      int rows = ps.executeUpdate();
      if (rows == 0) {
        throw new StaleObjectException(
            "Item "
                + item.getId()
                + " was modified concurrently (version "
                + item.getVersion()
                + " no longer current).");
      }
      item.setVersion(item.getVersion() + 1);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update item: " + e.getMessage(), e);
    }
  }

  private void setItemParams(PreparedStatement ps, Item item) throws SQLException {
    ps.setString(1, item.getId());
    bindMutableColumns(ps, item, 2);
  }

  /**
   * Binds the 25 mutable item columns (everything except id and version) starting at the given JDBC
   * index.
   */
  private void setUpdateParams(PreparedStatement ps, Item item) throws SQLException {
    bindMutableColumns(ps, item, 1);
  }

  private void bindMutableColumns(PreparedStatement ps, Item item, int start) throws SQLException {
    ps.setString(start, item.getName());
    ps.setString(start + 1, item.getDescription());
    ps.setDouble(start + 2, item.getStartingPrice());
    ps.setDouble(start + 3, item.getCurrentPrice());
    ps.setDouble(start + 4, item.getPriceStep());
    ps.setString(
        start + 5, item.getBidStartTime() != null ? item.getBidStartTime().toString() : null);
    ps.setString(start + 6, item.getBidEndTime() != null ? item.getBidEndTime().toString() : null);
    ps.setString(start + 7, item.getSellerId());
    ps.setString(start + 8, item.getStatus().name());
    ps.setString(start + 9, item.getCurrentWinnerId());
    ps.setString(start + 10, itemType(item));

    // Subtype-specific fields (default null)
    String category = null, condition = null, artist = null, paintingStyle = null, origin = null;
    Integer wattage = null, warrantyMonths = null, miles = null;
    String serialNumber = null, mfgDate = null, brand = null, vin = null;
    int accidentHistory = 0;

    if (item instanceof AuctionItem ai) {
      category = ai.getCategory();
      condition = ai.getCondition();
    }
    if (item instanceof Art a) {
      artist = a.getArtist();
      paintingStyle = a.getPaintingStyle();
      origin = a.getOrigin();
    } else if (item instanceof Electronics e) {
      wattage = e.getWattage();
      origin = e.getOrigin();
      warrantyMonths = e.getWarrantyMonths();
      serialNumber = e.getSerialNumber();
    } else if (item instanceof Vehicle v) {
      miles = v.getMiles();
      mfgDate = v.getManufacturingDate() != null ? v.getManufacturingDate().toString() : null;
      brand = v.getBrand();
      vin = v.getVin();
      accidentHistory = v.hasAccidentHistory() ? 1 : 0;
    }

    ps.setString(start + 11, category);
    ps.setString(start + 12, condition);
    ps.setString(start + 13, artist);
    ps.setString(start + 14, paintingStyle);
    ps.setString(start + 15, origin);
    setNullableInt(ps, start + 16, wattage);
    setNullableInt(ps, start + 17, warrantyMonths);
    ps.setString(start + 18, serialNumber);
    setNullableInt(ps, start + 19, miles);
    ps.setString(start + 20, mfgDate);
    ps.setString(start + 21, brand);
    ps.setString(start + 22, vin);
    ps.setInt(start + 23, accidentHistory);
    ps.setString(start + 24, imagesToJson(item.getImageDataList()));
  }

  private Item mapRow(ResultSet rs) throws SQLException {
    String id = rs.getString("id");
    String name = rs.getString("name");
    String desc = rs.getString("description");
    double startPrice = rs.getDouble("starting_price");
    double currentPrice = rs.getDouble("current_price");
    double priceStep = rs.getDouble("price_step");
    String startTimeStr = rs.getString("bid_start_time");
    String endTimeStr = rs.getString("bid_end_time");
    String sellerId = rs.getString("seller_id");
    String statusStr = rs.getString("status");
    String winnerId = rs.getString("current_winner_id");
    String type = rs.getString("item_type");

    LocalDateTime startTime = startTimeStr != null ? LocalDateTime.parse(startTimeStr) : null;
    LocalDateTime endTime = endTimeStr != null ? LocalDateTime.parse(endTimeStr) : null;

    Item item =
        switch (type) {
          case "Art" ->
              new Art(
                  id,
                  name,
                  desc,
                  startPrice,
                  priceStep,
                  startTime,
                  endTime,
                  sellerId,
                  rs.getString("artist"),
                  rs.getString("painting_style"),
                  rs.getString("origin"));
          case "Electronics" ->
              new Electronics(
                  id,
                  name,
                  desc,
                  startPrice,
                  priceStep,
                  startTime,
                  endTime,
                  sellerId,
                  rs.getInt("wattage"),
                  rs.getString("origin"),
                  rs.getInt("warranty_months"),
                  rs.getString("serial_number"));
          case "Vehicle" -> {
            String mfgStr = rs.getString("manufacturing_date");
            LocalDate mfgDate = mfgStr != null ? LocalDate.parse(mfgStr) : null;
            yield new Vehicle(
                id,
                name,
                desc,
                startPrice,
                priceStep,
                startTime,
                endTime,
                sellerId,
                rs.getInt("miles"),
                mfgDate,
                rs.getString("brand"),
                rs.getString("vin"),
                rs.getInt("accident_history") == 1);
          }
          case "AuctionItem" ->
              new AuctionItem(
                  id,
                  name,
                  desc,
                  startPrice,
                  priceStep,
                  startTime,
                  endTime,
                  sellerId,
                  rs.getString("category"),
                  rs.getString("item_condition"));
          default -> new Other(id, name, desc, startPrice, priceStep, startTime, endTime, sellerId);
        };

    // Restore mutable fields
    item.setCurrentPrice(currentPrice);
    item.setStatus(AuctionStatus.valueOf(statusStr));
    item.setCurrentWinnerId(winnerId);
    item.setImageDataList(parseImages(rs.getString("image_data")));
    item.setVersion(rs.getLong("version"));
    return item;
  }

  private List<String> parseImages(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    if (raw.trim().startsWith("[")) return imagesFromJson(raw);
    return List.of(raw);
  }

  private String imagesToJson(List<String> images) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < images.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append('"').append(images.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
    }
    return sb.append(']').toString();
  }

  private List<String> imagesFromJson(String raw) {
    List<String> result = new ArrayList<>();
    String s = raw.trim();
    if (s.length() < 2) return result;
    StringBuilder current = new StringBuilder();
    boolean inString = false;
    boolean escaped = false;
    for (int i = 1; i < s.length() - 1; i++) {
      char ch = s.charAt(i);
      if (!inString) {
        if (ch == '"') inString = true;
        continue;
      }
      if (escaped) {
        current.append(ch);
        escaped = false;
      } else if (ch == '\\') {
        escaped = true;
      } else if (ch == '"') {
        result.add(current.toString());
        current.setLength(0);
        inString = false;
      } else {
        current.append(ch);
      }
    }
    return result;
  }

  private String itemType(Item item) {
    return item.getTypeName();
  }

  private void setNullableInt(PreparedStatement ps, int idx, Integer val) throws SQLException {
    if (val != null) ps.setInt(idx, val);
    else ps.setNull(idx, Types.INTEGER);
  }
}
