package com.samriddhi.bankledger.repository;

import com.samriddhi.bankledger.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
}
