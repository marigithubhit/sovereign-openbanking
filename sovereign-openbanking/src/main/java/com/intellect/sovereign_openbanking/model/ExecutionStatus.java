package com.intellect.sovereign.model;

public enum ExecutionStatus {
    AUTONOMOUS_EXECUTED,
    PENDING_MAKER_CHECKER,
    APPROVED_BY_MAKER_CHECKER,
    REJECTED_MAKER_CHECKER,
    REJECTED_NO_IDLE_CASH
}