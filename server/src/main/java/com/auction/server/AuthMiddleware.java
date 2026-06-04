package com.auction.server;

import com.auction.security.JwtUtil;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.util.Map;
import java.util.Set;

public class AuthMiddleware implements Handler {

  private static final Set<String> PUBLIC_PATHS = Set.of("/api/users/login", "/api/users/register");

  @Override
  public void handle(Context ctx) {
    if (PUBLIC_PATHS.contains(ctx.path())) return;
    if (!ctx.path().startsWith("/api")) return;

    String authHeader = ctx.header("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      ctx.status(401).json(Map.of("error", "Missing or invalid Authorization header."));
      ctx.skipRemainingHandlers();
      return;
    }

    String token = authHeader.substring(7);
    try {
      Map<String, String> claims = JwtUtil.validateToken(token);
      ctx.attribute("userId", claims.get("sub"));
      ctx.attribute("role", claims.get("role"));
    } catch (Exception e) {
      ctx.status(401).json(Map.of("error", e.getMessage()));
      ctx.skipRemainingHandlers();
    }
  }
}
