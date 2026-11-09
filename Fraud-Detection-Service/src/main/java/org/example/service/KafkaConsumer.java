package org.example.service;

import org.example.dto.TransferEvent;
import org.example.dto.TransferResponse;
import org.example.dto.TransferRequest;


import org.springframework.kafka.annotation.KafkaListener;


import org.springframework.stereotype.Service;


@Service
public class KafkaConsumer {
    

    private final RedisTimeSeriesService redisTimeSeriesService;

    public KafkaConsumer(RedisTimeSeriesService redisTimeSeriesService) {
        this.redisTimeSeriesService = redisTimeSeriesService;
       
    }

    
    @KafkaListener(
        topics = "transfer-topic",
        groupId = "fraud-detection-group"
    )
    public void listen(TransferEvent event) {

        if (event.transactionId() == null) {
            throw new IllegalArgumentException(
                "Missing transactionId in transfer event"
            );
        }

        TransferResponse notif = new TransferResponse(
            "transfer from account " + event.fromAccountId()
                + " to account " + event.toAccountId()
                + " completed successfully.",
            "💸 $" + event.amount() + " transferred."
        );

        System.out.println("✅ Consumed Kafka message: " + notif);

        String transactionId = event.transactionId().toString();

        redisTimeSeriesService.recordTransaction(
            event.userId(),
            transactionId,
            event.amount().doubleValue()
        );

        System.out.println(
            "✅ Recorded transaction in Redis for user: "
            + event.userId()
            + ", transactionId: " + transactionId
            + ", amount: " + event.amount()
        );

        redisTimeSeriesService.recordIncomingTransaction(
            event.toAccountId(),
            event.fromAccountId(),
            transactionId,
            event.amount().doubleValue()
        );

        System.out.println(
            "✅ Recorded incoming transaction in Redis for account: "
            + event.toAccountId()
            + ", from account: " + event.fromAccountId()
            + ", transactionId: " + transactionId
        );
}


}
