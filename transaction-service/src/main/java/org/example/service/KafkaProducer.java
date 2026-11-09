package org.example.service;

import org.example.dto.TransferEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    public KafkaProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendTransferNotification(TransferEvent event){
        kafkaTemplate.send("transfer-topic", event);
    }

    public void sendDepositNotification(TransferEvent event){
        kafkaTemplate.send("account-topic", event);
    }
}
