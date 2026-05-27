package com.auction.repository;

import com.auction.model.AutoBid;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteAutoBidRepository implements AutoBidRepository {

    private final Connection conn;

    public SqliteAutoBidRepository(DatabaseManager dbManager) {
        this.conn = dbManager.getConnection();
    }

    @Override
    public void save(AutoBid autoBid) {
        String sql = """
            INSERT INTO auto_bids (user_id, item_id, max_bid, increment, created_at, last_bid_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(user_id, item_id) DO UPDATE SET
                max_bid = excluded.max_bid,
                increment = excluded.increment,
                created_at = auto_bids.created_at,
                last_bid_at = auto_bids.last_bid_at
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, autoBid.getUserId());
            ps.setString(2, autoBid.getItemId());
            ps.setDouble(3, autoBid.getMaxBid());
            ps.setDouble(4, autoBid.getIncrement());
            ps.setLong(5, autoBid.getCreatedAt());
            ps.setLong(6, autoBid.getLastBidAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save auto-bid: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<AutoBid> findByUserAndItem(String userId, String itemId) {
        String sql = "SELECT * FROM auto_bids WHERE user_id = ? AND item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find auto-bid: " + e.getMessage(), e);
        }
    }

    @Override
    public List<AutoBid> findByItemId(String itemId) {
        String sql = "SELECT * FROM auto_bids WHERE item_id = ? ORDER BY max_bid DESC, created_at ASC";
        return query(sql, itemId);
    }

    @Override
    public List<AutoBid> findByUserId(String userId) {
        String sql = "SELECT * FROM auto_bids WHERE user_id = ?";
        return query(sql, userId);
    }

    @Override
    public void delete(String userId, String itemId) {
        String sql = "DELETE FROM auto_bids WHERE user_id = ? AND item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, itemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete auto-bid: " + e.getMessage(), e);
        }
    }

    @Override
    public void recordBid(String userId, String itemId, long bidAt) {
        String sql = "UPDATE auto_bids SET last_bid_at = ? WHERE user_id = ? AND item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bidAt);
            ps.setString(2, userId);
            ps.setString(3, itemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update auto-bid cooldown: " + e.getMessage(), e);
        }
    }

    private List<AutoBid> query(String sql, String param) {
        List<AutoBid> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(mapRow(rs));
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query auto-bids: " + e.getMessage(), e);
        }
    }

    private AutoBid mapRow(ResultSet rs) throws SQLException {
        return new AutoBid(
                rs.getString("user_id"),
                rs.getString("item_id"),
                rs.getDouble("max_bid"),
                rs.getDouble("increment"),
                rs.getLong("created_at"),
                rs.getLong("last_bid_at")
        );
    }
}
