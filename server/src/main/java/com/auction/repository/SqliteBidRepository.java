package com.auction.repository;

import com.auction.model.Bid;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteBidRepository implements BidRepository {

  private final Connection conn;

  public SqliteBidRepository(DatabaseManager dbManager) {
    this.conn = dbManager.getConnection();
  }

  @Override
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

  @Override
  public Optional<Bid> findById(int id) {
    String sql = "SELECT * FROM bids WHERE id = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, id);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) return Optional.of(mapRow(rs));
      return Optional.empty();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find bid: " + e.getMessage(), e);
    }
  }

  @Override
  public List<Bid> findByItemId(String itemId) {
    String sql = "SELECT * FROM bids WHERE item_id = ? ORDER BY timestamp ASC, id ASC";
    return query(sql, itemId);
  }

  @Override
  public List<Bid> findByBidderId(String bidderId) {
    String sql = "SELECT * FROM bids WHERE bidder_id = ? ORDER BY timestamp DESC";
    return query(sql, bidderId);
  }

  @Override
  public void deleteLatestByBidderAndItem(String bidderId, String itemId) {
    String sql =
        "DELETE FROM bids WHERE rowid IN ("
            + "SELECT rowid FROM bids WHERE bidder_id = ? AND item_id = ? "
            + "ORDER BY timestamp DESC, rowid DESC LIMIT 1"
            + ")";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, bidderId);
      ps.setString(2, itemId);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to delete latest bid: " + e.getMessage(), e);
    }
  }

  private List<Bid> query(String sql, String param) {
    List<Bid> result = new ArrayList<>();
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, param);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        result.add(mapRow(rs));
      }
      return result;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to query bids: " + e.getMessage(), e);
    }
  }

  private Bid mapRow(ResultSet rs) throws SQLException {
    return new Bid(
        rs.getString("bidder_id"),
        rs.getString("item_id"),
        rs.getDouble("amount"),
        rs.getLong("timestamp"));
  }
}
