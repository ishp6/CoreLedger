package com.coreledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank(message = "ownerName is required")
        String ownerName,

        @NotNull(message = "openingBalance is required")
        @DecimalMin(value = "0.00", message = "openingBalance cannot be negative")
        BigDecimal openingBalance,

        String currency
) {
}
