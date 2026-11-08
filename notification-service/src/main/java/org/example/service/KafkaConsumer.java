package org.example.service;

import org.example.dto.NotificationResponse;

import org.example.dto.TransferRequest;
import org.example.dto.DepositRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;


@Service
public class KafkaConsumer {
    @Autowired
    private SimpMessagingTemplate  messagingTemplate;

    public KafkaConsumer(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(topics = "account-topic", groupId = "notif-group")
    public void listen(DepositRequest event) {
        String eventType = event.getType() == null
                ? ""
                : event.getType().trim().toUpperCase(Locale.ROOT);
        String typed = switch (eventType) {
            case "DEPOSIT" -> "deposited";
            case "WITHDRAW", "WITHDRAWAL" -> "withdrawn";
            default -> throw new IllegalArgumentException(
                    "Unsupported account event type: " + event.getType());
        };
        NotificationResponse notif = new NotificationResponse(
                "account",
                "✅ $" + event.getAmount() + " " + typed + ".",
                "Just Now"
        );
        System.out.println("✅ Consumed Kafka message: " + notif);

        messagingTemplate.convertAndSendToUser(event.getUserId(), "/topic/notifications",notif);
    }

    @KafkaListener(topics = "transfer-topic", groupId = "notif-group")
    public void listening(TransferRequest event){
        NotificationResponse notif = new NotificationResponse(
                "account",
                "$" + String.valueOf(event.getAmount()) + " is transferred" + " from account " + String.valueOf(event.getFromAccountId()) + " to account " + String.valueOf(event.getToAccountId()),
                "Just Now"
        );
        System.out.println("✅ Consumed Kafka message: " + notif);

        messagingTemplate.convertAndSendToUser(event.getUserId(), "/topic/notifications",notif);
    }


    @KafkaListener(topics = "ai-topic", groupId = "notif-group")
    public void listen(String message){
        NotificationResponse notif = new NotificationResponse(
                "ai",
                message,
                "Just Now"
        );
        System.out.println("✅ Consumed Kafka message: " + notif);

        messagingTemplate.convertAndSend("/topic/notifications-ai", notif);
    }
}
