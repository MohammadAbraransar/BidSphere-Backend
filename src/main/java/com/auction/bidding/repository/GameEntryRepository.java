package com.auction.bidding.repository;

import com.auction.bidding.model.GameEntry;
import com.auction.bidding.model.GameRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameEntryRepository extends JpaRepository<GameEntry, Long> {
    List<GameEntry> findByGameRound(GameRound round);
    List<GameEntry> findByUsernameOrderByTimestampDesc(String username);
}
