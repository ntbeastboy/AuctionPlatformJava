package com.auction.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final Connection connection;

    public DatabaseManager(String dbPath) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            connection.setAutoCommit(true);
            createTables();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite database: " + e.getMessage(), e);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                    id TEXT PRIMARY KEY,
                    username TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL,
                    role TEXT NOT NULL,
                    balance REAL DEFAULT 0.0,
                    ban_type TEXT,
                    ban_expiry_unix INTEGER DEFAULT 0
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS items (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT,
                    starting_price REAL NOT NULL,
                    current_price REAL NOT NULL,
                    price_step REAL NOT NULL,
                    bid_start_time TEXT,
                    bid_end_time TEXT,
                    seller_id TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'OPEN',
                    current_winner_id TEXT,
                    item_type TEXT NOT NULL,
                    category TEXT,
                    item_condition TEXT,
                    artist TEXT,
                    painting_style TEXT,
                    origin TEXT,
                    wattage INTEGER,
                    warranty_months INTEGER,
                    serial_number TEXT,
                    miles INTEGER,
                    manufacturing_date TEXT,
                    brand TEXT,
                    vin TEXT,
                    accident_history INTEGER DEFAULT 0
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS bids (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    bidder_id TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    amount REAL NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing database: " + e.getMessage());
        }
    }
}
