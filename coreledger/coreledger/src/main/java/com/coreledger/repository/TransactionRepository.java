package com.coreledger.repository;

import com.coreledger.entity.Account;
import com.coreledger.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findByFromAccountOrToAccountOrderByCreatedAtDesc(Account from, Account to);
}
