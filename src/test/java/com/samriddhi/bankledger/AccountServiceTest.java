package com.samriddhi.bankledger;

import com.samriddhi.bankledger.exception.AccountNotFoundException;
import com.samriddhi.bankledger.exception.InsufficientFundsException;
import com.samriddhi.bankledger.model.Account;
import com.samriddhi.bankledger.model.Transaction;
import com.samriddhi.bankledger.model.TransactionType;
import com.samriddhi.bankledger.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void createAccountSetsInitialBalance() {
        Account account = accountService.createAccount("Asha", money("100.00"));

        assertNotNull(account.getId());
        assertNotNull(account.getCreatedAt());
        assertEquals("Asha", account.getOwnerName());
        assertEquals(0, money("100.00").compareTo(account.getBalance()));
    }

    @Test
    void depositIncreasesBalance() {
        Account account = accountService.createAccount("Asha", money("100.00"));

        Account updated = accountService.deposit(account.getId(), money("50.25"));

        assertEquals(0, money("150.25").compareTo(updated.getBalance()));
        assertEquals(0, money("150.25").compareTo(accountService.getAccount(account.getId()).getBalance()));
    }

    @Test
    void withdrawDecreasesBalance() {
        Account account = accountService.createAccount("Asha", money("100.00"));

        Account updated = accountService.withdraw(account.getId(), money("30.00"));

        assertEquals(0, money("70.00").compareTo(updated.getBalance()));
    }

    @Test
    void withdrawMoreThanBalanceThrowsAndLeavesBalanceUnchanged() {
        Account account = accountService.createAccount("Asha", money("100.00"));

        assertThrows(InsufficientFundsException.class,
                () -> accountService.withdraw(account.getId(), money("100.01")));

        assertEquals(0, money("100.00").compareTo(accountService.getAccount(account.getId()).getBalance()));
        assertTrue(accountService.getTransactionHistory(account.getId()).isEmpty());
    }

    @Test
    void withdrawExactBalanceIsAllowed() {
        Account account = accountService.createAccount("Asha", money("100.00"));

        Account updated = accountService.withdraw(account.getId(), money("100.00"));

        assertEquals(0, BigDecimal.ZERO.compareTo(updated.getBalance()));
    }

    @Test
    void fetchingNonexistentAccountThrows() {
        assertThrows(AccountNotFoundException.class, () -> accountService.getAccount(999_999L));
    }

    @Test
    void transactionHistoryOfNonexistentAccountThrows() {
        assertThrows(AccountNotFoundException.class, () -> accountService.getTransactionHistory(999_999L));
    }

    @Test
    void transactionHistoryRecordsDepositAndWithdrawal() {
        Account account = accountService.createAccount("Asha", money("100.00"));
        accountService.deposit(account.getId(), money("50.00"));
        accountService.withdraw(account.getId(), money("20.00"));

        List<Transaction> history = accountService.getTransactionHistory(account.getId());

        assertEquals(2, history.size());
        Transaction deposit = history.stream()
                .filter(t -> t.getType() == TransactionType.DEPOSIT).findFirst().orElseThrow();
        Transaction withdrawal = history.stream()
                .filter(t -> t.getType() == TransactionType.WITHDRAWAL).findFirst().orElseThrow();
        assertEquals(0, money("50.00").compareTo(deposit.getAmount()));
        assertEquals(0, money("150.00").compareTo(deposit.getBalanceAfter()));
        assertEquals(0, money("20.00").compareTo(withdrawal.getAmount()));
        assertEquals(0, money("130.00").compareTo(withdrawal.getBalanceAfter()));
    }
}
