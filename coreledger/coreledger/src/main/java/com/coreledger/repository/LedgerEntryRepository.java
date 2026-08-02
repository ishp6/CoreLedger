package com.coreledger.repository;

import com.coreledger.entity.Account;
import com.coreledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByAccountOrderByCreatedAtAsc(Account account);
}
