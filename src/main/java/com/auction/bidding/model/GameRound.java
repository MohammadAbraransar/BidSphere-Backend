package com.auction.bidding.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_rounds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String gameType; // e.g., SMALL_BIG

    @Column(nullable = false)
    private String status; // OPEN, CLOSED, SETTLED

    private LocalDateTime openTime;
    private LocalDateTime closeTime;

    private String result; // The server-generated result

    @Column
    private Integer resultNumber; // Exact server-generated number for Small & Big
}
