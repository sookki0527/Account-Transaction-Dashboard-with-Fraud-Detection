package org.example.dto;


import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DepositRequest {
    private Long id;
    private String userId;
    private Long AccountId;
    private BigDecimal amount;
    private String type;
    private LocalDateTime date;
}
