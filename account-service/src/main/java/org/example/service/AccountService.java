package org.example.service;

import org.example.dto.AccountDto;
import org.example.entity.Account;
import org.example.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.List;


@Service
public class AccountService {

    private AccountRepository accountRepository;
    @Autowired
    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public List<AccountDto> getAccountsByUserId(String userId) {
        List<Account> accounts = accountRepository.findAccountsByUserId(userId);
        return accounts.stream().map(account -> new AccountDto(account.getAccountId(), account.getUserId(), account.getAccountType(), account.getAccountNumber(),
                account.getBalance(), account.getCurrency())).toList();
    }

    public AccountDto getAccountById(Long accountId){
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        return new AccountDto(account.getAccountId(), account.getUserId(), account.getAccountType(), account.getAccountNumber(), account.getBalance(),
                account.getCurrency());
    }
}