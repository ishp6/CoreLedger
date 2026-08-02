package com.coreledger.controller;

import com.coreledger.dto.AccountResponse;
import com.coreledger.dto.CreateAccountRequest;
import com.coreledger.entity.LedgerEntry;
import com.coreledger.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getAccount(accountNumber));
    }

    @GetMapping("/{accountNumber}/statement")
    public ResponseEntity<List<Map<String, Object>>> getStatement(@PathVariable String accountNumber) {
        List<LedgerEntry> entries = accountService.getStatement(accountNumber);
        List<Map<String, Object>> body = entries.stream().map(e -> Map.<String, Object>of(
                "entryType", e.getEntryType().name(),
                "amount", e.getAmount(),
                "balanceAfter", e.getBalanceAfter(),
                "transactionId", e.getTransaction().getId(),
                "createdAt", e.getCreatedAt().toString()
        )).collect(Collectors.toList());
        return ResponseEntity.ok(body);
    }
}
