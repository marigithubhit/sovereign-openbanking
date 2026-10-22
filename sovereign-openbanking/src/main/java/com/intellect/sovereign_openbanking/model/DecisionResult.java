package com.intellect.sovereign.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DecisionResult(
        String executionId,
        String customerId,
        BigDecimal sweepAmount,
        String sourceBank,
        String targetBank,
        BigDecimal calculatedAnnualGainUsd,
        ExecutionStatus status,
        List<AgentAuditTrace> auditTraces,
        Instant executedAt
) {
    public record AgentAuditTrace(
            String agentId,
            String agentRole,
            String phase,
            String reasoning,
            Instant timestamp
    ) {}
}