package com.coreledger.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
        Long transactionId,
        String idempotencyKey,
        String status,
        String fromAccountNumber,
        String toAccountNumber,
        BigDecimal amount,
        BigDecimal fromAccountBalanceAfter,
        BigDecimal toAccountBalanceAfter,
        Instant createdAt
) {
}
