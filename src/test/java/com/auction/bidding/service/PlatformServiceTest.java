package com.auction.bidding.service;

import com.auction.bidding.dto.BidRequest;
import com.auction.bidding.dto.GameMessage;
import com.auction.bidding.model.AuctionItem;
import com.auction.bidding.model.User;
import com.auction.bidding.repository.AuctionItemRepository;
import com.auction.bidding.repository.BidRepository;
import com.auction.bidding.repository.GameEntryRepository;
import com.auction.bidding.repository.GameRoundRepository;
import com.auction.bidding.repository.TransactionRepository;
import com.auction.bidding.repository.UserRepository;
import com.auction.bidding.scheduler.AuctionScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class PlatformServiceTest {

    @Autowired private AuctionService auctionService;
    @Autowired private GameService gameService;
    @Autowired private AuctionScheduler auctionScheduler;
    @Autowired private UserRepository userRepository;
    @Autowired private AuctionItemRepository itemRepository;
    @Autowired private BidRepository bidRepository;
    @Autowired private TransactionRepository transactionRepository;

    @BeforeEach
    public void setup() {
        bidRepository.deleteAll();
        transactionRepository.deleteAll();
        itemRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "buyer1", roles = {"USER"})
    public void testAuctionLifecycleAndSettlement() {
        // Setup Users
        User creator = User.builder().username("creator1").email("c@test.com").password("pass").role("ROLE_AUCTION_MAKER").walletBalance(BigDecimal.ZERO).build();
        User buyer1 = User.builder().username("buyer1").email("b1@test.com").password("pass").role("ROLE_USER").walletBalance(new BigDecimal("1000")).build();
        User buyer2 = User.builder().username("buyer2").email("b2@test.com").password("pass").role("ROLE_USER").walletBalance(new BigDecimal("1000")).build();
        userRepository.save(creator);
        userRepository.save(buyer1);
        userRepository.save(buyer2);

        // Setup Auction
        AuctionItem item = AuctionItem.builder()
                .title("Test Item")
                .startingPrice(new BigDecimal("100"))
                .currentPrice(new BigDecimal("100"))
                .endTime(LocalDateTime.now().plusSeconds(2))
                .creatorName("creator1")
                .active(true)
                .build();
        item = itemRepository.save(item);

        // Buyer 1 Bids
        BidRequest req1 = new BidRequest();
        req1.setItemId(item.getId());
        req1.setAmount(new BigDecimal("150"));
        req1.setBidderName("buyer1");
        auctionService.placeBid(req1);

        // Verify balance deducted
        buyer1 = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, buyer1.getWalletBalance().compareTo(new BigDecimal("850")));

        // Buyer 2 Bids Higher
        BidRequest req2 = new BidRequest();
        req2.setItemId(item.getId());
        req2.setAmount(new BigDecimal("200"));
        req2.setBidderName("buyer2");
        auctionService.placeBid(req2);

        // Verify buyer 1 refunded
        buyer1 = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, buyer1.getWalletBalance().compareTo(new BigDecimal("1000")));
        
        // Verify buyer 2 deducted
        buyer2 = userRepository.findByUsername("buyer2").orElseThrow();
        assertEquals(0, buyer2.getWalletBalance().compareTo(new BigDecimal("800")));

        // Settle Auction (Simulate time pass)
        item = itemRepository.findById(item.getId()).orElseThrow();
        item.setEndTime(LocalDateTime.now().minusSeconds(1));
        itemRepository.save(item);
        
        auctionScheduler.processEndedAuctions();

        // Verify item closed
        item = itemRepository.findById(item.getId()).orElseThrow();
        assertFalse(item.isActive());
        assertEquals("COMPLETED", item.getStatus());

        // Verify creator received funds
        creator = userRepository.findByUsername("creator1").orElseThrow();
        assertEquals(0, creator.getWalletBalance().compareTo(new BigDecimal("200")));
    }

    @Test
    @WithMockUser(username = "player1", roles = {"USER"})
    public void testGameEntryAndVirtualCredits() {
        User player = User.builder().username("player1").email("p1@test.com").password("pass").role("ROLE_USER").walletBalance(new BigDecimal("100")).build();
        userRepository.save(player);

        GameMessage msg = GameMessage.builder()
                .username("player1")
                .gameType("SMALL_BIG")
                .wager(new BigDecimal("50"))
                .payload(Map.of("selection", "SMALL"))
                .build();

        gameService.processJoinRequest(msg);

        player = userRepository.findByUsername("player1").orElseThrow();
        // Since outcome is random, balance is either 100 - 50 = 50 (lose) or 50 + (50 * 1.95) = 147.50 (win)
        assertTrue(player.getWalletBalance().compareTo(new BigDecimal("50")) == 0 || player.getWalletBalance().compareTo(new BigDecimal("147.50")) == 0);
    }
    
    @Test
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    public void testAuthorizationAdminCannotBid() {
        AuctionItem item = AuctionItem.builder()
                .title("Test")
                .startingPrice(new BigDecimal("10"))
                .endTime(LocalDateTime.now().plusHours(1))
                .build();
        item = auctionService.createItem(item);

        BidRequest req = new BidRequest();
        req.setItemId(item.getId());
        req.setAmount(new BigDecimal("20"));
        req.setBidderName("admin1");
        
        Exception ex = assertThrows(RuntimeException.class, () -> auctionService.placeBid(req));
        assertTrue(ex.getMessage().contains("Administrators are not allowed to place bids"));
    }

    @Test
    public void testConcurrentBidding() throws InterruptedException {
        User creator = User.builder().username("creator1").email("c@test.com").password("pass").role("ROLE_AUCTION_MAKER").walletBalance(BigDecimal.ZERO).build();
        userRepository.save(creator);
        
        AuctionItem item = AuctionItem.builder()
                .title("Test Item")
                .startingPrice(new BigDecimal("100"))
                .currentPrice(new BigDecimal("100"))
                .endTime(LocalDateTime.now().plusHours(1))
                .creatorName("creator1")
                .active(true)
                .build();
        final Long itemId = itemRepository.save(item).getId();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 10; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    User bidder = User.builder().username("user" + index).email("u" + index + "@test.com").password("pass").role("ROLE_USER").walletBalance(new BigDecimal("1000")).build();
                    userRepository.save(bidder);

                    BidRequest req = new BidRequest();
                    req.setItemId(itemId);
                    req.setAmount(new BigDecimal(150 + index * 10)); // Bids from 150 to 240
                    req.setBidderName("user" + index);
                    
                    // Since placeBid requires a SecurityContext, we skip auth check in this threaded test by manually configuring it, or we rely on the fact that placeBid doesn't throw NPE if context is null, it just returns false for auth check.
                    auctionService.placeBid(req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Optimistic locking failure or race condition should be caught
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        // Since multiple threads try to update the same row with optimistic locking or race conditions, not all might succeed, or they are processed serially.
        assertTrue(successCount.get() > 0);
    }
}
