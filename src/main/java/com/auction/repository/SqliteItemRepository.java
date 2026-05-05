package com.auction.repository;

import com.auction.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.auction.model.AuctionItem;
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
        String sql = """
            INSERT OR REPLACE INTO items (
                id, name, description, starting_price, current_price, price_step,
                bid_start_time, bid_end_time, seller_id, status, current_winner_id,
                item_type, category, item_condition, artist, painting_style, origin,
                wattage, warranty_months, serial_number, miles, manufacturing_date,
                brand, vin, accident_history
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setItemParams(ps, item);
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
        save(item); // INSERT OR REPLACE handles upsert
    }

    private void setItemParams(PreparedStatement ps, Item item) throws SQLException {
        ps.setString(1, item.getId());
        ps.setString(2, item.getName());
        ps.setString(3, item.getDescription());
        ps.setDouble(4, item.getStartingPrice());
        ps.setDouble(5, item.getCurrentPrice());
        ps.setDouble(6, item.getPriceStep());
        ps.setString(7, item.getBidStartTime() != null ? item.getBidStartTime().toString() : null);
        ps.setString(8, item.getBidEndTime() != null ? item.getBidEndTime().toString() : null);
        ps.setString(9, item.getSellerId());
        ps.setString(10, item.getStatus().name());
        ps.setString(11, item.getCurrentWinnerId());
        ps.setString(12, itemType(item));

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

        ps.setString(13, category);
        ps.setString(14, condition);
        ps.setString(15, artist);
        ps.setString(16, paintingStyle);
        ps.setString(17, origin);
        setNullableInt(ps, 18, wattage);
        setNullableInt(ps, 19, warrantyMonths);
        ps.setString(20, serialNumber);
        setNullableInt(ps, 21, miles);
        ps.setString(22, mfgDate);
        ps.setString(23, brand);
        ps.setString(24, vin);
        ps.setInt(25, accidentHistory);
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

        Item item = switch (type) {
            case "Art" -> new Art(id, name, desc, startPrice, priceStep, startTime, endTime, sellerId,
                    rs.getString("artist"), rs.getString("painting_style"), rs.getString("origin"));
            case "Electronics" -> new Electronics(id, name, desc, startPrice, priceStep, startTime, endTime, sellerId,
                    rs.getInt("wattage"), rs.getString("origin"),
                    rs.getInt("warranty_months"), rs.getString("serial_number"));
            case "Vehicle" -> {
                String mfgStr = rs.getString("manufacturing_date");
                LocalDate mfgDate = mfgStr != null ? LocalDate.parse(mfgStr) : null;
                yield new Vehicle(id, name, desc, startPrice, priceStep, startTime, endTime, sellerId,
                        rs.getInt("miles"), mfgDate, rs.getString("brand"),
                        rs.getString("vin"), rs.getInt("accident_history") == 1);
            }
            case "AuctionItem" -> new AuctionItem(id, name, desc, startPrice, priceStep, startTime, endTime, sellerId,
                    rs.getString("category"), rs.getString("item_condition"));
            default -> new Other(id, name, desc, startPrice, priceStep, startTime, endTime, sellerId);
        };

        // Restore mutable fields
        item.setCurrentPrice(currentPrice);
        item.setStatus(AuctionStatus.valueOf(statusStr));
        item.setCurrentWinnerId(winnerId);
        return item;
    }

    private String itemType(Item item) {
        if (item instanceof Art) return "Art";
        if (item instanceof Electronics) return "Electronics";
        if (item instanceof Vehicle) return "Vehicle";
        if (item instanceof AuctionItem) return "AuctionItem";
        return "Other";
    }

    private void setNullableInt(PreparedStatement ps, int idx, Integer val) throws SQLException {
        if (val != null) ps.setInt(idx, val);
        else ps.setNull(idx, Types.INTEGER);
    }
}
