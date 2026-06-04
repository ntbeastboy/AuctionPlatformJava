package com.auction.app;

import com.auction.model.User;
import com.auction.repository.ItemRepository;
import com.auction.repository.UserRepository;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
import com.auction.service.ItemService;
import com.auction.service.UserService;
import com.auction.service.http.HttpClientService;
import com.auction.service.rest.RestUserService;

public class AppState {
  public final HttpClientService httpClient;
  public final UserRepository userRepository;
  public final ItemRepository itemRepository;
  public final UserService userService;
  public final ItemService itemService;
  public final BidService bidService;
  public final AuctionService auctionService;

  /**
   * Same instance as {@link #userService}, but typed concretely so the UI can call REST-only
   * helpers (addBalance / deductBalance) without an instanceof check.
   */
  public final RestUserService restUserService;

  public User currentUser;

  public AppState(
      HttpClientService httpClient,
      UserRepository userRepository,
      ItemRepository itemRepository,
      UserService userService,
      ItemService itemService,
      BidService bidService,
      AuctionService auctionService,
      RestUserService restUserService) {
    this.httpClient = httpClient;
    this.userRepository = userRepository;
    this.itemRepository = itemRepository;
    this.userService = userService;
    this.itemService = itemService;
    this.bidService = bidService;
    this.auctionService = auctionService;
    this.restUserService = restUserService;
  }
}
