package com.auction.bidding.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_round_id", nullable = false)
    private GameRound gameRound;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String selection; // SMALL or BIG

    private String outcome; // WIN, LOSE, PENDING

    @Column(nullable = false)
    private LocalDateTime timestamp;
}
