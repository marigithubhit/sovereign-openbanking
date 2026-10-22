package com.intellect.sovereign.service;

import com.intellect.sovereign.controller.EmachOpenBankingGatewayController;
import com.intellect.sovereign.model.*;
import com.intellect.sovereign.model.DecisionResult.AgentAuditTrace;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PurpleFabricOrchestratorService {

    private final EmachOpenBankingGatewayController apiGateway;
    private final AuditStreamService auditStreamService;

    private static final BigDecimal OPERATIONAL_BUFFER = new BigDecimal("25000.00");
    private static final BigDecimal AUTONOMOUS_LIMIT = new BigDecimal("50000.00");

    private final Map<String, DecisionResult> pendingApprovalStore = new ConcurrentHashMap<>();

    public PurpleFabricOrchestratorService(EmachOpenBankingGatewayController apiGateway, AuditStreamService auditStreamService) {
        this.apiGateway = apiGateway;
        this.auditStreamService = auditStreamService;
    }

    public DecisionResult processAutonomousLiquiditySweep(String customerId) {
        List<AgentAuditTrace> auditLog = new ArrayList<>();
        List<BankAccount> accounts = Objects.requireNonNull(apiGateway.getConsolidatedAccounts(customerId).getBody());

        // 1. Liquidity Evaluator Agent
        BankAccount checkingAcc = accounts.stream()
                .filter(a -> a.type() == AccountType.CHECKING)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Primary operational account not found"));

        BigDecimal idleCash = checkingAcc.balance().subtract(OPERATIONAL_BUFFER);

        emitAndRecordTrace(auditLog, "LiquidityEvaluator", "Liquidity Evaluator Agent", "ANALYSIS",
                "Inspecting operational account at " + checkingAcc.bankName() + " (Balance: $" + checkingAcc.balance() + "). Buffer requirement: $" + OPERATIONAL_BUFFER);

        if (idleCash.compareTo(BigDecimal.ZERO) <= 0) {
            emitAndRecordTrace(auditLog, "LiquidityEvaluator", "Liquidity Evaluator Agent", "DECISION",
                    "Checking balance is within safety buffer. Zero surplus liquidity detected.");
            return new DecisionResult(UUID.randomUUID().toString(), customerId, BigDecimal.ZERO,
                    checkingAcc.bankId(), "NONE", BigDecimal.ZERO, ExecutionStatus.REJECTED_NO_IDLE_CASH, auditLog, Instant.now());
        }

        emitAndRecordTrace(auditLog, "LiquidityEvaluator", "Liquidity Evaluator Agent", "IDENTIFICATION",
                "Identified $" + idleCash + " in uninvested idle liquidity above operational reserve threshold.");

        // 2. Yield & APY Optimizer Agent
        BankAccount targetAcc = accounts.stream()
                .filter(a -> a.type() != AccountType.CHECKING)
                .max(Comparator.comparing(BankAccount::apyPercentage))
                .orElseThrow(() -> new IllegalStateException("No high-yielding target account available"));

        BigDecimal apySpread = targetAcc.apyPercentage().subtract(checkingAcc.apyPercentage());
        BigDecimal projectedAnnualGain = idleCash.multiply(apySpread).setScale(2, RoundingMode.HALF_UP);

        emitAndRecordTrace(auditLog, "YieldOptimizer", "Yield Optimizer Agent", "STRATEGY",
                "Selected target: " + targetAcc.bankName() + " (" + targetAcc.type() + " @ " + targetAcc.apyPercentage().multiply(new BigDecimal("100")) + "% APY).");

        emitAndRecordTrace(auditLog, "YieldOptimizer", "Yield Optimizer Agent", "YIELD_CALCULATION",
                "Calculated APY spread gain: +$" + projectedAnnualGain + "/year additional revenue generated.");

        // 3. Governance Guardrail Agent
        ExecutionStatus status;
        String executionId = UUID.randomUUID().toString();

        if (idleCash.compareTo(AUTONOMOUS_LIMIT) > 0) {
            status = ExecutionStatus.PENDING_MAKER_CHECKER;
            emitAndRecordTrace(auditLog, "GovernanceGuardrail", "Governance Guardrail Agent", "RISK_CHECK",
                    "Sweep amount ($" + idleCash + ") exceeds autonomous threshold ($" + AUTONOMOUS_LIMIT + "). Escalated to Maker-Checker human authorization queue.");
        } else {
            status = ExecutionStatus.AUTONOMOUS_EXECUTED;
            emitAndRecordTrace(auditLog, "GovernanceGuardrail", "Governance Guardrail Agent", "RISK_CHECK",
                    "Sweep amount ($" + idleCash + ") is within safe limits ($" + AUTONOMOUS_LIMIT + "). Approved for execution.");

            apiGateway.executePaymentSweep(new SweepRequest(checkingAcc.bankId(), checkingAcc.accountId(), targetAcc.bankId(), targetAcc.accountId(), idleCash, "USD"));
        }

        DecisionResult result = new DecisionResult(executionId, customerId, idleCash, checkingAcc.bankId(), targetAcc.bankId(), projectedAnnualGain, status, auditLog, Instant.now());

        if (status == ExecutionStatus.PENDING_MAKER_CHECKER) {
            pendingApprovalStore.put(executionId, result);
        }

        return result;
    }

    public DecisionResult confirmMakerCheckerApproval(String executionId, boolean approved) {
        DecisionResult existing = pendingApprovalStore.get(executionId);
        if (existing == null) throw new IllegalArgumentException("Execution ID not found or already processed");

        List<AgentAuditTrace> updatedAuditLog = new ArrayList<>(existing.auditTraces());
        ExecutionStatus newStatus;

        if (approved) {
            newStatus = ExecutionStatus.APPROVED_BY_MAKER_CHECKER;
            emitAndRecordTrace(updatedAuditLog, "GovernanceGuardrail", "Governance Guardrail Agent", "HUMAN_APPROVAL",
                    "Maker-Checker authorization granted by Corporate Treasurer. Executing payment sweep.");

            apiGateway.executePaymentSweep(new SweepRequest(existing.sourceBank(), "ACC-UAE-001", existing.targetBank(), "ACC-FAB-003", existing.sweepAmount(), "USD"));
        } else {
            newStatus = ExecutionStatus.REJECTED_MAKER_CHECKER;
            emitAndRecordTrace(updatedAuditLog, "GovernanceGuardrail", "Governance Guardrail Agent", "HUMAN_REJECTION",
                    "Transaction rejected during Maker-Checker verification.");
        }

        DecisionResult updatedResult = new DecisionResult(
                existing.executionId(), existing.customerId(), existing.sweepAmount(),
                existing.sourceBank(), existing.targetBank(), existing.calculatedAnnualGainUsd(),
                newStatus, updatedAuditLog, Instant.now()
        );

        pendingApprovalStore.remove(executionId);
        return updatedResult;
    }

    public Collection<DecisionResult> getPendingApprovals() {
        return pendingApprovalStore.values();
    }

    private void emitAndRecordTrace(List<AgentAuditTrace> auditLog, String agentId, String role, String phase, String reasoning) {
        AgentAuditTrace trace = new AgentAuditTrace(agentId, role, phase, reasoning, Instant.now());
        auditLog.add(trace);
        auditStreamService.broadcastTrace(trace);
    }
}