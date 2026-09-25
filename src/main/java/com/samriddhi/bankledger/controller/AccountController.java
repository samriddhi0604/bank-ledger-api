package com.samriddhi.bankledger.controller;

import com.samriddhi.bankledger.dto.CreateAccountRequest;
import com.samriddhi.bankledger.dto.TransactionRequest;
import com.samriddhi.bankledger.model.Account;
import com.samriddhi.bankledger.model.Transaction;
import com.samriddhi.bankledger.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<Account> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        Account created = accountService.createAccount(request.getOwnerName(), request.getInitialBalance());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<Account> getAllAccounts() {
        return accountService.getAllAccounts();
    }

    @GetMapping("/{id}")
    public Account getAccount(@PathVariable Long id) {
        return accountService.getAccount(id);
    }

    @PostMapping("/{id}/deposit")
    public Account deposit(@PathVariable Long id, @Valid @RequestBody TransactionRequest request) {
        return accountService.deposit(id, request.getAmount());
    }

    @PostMapping("/{id}/withdraw")
    public Account withdraw(@PathVariable Long id, @Valid @RequestBody TransactionRequest request) {
        return accountService.withdraw(id, request.getAmount());
    }

    @GetMapping("/{id}/transactions")
    public List<Transaction> getTransactions(@PathVariable Long id) {
        return accountService.getTransactionHistory(id);
    }
}
