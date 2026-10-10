package com.auction.bidding.repository;

import com.auction.bidding.model.Bid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {
    List<Bid> findByItemIdOrderByBidAmountDesc(Long itemId);
    List<Bid> findAllByOrderByBidTimeDesc();
}
