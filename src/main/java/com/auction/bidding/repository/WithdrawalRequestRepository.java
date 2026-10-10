package com.auction.bidding.repository;

import com.auction.bidding.model.WithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {
    List<WithdrawalRequest> findByUsernameOrderByRequestTimeDesc(String username);
    List<WithdrawalRequest> findByStatusOrderByRequestTimeAsc(String status);
}
