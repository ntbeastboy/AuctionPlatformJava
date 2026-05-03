package com.auction.repository;

import com.auction.model.Bid;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SqliteBidRepository {

    private final Connection conn;

    public SqliteBidRepository(DatabaseManager dbManager) {
        this.conn = dbManager.getConnection();
    }

    public void save(Bid bid) {
        String sql = "INSERT INTO bids (bidder_id, item_id, amount, timestamp) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bid.getBidderId());
            ps.setString(2, bid.getItemId());
            ps.setDouble(3, bid.getAmount());
            ps.setLong(4, bid.getTimestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save bid: " + e.getMessage(), e);
        }
    }

    public List<Bid> findByItemId(String itemId) {
        String sql = "SELECT * FROM bids WHERE item_id = ? ORDER BY amount DESC";
        return query(sql, itemId);
    }

    public List<Bid> findByBidderId(String bidderId) {
        String sql = "SELECT * FROM bids WHERE bidder_id = ? ORDER BY timestamp DESC";
        return query(sql, bidderId);
    }

    private List<Bid> query(String sql, String param) {
        List<Bid> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new Bid(
                    rs.getString("bidder_id"),
                    rs.getString("item_id"),
                    rs.getDouble("amount")
                ));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query bids: " + e.getMessage(), e);
        }
    }
}
