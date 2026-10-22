package com.intellect.sovereign.model;

import java.math.BigDecimal;

public record SweepRequest(
        String sourceBankId,
        String sourceAccountId,
        String targetBankId,
        String targetAccountId,
        BigDecimal amount,
        String currency
) {}