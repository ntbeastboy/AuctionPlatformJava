package com.auction.service.network;

import com.auction.dto.BidRemoteDto;
import com.auction.service.http.HttpClientService;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkBidService {

    private final HttpClientService httpClient;

    public NetworkBidService(HttpClientService httpClient) {
        this.httpClient = httpClient;
    }

    public BidRemoteDto placeBid(String bidderId, String itemId, Double bidAmount) throws IOException {
        Map<String, Object> bidData = new HashMap<>();
        bidData.put("itemId", itemId);
        bidData.put("bidAmount", bidAmount);

        String response = httpClient.post("/bids?bidderId=" + bidderId, httpClient.getGson().toJson(bidData));
        return httpClient.getGson().fromJson(response, BidRemoteDto.class);
    }

    public BidRemoteDto getBidById(String id) throws IOException {
        String response = httpClient.get("/bids/" + id);
        return httpClient.getGson().fromJson(response, BidRemoteDto.class);
    }

    public List<BidRemoteDto> getBidsForItem(String itemId) throws IOException {
        String response = httpClient.get("/bids/item/" + itemId);
        return httpClient.getGson().fromJson(response, new TypeToken<List<BidRemoteDto>>(){}.getType());
    }

    public List<BidRemoteDto> getBidsByBidder(String bidderId) throws IOException {
        String response = httpClient.get("/bids/bidder/" + bidderId);
        return httpClient.getGson().fromJson(response, new TypeToken<List<BidRemoteDto>>(){}.getType());
    }

    public BidRemoteDto getHighestBidForItem(String itemId) throws IOException {
        String response = httpClient.get("/bids/item/" + itemId + "/highest");
        return httpClient.getGson().fromJson(response, BidRemoteDto.class);
    }
}
