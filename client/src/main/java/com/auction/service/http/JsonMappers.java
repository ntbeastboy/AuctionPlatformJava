package com.auction.service.http;

import com.auction.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Translates JSON maps returned by the REST API into domain model
 * instances used by the desktop client.
 *
 * The server never sends password hashes over the wire, so reconstructed
 * users carry an empty password — the client never needs it (auth is
 * already established by the JWT bearer token).
 */
public final class JsonMappers {

    private JsonMappers() {}

    public static User toUser(Map<String, Object> map) {
        if (map == null) return null;
        String id = str(map, "id");
        String username = str(map, "username");
        String role = str(map, "role");

        User user;
        switch (role == null ? "" : role.toUpperCase()) {
            case "ADMIN" -> user = new Admin(id, username, "");
            case "SELLER" -> {
                Seller s = new Seller(id, username, "");
                double bal = num(map, "balance");
                if (bal > 0) s.addFunds(bal);
                applyBan(s, map);
                user = s;
            }
            case "BIDDER" -> {
                Bidder b = new Bidder(id, username, "");
                double bal = num(map, "balance");
                if (bal > 0) b.addFunds(bal);
                applyBan(b, map);
                user = b;
            }
            default -> {
                // Fall back to bidder if the server omitted role for any reason.
                Bidder b = new Bidder(id, username, "");
                double bal = num(map, "balance");
                if (bal > 0) b.addFunds(bal);
                applyBan(b, map);
                user = b;
            }
        }
        return user;
    }

    private static void applyBan(BannableUser u, Map<String, Object> map) {
        Object banned = map.get("banned");
        if (Boolean.TRUE.equals(banned)) u.banPermanent();
    }

    public static Item toItem(Map<String, Object> map) {
        if (map == null) return null;
        String id = str(map, "id");
        String name = str(map, "name");
        String description = str(map, "description");
        double startPrice = num(map, "startPrice");
        double currentPrice = num(map, "currentPrice");
        double priceStep = num(map, "priceStep");
        String sellerId = str(map, "sellerId");
        String type = str(map, "type");
        String statusStr = str(map, "status");
        String winnerId = str(map, "currentWinnerId");
        LocalDateTime bidStart = parseDateTime(str(map, "bidStartTime"));
        LocalDateTime bidEnd = parseDateTime(str(map, "bidEndTime"));

        Item item;
        switch (type == null ? "Other" : type) {
            case "Art" -> item = new Art(id, name, description, startPrice, priceStep, bidStart, bidEnd, sellerId,
                    str(map, "artist"), str(map, "paintingStyle"), str(map, "origin"));
            case "Electronics" -> item = new Electronics(id, name, description, startPrice, priceStep, bidStart, bidEnd, sellerId,
                    (int) num(map, "wattage"), str(map, "origin"),
                    (int) num(map, "warrantyMonths"), str(map, "serialNumber"));
            case "Vehicle" -> {
                LocalDate mfg = parseDate(str(map, "manufacturingDate"));
                item = new Vehicle(id, name, description, startPrice, priceStep, bidStart, bidEnd, sellerId,
                        (int) num(map, "miles"), mfg, str(map, "brand"), str(map, "vin"),
                        Boolean.TRUE.equals(map.get("accidentHistory")));
            }
            default -> item = new Other(id, name, description, startPrice, priceStep, bidStart, bidEnd, sellerId);
        }

        item.setCurrentPrice(currentPrice);
        item.setCurrentWinnerId(winnerId);
        if (statusStr != null) {
            try { item.setStatus(AuctionStatus.valueOf(statusStr)); }
            catch (IllegalArgumentException ignored) {}
        }
        return item;
    }

    public static Bid toBid(Map<String, Object> map) {
        if (map == null) return null;
        String bidderId = str(map, "bidderId");
        String itemId = str(map, "itemId");
        double amount = num(map, "bidAmount");
        long timestamp = Math.round(num(map, "timestamp"));
        if (timestamp <= 0) return new Bid(bidderId, itemId, amount);
        return new Bid(bidderId, itemId, amount, timestamp);
    }

    public static AutoBid toAutoBid(Map<String, Object> map) {
        if (map == null) return null;
        return new AutoBid(
                str(map, "userId"),
                str(map, "itemId"),
                num(map, "maxBid"),
                num(map, "increment"),
                Math.round(num(map, "createdAt"))
        );
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> asList(Object o) {
        if (o instanceof List<?> l) return (List<Map<String, Object>>) l;
        return List.of();
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private static double num(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private static LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s); }
        catch (Exception e) { return null; }
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s); }
        catch (Exception e) { return null; }
    }
}
