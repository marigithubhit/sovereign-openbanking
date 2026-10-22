package com.intellect.sovereign.model;

import java.time.Instant;

public record SweepResponse(
        String transactionId,
        String status,
        String iso20022EndToEndId,
        Instant timestamp
) {}