package com.auction.server;

import com.auction.exception.*;
import io.javalin.Javalin;

import java.util.Map;

public class ExceptionMapper {

    public static void register(Javalin app) {
        app.exception(InvalidInputException.class, (e, ctx) -> {
            ctx.status(400).json(Map.of("error", msg(e)));
        });

        app.exception(UnauthorizedActionException.class, (e, ctx) -> {
            String message = msg(e);
            // Login failures (bad credentials) → 401; permission/role failures → 403
            if (message.contains("Invalid username or password")) {
                ctx.status(401).json(Map.of("error", message));
            } else {
                ctx.status(403).json(Map.of("error", message));
            }
        });

        app.exception(ProductNotFoundException.class, (e, ctx) -> {
            ctx.status(404).json(Map.of("error", msg(e)));
        });

        app.exception(UserNotFoundException.class, (e, ctx) -> {
            ctx.status(404).json(Map.of("error", msg(e)));
        });

        app.exception(InvalidBidException.class, (e, ctx) -> {
            ctx.status(409).json(Map.of("error", msg(e)));
        });

        app.exception(AuctionClosedException.class, (e, ctx) -> {
            ctx.status(409).json(Map.of("error", msg(e)));
        });

        app.exception(IllegalStateException.class, (e, ctx) -> {
            ctx.status(409).json(Map.of("error", msg(e)));
        });

        app.exception(Exception.class, (e, ctx) -> {
            ctx.status(500).json(Map.of("error", msg(e)));
        });
    }

    private static String msg(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "Unknown error";
    }
}
