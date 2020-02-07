package org.niis.xroad.restapi.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @MessageMapping("/test")
    @SendTo("/topic/public")
    public String answer(String answerString) {
        return answerString;
    }

    public void sendString(String answerString) {
        simpMessagingTemplate.convertAndSend("/topic/public", answerString);
    }
}
