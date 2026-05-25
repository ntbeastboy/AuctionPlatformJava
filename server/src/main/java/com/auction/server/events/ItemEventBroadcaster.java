package com.auction.server.events;

import io.javalin.Javalin;
import io.javalin.websocket.WsContext;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ItemEventBroadcaster {

    private final Set<WsContext> clients = ConcurrentHashMap.newKeySet();

    public void register(Javalin app) {
        app.ws("/api/events", ws -> {
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
