package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name= "account")
@Entity
public class Account {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long accountId;

    @Column(name = "user_id", nullable = false)
    private String userId;


    @Column(name = "account_type")
    private String accountType;

    @Column(name = "account_number", unique = true)
    private String accountNumber;

    @Column(name = "balance", precision = 30, scale = 2)
    private BigDecimal balance;

    @Column(name = "currency")
    private String currency;
}