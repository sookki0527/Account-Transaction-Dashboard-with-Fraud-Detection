package org.example.dto;


import java.math.BigDecimal;
import java.time.Instant;



public record TransferEvent(
        Long transactionId,
        String userId,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        Instant date
        
) {}

