package com.auction.service.rest;

import com.auction.model.User;
import com.auction.service.AuctionService;
import com.auction.service.http.HttpClientService;

import java.io.IOException;

/**
 * Network-backed AuctionService — every action becomes a POST to the
 * matching server endpoint. Scheduling and timer recovery happen on the
 * server, so the client implementation is essentially a thin proxy.
 */
public class RestAuctionService extends AuctionService {

    private final HttpClientService http;

    public RestAuctionService(HttpClientService http) {
        super(null, null, null);
        this.http = http;
    }

    @Override
    public void startAuction(String itemId, User requestingUser) {
        post("/auction/" + itemId + "/start");
    }

    @Override
    public void closeAuction(String itemId) {
        // The server closes auctions automatically when their timer fires;
        // there is no public "close" endpoint, only "end early" which is
        // admin-only. Keep the method as a no-op rather than 404 the user.
    }

    @Override
    public void endAuctionEarly(String itemId, User requestingUser) {
        post("/auction/" + itemId + "/end");
    }

    @Override
    public void cancelAuction(String itemId, User requestingUser) {
        post("/auction/" + itemId + "/cancel");
    }

    @Override
    public void paySeller(String itemId, User requestingUser) {
        post("/auction/" + itemId + "/pay");
    }

    @Override
    public void recoverScheduledAuctions() {
        // Server handles this on its own startup.
    }

    @Override
    public void shutdown() {
        // No client-side scheduler to shut down.
    }

    private void post(String endpoint) {
        try {
            http.post(endpoint, "{}");
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
