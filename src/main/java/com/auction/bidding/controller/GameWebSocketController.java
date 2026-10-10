package com.auction.bidding.controller;

import com.auction.bidding.dto.GameMessage;
import com.auction.bidding.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final GameService gameService;

    @MessageMapping("/game.join")
    public void joinGame(@Payload GameMessage message, Principal principal) {
        if (principal != null) {
            message.setUsername(principal.getName());
            gameService.processJoinRequest(message);
        }
    }

    @MessageMapping("/game.action")
    public void handleGameAction(@Payload GameMessage message, Principal principal) {
        if (principal != null) {
            message.setUsername(principal.getName());
            gameService.processGameAction(message);
        }
    }
}
