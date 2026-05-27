package com.auction.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static DatabaseManager instance;

    private final Connection connection;

    public static synchronized DatabaseManager getInstance(String dbPath) {
        if (instance == null) instance = new DatabaseManager(dbPath);
        return instance;
    }

    public DatabaseManager(String dbPath) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            connection.setAutoCommit(true);
            try (Statement stmt = connection.createStatement()) {
                // Enforce FKs and use WAL for better read/write concurrency.
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL");
            }
            createTables();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite database: " + e.getMessage(), e);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    /**
     * Run the given action inside a SQLite transaction. Commits on normal
     * return; rolls back on any thrown exception. Synchronizes on the single
     * shared {@link Connection} so callers don't trample each other's
     * autoCommit state.
     */
    public synchronized void inTransaction(TxAction action) {
        boolean prevAutoCommit;
        try {
            prevAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to begin transaction: " + e.getMessage(), e);
        }
        try {
            action.run();
            connection.commit();
        } catch (RuntimeException | SQLException ex) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            if (ex instanceof RuntimeException re) throw re;
            throw new RuntimeException("Transaction failed: " + ex.getMessage(), ex);
        } finally {
            try { connection.setAutoCommit(prevAutoCommit); } catch (SQLException ignored) {}
        }
    }

    @FunctionalInterface
    public interface TxAction {
        void run() throws SQLException;
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
                    accident_history INTEGER DEFAULT 0,
                    version INTEGER NOT NULL DEFAULT 0
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

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS auto_bids (
                    user_id TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    max_bid REAL NOT NULL,
                    increment REAL NOT NULL,
                    created_at INTEGER NOT NULL,
                    last_bid_at INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (user_id, item_id)
                )
            """);

            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bids_item_id ON bids(item_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bids_bidder_id ON bids(bidder_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_auto_bids_item_id ON auto_bids(item_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_auto_bids_user_id ON auto_bids(user_id)");
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
