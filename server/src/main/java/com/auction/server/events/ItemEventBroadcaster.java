package com.auction.server.events;

import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ItemEventBroadcaster {

  private final Set<WsContext> clients = ConcurrentHashMap.newKeySet();

  private enum EventType {
    ITEM_UPDATED,
    ITEMS_CHANGED,
    USER_UPDATED,
    USER_BANNED,
    USER_DELETED,
    USERS_CHANGED
  }

  public void register(Javalin app) {
    app.ws(
        "/api/events",
        ws -> {
          ws.onConnect(ctx -> clients.add(ctx));
          ws.onClose(ctx -> clients.remove(ctx));
          ws.onError(ctx -> clients.remove(ctx));
        });
  }

  public void broadcastItemUpdated(String itemId) {
    broadcast(createEvent(EventType.ITEM_UPDATED, "itemId", itemId));
  }

  public void broadcastItemsChanged() {
    broadcast(createEvent(EventType.ITEMS_CHANGED));
  }

  public void broadcastUserUpdated(String userId) {
    broadcast(createEvent(EventType.USER_UPDATED, "userId", userId));
    broadcastUsersChanged();
  }

  public void broadcastUserBanned(String userId) {
    broadcast(createEvent(EventType.USER_BANNED, "userId", userId));
    broadcastUsersChanged();
  }

  public void broadcastUserDeleted(String userId) {
    broadcast(createEvent(EventType.USER_DELETED, "userId", userId));
    broadcastUsersChanged();
  }

  public void broadcastUsersChanged() {
    broadcast(createEvent(EventType.USERS_CHANGED));
  }

  private String createEvent(EventType type) {
    return "{\"type\":\"" + type.name() + "\"}";
  }

  private String createEvent(EventType type, String key, String value) {
    return "{"
        + "\"type\":\"" + type.name() + "\","
        + "\"" + escapeJson(key) + "\":\"" + escapeJson(value) + "\""
        + "}";
  }

  private void broadcast(String message) {
    for (WsContext client : clients) {
      try {
        client.send(message);
      } catch (Exception e) {
        clients.remove(client);
      }
    }
  }

  private String escapeJson(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}