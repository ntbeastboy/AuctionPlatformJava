package com.auction.server;

import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidInputException;
import com.auction.exception.ProductNotFoundException;
import com.auction.exception.UnauthorizedActionException;
import com.auction.exception.UserNotFoundException;
import io.javalin.Javalin;
import io.javalin.http.ExceptionHandler;
import java.util.Map;

public class ExceptionMapper {

  public static void register(Javalin app) {
    app.exception(InvalidInputException.class, errorHandler(400));

    app.exception(
        UnauthorizedActionException.class,
        (e, ctx) -> {
          String message = msg(e);

          if (message.contains("Invalid username or password")) {
            ctx.status(401).json(error(message));
          } else {
            ctx.status(403).json(error(message));
          }
        });

    app.exception(ProductNotFoundException.class, errorHandler(404));
    app.exception(UserNotFoundException.class, errorHandler(404));

    app.exception(InvalidBidException.class, errorHandler(409));
    app.exception(AuctionClosedException.class, errorHandler(409));
    app.exception(IllegalStateException.class, errorHandler(409));

    app.exception(Exception.class, errorHandler(500));
  }

  private static <T extends Exception> ExceptionHandler<T> errorHandler(int statusCode) {
    return (e, ctx) -> ctx.status(statusCode).json(error(msg(e)));
  }

  private static Map<String, String> error(String message) {
    return Map.of("error", message);
  }

  private static String msg(Exception e) {
    return e.getMessage() != null ? e.getMessage() : "Unknown error";
  }
}