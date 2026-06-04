package com.auction.server.events;

import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ItemEventBroadcaster {

  private final Set<WsContext> clients = ConcurrentHashMap.newKeySet();

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
    broadcast("{\"type\":\"ITEM_UPDATED\",\"itemId\":\"" + escapeJson(itemId) + "\"}");
  }

  public void broadcastItemsChanged() {
    broadcast("{\"type\":\"ITEMS_CHANGED\"}");
  }

  public void broadcastUserUpdated(String userId) {
    broadcast("{\"type\":\"USER_UPDATED\",\"userId\":\"" + escapeJson(userId) + "\"}");
    broadcastUsersChanged();
  }

  public void broadcastUserBanned(String userId) {
    broadcast("{\"type\":\"USER_BANNED\",\"userId\":\"" + escapeJson(userId) + "\"}");
    broadcastUsersChanged();
  }

  public void broadcastUserDeleted(String userId) {
    broadcast("{\"type\":\"USER_DELETED\",\"userId\":\"" + escapeJson(userId) + "\"}");
    broadcastUsersChanged();
  }

  public void broadcastUsersChanged() {
    broadcast("{\"type\":\"USERS_CHANGED\"}");
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
