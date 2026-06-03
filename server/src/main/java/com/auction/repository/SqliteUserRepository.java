package com.auction.repository;

import com.auction.model.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteUserRepository implements UserRepository {

    private final Connection conn;

    public SqliteUserRepository(DatabaseManager dbManager) {
        this.conn = dbManager.getConnection();
    }

    @Override
    public void save(User user) {
        String sql = """
            INSERT OR REPLACE INTO users (id, username, password, role, balance, ban_type, ban_expiry_unix)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPassword());
            ps.setString(4, roleOf(user));
            ps.setDouble(5, balanceOf(user));
            if (user instanceof BannableUser bu) {
                ps.setString(6, bu.getBanType() != null ? bu.getBanType().name() : null);
                ps.setLong(7, bu.getBanExpiryUnix());
            } else {
                ps.setNull(6, Types.VARCHAR);
                ps.setLong(7, 0);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save user: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find user by username: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    @Override
    public Optional<User> findById(String id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find user by id: " + e.getMessage(), e);
        }
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users";
        List<User> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) result.add(mapRow(rs));
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all users: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete user: " + e.getMessage(), e);
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String username = rs.getString("username");
        String password = rs.getString("password");
        String role = rs.getString("role");
        double balance = rs.getDouble("balance");
        String banType = rs.getString("ban_type");
        long banExpiry = rs.getLong("ban_expiry_unix");

        User user = switch (role) {
            case "ADMIN" -> new Admin(id, username, password);
            case "BIDDER" -> {
                Bidder b = new Bidder(id, username, password);
                if (balance > 0) b.addFunds(balance);
                if (banType != null) {
                    if ("PERMANENT".equals(banType)) b.banPermanent();
                    else if ("TEMPORARY".equals(banType)) {
                        long remaining = banExpiry - System.currentTimeMillis() / 1000L;
                        if (remaining > 0) b.banTemporary(remaining);
                    }
                }
                yield b;
            }
            case "SELLER" -> {
                Seller s = new Seller(id, username, password);
                if (balance > 0) s.addFunds(balance);
                if (banType != null) {
                    if ("PERMANENT".equals(banType)) s.banPermanent();
                    else if ("TEMPORARY".equals(banType)) {
                        long remaining = banExpiry - System.currentTimeMillis() / 1000L;
                        if (remaining > 0) s.banTemporary(remaining);
                    }
                }
                yield s;
            }
            default -> throw new RuntimeException("Unknown role: " + role);
        };
        return user;
    }

    private String roleOf(User user) {
        if (user instanceof Admin) return "ADMIN";
        if (user instanceof Bidder) return "BIDDER";
        if (user instanceof Seller) return "SELLER";
        return "UNKNOWN";
    }

    private double balanceOf(User user) {
        if (user instanceof Bidder b) return b.getBalance();
        if (user instanceof Seller s) return s.getBalance();
        return 0.0;
    }
}
