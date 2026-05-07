package com.auction.service.rest;

import com.auction.exception.InvalidBidException;
import com.auction.model.Bid;
import com.auction.model.User;
import com.auction.service.BidService;
import com.auction.service.http.HttpClientService;
import com.auction.service.http.JsonMappers;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Network-backed BidService — POSTs the bid to the API and reconstructs
 * the persisted Bid from the response. Concurrency, balance checks and
 * the optimistic-locking version bump all happen server-side; the client
 * only sees the result.
 */
public class RestBidService extends BidService {

    private static final Type MAP = new TypeToken<Map<String, Object>>(){}.getType();

    private final HttpClientService http;

    public RestBidService(HttpClientService http) {
        super(null, null, null, null);
        this.http = http;
    }

    @Override
    public Bid placeBid(User user, String itemId, double amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("itemId", itemId);
        body.put("bidAmount", amount);
        try {
            String response = http.post("/bids", http.getGson().toJson(body));
            Map<String, Object> map = http.getGson().fromJson(response, MAP);
            return JsonMappers.toBid(map);
        } catch (IOException e) {
            // Server-side validation messages come back via the IOException
            // payload; surface them to the UI as InvalidBidException so the
            // existing error handler shows them verbatim.
            throw new InvalidBidException(e.getMessage());
        }
    }
}
