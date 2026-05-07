package com.auction.security;

import com.auction.exception.UnauthorizedActionException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class JwtUtil {

    private static final long TOKEN_EXPIRY_SECONDS = 3600;
    private static final byte[] SECRET;
    private static final String HEADER_B64;

    static {
        String envSecret = System.getenv("JWT_SECRET");
        if (envSecret != null && !envSecret.isBlank()) {
            SECRET = envSecret.getBytes(StandardCharsets.UTF_8);
        } else {
            SECRET = new byte[32];
            new SecureRandom().nextBytes(SECRET);
        }
        HEADER_B64 = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    }

    public static String generateToken(String userId, String role) {
        long now = System.currentTimeMillis() / 1000L;
        long exp = now + TOKEN_EXPIRY_SECONDS;
        String payload = "{\"sub\":\"" + escapeJson(userId) + "\","
                + "\"role\":\"" + escapeJson(role) + "\","
                + "\"iat\":" + now + ","
                + "\"exp\":" + exp + "}";
        String payloadB64 = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        String sigInput = HEADER_B64 + "." + payloadB64;
        String signature = base64Url(hmacSha256(sigInput));
        return sigInput + "." + signature;
    }

    public static Map<String, String> validateToken(String token) {
        if (token == null || token.isBlank())
            throw new UnauthorizedActionException("Missing token.");

        String[] parts = token.split("\\.");
        if (parts.length != 3)
            throw new UnauthorizedActionException("Malformed token.");

        String sigInput = parts[0] + "." + parts[1];
        String expectedSig = base64Url(hmacSha256(sigInput));
        if (!constantTimeEquals(expectedSig, parts[2]))
            throw new UnauthorizedActionException("Invalid token signature.");

        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, String> claims = parsePayload(payloadJson);

        long exp = Long.parseLong(claims.getOrDefault("exp", "0"));
        if (System.currentTimeMillis() / 1000L > exp)
            throw new UnauthorizedActionException("Token expired.");

        return claims;
    }

    private static byte[] hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static Map<String, String> parsePayload(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        for (String pair : json.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String val = kv[1].trim().replace("\"", "");
                map.put(key, val);
            }
        }
        return map;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
