package com.samriddhi.bankledger.service;

import com.samriddhi.bankledger.exception.AccountNotFoundException;
import com.samriddhi.bankledger.exception.InsufficientFundsException;
import com.samriddhi.bankledger.model.Account;
import com.samriddhi.bankledger.model.Transaction;
import com.samriddhi.bankledger.model.TransactionType;
import com.samriddhi.bankledger.repository.AccountRepository;
import com.samriddhi.bankledger.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public Account createAccount(String ownerName, BigDecimal initialBalance) {
        return accountRepository.save(new Account(ownerName, initialBalance));
    }

    public Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    /**
     * Balance update and ledger entry are written in one transaction: if either fails,
     * both roll back, so the ledger can never drift from the actual balance.
     */
    @Transactional
    public Account deposit(Long accountId, BigDecimal amount) {
        Account account = getAccount(accountId);
        account.setBalance(account.getBalance().add(amount));
        Account saved = accountRepository.save(account);
        transactionRepository.save(
                new Transaction(accountId, TransactionType.DEPOSIT, amount, saved.getBalance()));
        return saved;
    }

    /**
     * The funds check and the balance update happen inside the same transaction, never
     * split across transaction boundaries.
     */
    @Transactional
    public Account withdraw(Long accountId, BigDecimal amount) {
        Account account = getAccount(accountId);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds: balance " + account.getBalance() + ", requested " + amount);
        }
        account.setBalance(account.getBalance().subtract(amount));
        Account saved = accountRepository.save(account);
        transactionRepository.save(
                new Transaction(accountId, TransactionType.WITHDRAWAL, amount, saved.getBalance()));
        return saved;
    }

    public List<Transaction> getTransactionHistory(Long accountId) {
        // 404 for unknown accounts instead of a silently empty list
        getAccount(accountId);
        return transactionRepository.findByAccountIdOrderByTimestampDesc(accountId);
    }
}
