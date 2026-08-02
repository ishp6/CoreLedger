package com.coreledger.service;

import com.coreledger.dto.AccountResponse;
import com.coreledger.dto.CreateAccountRequest;
import com.coreledger.entity.Account;
import com.coreledger.entity.LedgerEntry;
import com.coreledger.exception.AccountNotFoundException;
import com.coreledger.repository.AccountRepository;
import com.coreledger.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private static final SecureRandom RANDOM = new SecureRandom();

    public AccountService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        String accountNumber = generateAccountNumber();
        String currency = (request.currency() == null || request.currency().isBlank())
                ? "INR" : request.currency();

        Account account = new Account(accountNumber, request.ownerName(), request.openingBalance(), currency);
        accountRepository.save(account);
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getStatement(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        return ledgerEntryRepository.findByAccountOrderByCreatedAtAsc(account);
    }

    private String generateAccountNumber() {
        // 12-digit numeric account number, e.g. 483920175610
        StringBuilder sb = new StringBuilder("CL");
        for (int i = 0; i < 10; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private AccountResponse toResponse(Account a) {
        return new AccountResponse(
                a.getId(), a.getAccountNumber(), a.getOwnerName(),
                a.getBalance(), a.getCurrency(), a.getStatus().name(), a.getCreatedAt()
        );
    }
}
