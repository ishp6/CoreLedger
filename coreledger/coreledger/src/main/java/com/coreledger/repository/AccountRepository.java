package com.coreledger.repository;

import com.coreledger.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * SELECT ... FOR UPDATE. Row-locks the account for the duration of the
     * enclosing transaction so a second concurrent transfer touching the same
     * account has to wait, rather than reading a stale balance and overwriting
     * this transaction's update (the "lost update" problem).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
