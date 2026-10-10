package com.auction.bidding.repository;

import com.auction.bidding.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUsernameOrderByTimestampDesc(String username);
    List<Transaction> findAllByOrderByTimestampDesc();
    boolean existsByTypeAndDescription(String type, String description);
    java.util.Optional<Transaction> findByReferenceIdAndType(Long referenceId, String type);
}
