package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.AccountDto;
import org.example.dto.TransferRequest;
import org.example.entity.Account;
import org.example.repository.AccountRepository;
import org.example.service.AccountService;
import org.example.service.KafkaProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
public class AccountController {


    private final KafkaProducer kafkaProducer;
    private AccountService accountService;
    private AccountRepository accountRepository;

    public AccountController(AccountService accountService, AccountRepository accountRepository, KafkaProducer kafkaProducer) {
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.kafkaProducer = kafkaProducer;
    }

    @GetMapping("/{userId}")
    public List<AccountDto> getAccountsForUser(@PathVariable("userId") String userId) {
        return accountService.getAccountsByUserId(userId);
    }

    @GetMapping("/account/{accountId}")
    public AccountDto getAccountById(@PathVariable("accountId") Long accountId){
        return accountService.getAccountById(accountId);
    }

    @PutMapping("/account/{accountId}/deposit")
    public ResponseEntity<Void> increaseAccount(
            @PathVariable("accountId") Long accountId,
            @RequestBody TransferRequest request
    ) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        account.setBalance(request.getAmount().add(account.getBalance()));
        accountRepository.save(account);
        kafkaProducer.sendTransferNotification(request);
        return ResponseEntity.ok().build();

    }

    @PutMapping("/account/{accountId}/withdraw")
    public ResponseEntity<Void> decreaseAccount(
            @PathVariable("accountId") Long accountId,
            @RequestBody TransferRequest request
    ) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        account.setBalance(account.getBalance().subtract(request.getAmount()));
        accountRepository.save(account);
        kafkaProducer.sendTransferNotification(request);
        return ResponseEntity.ok().build();

    }

}