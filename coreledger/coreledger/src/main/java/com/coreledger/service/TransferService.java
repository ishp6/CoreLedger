package com.coreledger.service;

import com.coreledger.dto.TransferRequest;
import com.coreledger.dto.TransferResponse;
import com.coreledger.entity.*;
import com.coreledger.exception.*;
import com.coreledger.repository.AccountRepository;
import com.coreledger.repository.LedgerEntryRepository;
import com.coreledger.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public TransferService(AccountRepository accountRepository,
                            TransactionRepository transactionRepository,
                            LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Transfers `amount` from one account to another.
     *
     * Three separate safety nets make this safe to call concurrently and safe
     * to retry over an unreliable network:
     *
     * 1. IDEMPOTENCY: the caller supplies an idempotencyKey. If a Transaction
     *    with that key already exists, we return its recorded result instead
     *    of moving money again. This is what makes it safe for a client to
     *    retry a request it's not sure succeeded (e.g. after a timeout).
     *
     * 2. DEADLOCK-FREE LOCKING: when two transfers touch the same pair of
     *    accounts in opposite directions (A->B and B->A) at the same time,
     *    always acquiring row locks in a fixed order (lower account id first)
     *    prevents the classic deadlock where each transaction holds one lock
     *    and waits for the other.
     *
     * 3. PESSIMISTIC LOCKING: SELECT ... FOR UPDATE on both accounts means a
     *    second concurrent transfer against either account blocks until this
     *    one commits, so nobody reads a stale balance and overwrites this
     *    update (the "lost update" problem).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResponse transfer(TransferRequest request) {

        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        if (request.fromAccountNumber().equals(request.toAccountNumber())) {
            throw new SameAccountTransferException();
        }

        Account fromLookup = accountRepository.findByAccountNumber(request.fromAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(request.fromAccountNumber()));
        Account toLookup = accountRepository.findByAccountNumber(request.toAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(request.toAccountNumber()));

        // Lock both rows in a consistent order (by primary key) regardless of
        // transfer direction, so concurrent A->B and B->A transfers can't deadlock.
        Long firstId = Math.min(fromLookup.getId(), toLookup.getId());
        Long secondId = Math.max(fromLookup.getId(), toLookup.getId());

        Account first = accountRepository.findByIdForUpdate(firstId).orElseThrow();
        Account second = accountRepository.findByIdForUpdate(secondId).orElseThrow();

        Account from = fromLookup.getId().equals(first.getId()) ? first : second;
        Account to = fromLookup.getId().equals(first.getId()) ? second : first;

        if (from.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(from.getAccountNumber());
        }
        if (to.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(to.getAccountNumber());
        }

        BigDecimal amount = request.amount();

        Transaction transaction = new Transaction(request.idempotencyKey(), from, to, amount);

        if (from.getBalance().compareTo(amount) < 0) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason("Insufficient funds");
            transactionRepository.save(transaction);
            throw new InsufficientFundsException(from.getAccountNumber());
        }

        // Move the money.
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        transaction.setStatus(TransactionStatus.SUCCESS);
        transactionRepository.save(transaction);

        // Double-entry ledger: one debit, one credit, same amount, always in balance.
        ledgerEntryRepository.save(new LedgerEntry(transaction, from, EntryType.DEBIT, amount, from.getBalance()));
        ledgerEntryRepository.save(new LedgerEntry(transaction, to, EntryType.CREDIT, amount, to.getBalance()));

        accountRepository.save(from);
        accountRepository.save(to);

        return toResponse(transaction);
    }

    private TransferResponse toResponse(Transaction t) {
        return new TransferResponse(
                t.getId(),
                t.getIdempotencyKey(),
                t.getStatus().name(),
                t.getFromAccount().getAccountNumber(),
                t.getToAccount().getAccountNumber(),
                t.getAmount(),
                t.getFromAccount().getBalance(),
                t.getToAccount().getBalance(),
                t.getCreatedAt()
        );
    }
}
