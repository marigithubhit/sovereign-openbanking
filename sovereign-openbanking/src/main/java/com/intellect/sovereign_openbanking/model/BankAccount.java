package com.intellect.sovereign.model;

import java.math.BigDecimal;

public record BankAccount(
        String bankId,
        String bankName,
        String accountId,
        AccountType type,
        BigDecimal balance,
        BigDecimal apyPercentage
) {}