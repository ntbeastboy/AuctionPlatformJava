package com.auction.service;

import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.model.AuctionItem;
import com.auction.model.Bidder;
import com.auction.repository.InMemoryItemRepository;
import com.auction.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class BidServiceTest {

    private ItemRepository itemRepository;
    private BidService bidService;

    @BeforeEach
    void setUp() {
        itemRepository = new InMemoryItemRepository();
        bidService = new BidService(itemRepository);
    }

    @Test
    void testValidBid() {
        AuctionItem item = new AuctionItem("item1", "Test", "Desc", 100, 10,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), "seller1", "Cat", "New");
        item.setStatus(com.auction.model.AuctionStatus.RUNNING);
        itemRepository.save(item);

        Bidder bidder = new Bidder("user1", "user1", "pass");
        bidder.addFunds(500);

        bidService.placeBid(bidder, "item1", 120);

        assertEquals(120, item.getCurrentPrice());
        assertEquals("user1", item.getCurrentWinnerId());
    }

    @Test
    void testBidLowerThanCurrent() {
        AuctionItem item = new AuctionItem("item1", "Test", "Desc", 100, 10,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), "seller1", "Cat", "New");
        item.setStatus(com.auction.model.AuctionStatus.RUNNING);
        itemRepository.save(item);

        Bidder bidder = new Bidder("user1", "user1", "pass");
        bidder.addFunds(500);

        // Min bid is 110 (100 + 10)
        assertThrows(InvalidBidException.class, () -> {
            bidService.placeBid(bidder, "item1", 105);
        });
    }

    @Test
    void testBidAfterTimeout() {
        AuctionItem item = new AuctionItem("item1", "Test", "Desc", 100, 10,
                LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1), "seller1", "Cat", "New");
        item.setStatus(com.auction.model.AuctionStatus.RUNNING);
        itemRepository.save(item);

        Bidder bidder = new Bidder("user1", "user1", "pass");
        bidder.addFunds(500);

        assertThrows(AuctionClosedException.class, () -> {
            bidService.placeBid(bidder, "item1", 120);
        });
    }

    @Test
    void testConcurrency() throws InterruptedException {
        AuctionItem item = new AuctionItem("item1", "Test", "Desc", 100, 10,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), "seller1", "Cat", "New");
        item.setStatus(com.auction.model.AuctionStatus.RUNNING);
        itemRepository.save(item);

        Bidder bidder1 = new Bidder("user1", "u1", "p");
        bidder1.addFunds(200);

        Bidder bidder2 = new Bidder("user2", "u2", "p");
        bidder2.addFunds(200);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        executor.submit(() -> {
            try { bidService.placeBid(bidder1, "item1", 120); } catch (Exception e) {}
            latch.countDown();
        });

        executor.submit(() -> {
            try { bidService.placeBid(bidder2, "item1", 130); } catch (Exception e) {}
            latch.countDown();
        });

        latch.await();
        executor.shutdown();

        // One of them will win because the second thread will read the updated price block.
        // It shouldn't overwrite the price if the bid is invalid due to the sync block.
        assertTrue(item.getCurrentPrice() >= 120);
    }
}
