package org.example.service;


import org.example.dto.TransferResponse;
import org.example.dto.TransferRequest;
import org.example.dto.UpdateBalanceRequest;

import org.springframework.kafka.annotation.KafkaListener;
import org.example.service.RedisTimeSeriesService;

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
    public void listen(TransferRequest event){
        TransferResponse notif = new TransferResponse(
                "transfer from account " + event.getFromAccountId() + " to account " + event.getToAccountId() + " completed successfully.",
                "💸 $" + event.getAmount() + " transferred."
        );
        System.out.println("✅ Consumed Kafka message: " + notif);

        redisTimeSeriesService.recordTransaction(event.getUserId(), String.valueOf(event.getTransactionId()), event.getAmount().doubleValue());
        System.out.println("✅ Recorded transaction in Redis Time Series for user: " + event.getUserId() + ", transactionId: " + event.getTransactionId() + ", amount: " + event.getAmount());
        
        redisTimeSeriesService.recordIncomingTransaction(
            event.getToAccountId(),
            event.getFromAccountId(),
            String.valueOf(event.getTransactionId()),
            event.getAmount().doubleValue()
        );
        System.out.println("✅ Recorded incoming transaction in Redis Time Series for receiving account: " + event.getToAccountId() + ", from account: " + event.getFromAccountId() + ", transactionId: " + event.getTransactionId() + ", amount: " + event.getAmount());
    }


}
