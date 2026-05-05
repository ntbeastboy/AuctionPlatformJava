package com.auction.app;

import com.auction.model.User;
import com.auction.repository.ItemRepository;
import com.auction.repository.UserRepository;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
import com.auction.service.ItemService;
import com.auction.service.UserService;

public class AppState {
    public final UserRepository userRepository;
    public final ItemRepository itemRepository;
    public final UserService userService;
    public final ItemService itemService;
    public final BidService bidService;
    public final AuctionService auctionService;

    public User currentUser;

    public AppState(UserRepository userRepository, ItemRepository itemRepository,
                    UserService userService, ItemService itemService,
                    BidService bidService, AuctionService auctionService) {
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.userService = userService;
        this.itemService = itemService;
        this.bidService = bidService;
        this.auctionService = auctionService;
    }
}