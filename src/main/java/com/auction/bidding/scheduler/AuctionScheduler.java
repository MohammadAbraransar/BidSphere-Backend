package com.auction.bidding.scheduler;

import com.auction.bidding.model.AuctionItem;
import com.auction.bidding.model.Bid;
import com.auction.bidding.model.Transaction;
import com.auction.bidding.model.User;
import com.auction.bidding.repository.AuctionItemRepository;
import com.auction.bidding.repository.BidRepository;
import com.auction.bidding.repository.TransactionRepository;
import com.auction.bidding.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private final AuctionItemRepository itemRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    @Scheduled(fixedRate = 5000)
    @Transactional
    public void processEndedAuctions() {
        List<AuctionItem> activeAuctions = itemRepository.findByActiveTrue();
        LocalDateTime now = LocalDateTime.now();

        for (AuctionItem item : activeAuctions) {
            if (item.getEndTime().isBefore(now)) {
                item.setActive(false);
                item.setStatus("COMPLETED");

                List<Bid> bids = bidRepository.findByItemIdOrderByBidAmountDesc(item.getId());
                if (!bids.isEmpty()) {
                    Bid highestBid = bids.get(0);
                    
                    // The highest bidder's credits were already deducted when they placed the bid.
                    // We just need to give those credits to the auction creator.
                    if (item.getCreatorName() != null) {
                        User creator = userRepository.findByUsername(item.getCreatorName()).orElse(null);
                        if (creator != null) {
                            creator.setWalletBalance(creator.getWalletBalance().add(highestBid.getBidAmount()));
                            userRepository.save(creator);

                            Transaction payoutTx = Transaction.builder()
                                    .username(creator.getUsername())
                                    .type("AUCTION_SOLD")
                                    .referenceId(item.getId())
                                    .amount(highestBid.getBidAmount())
                                    .status("COMPLETED")
                                    .timestamp(now)
                                    .description("Payout for completed auction #" + item.getId())
                                    .build();
                            transactionRepository.save(payoutTx);
                        }
                    }
                } else {
                    item.setStatus("CLOSED_NO_BIDS");
                }
                itemRepository.save(item);
            }
        }
    }
}
