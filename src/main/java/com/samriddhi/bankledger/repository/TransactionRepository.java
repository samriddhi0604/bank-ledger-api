package com.samriddhi.bankledger.repository;

import com.samriddhi.bankledger.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountIdOrderByTimestampDesc(Long accountId);
}
