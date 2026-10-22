package com.intellect.sovereign.controller;

import com.intellect.sovereign.model.DecisionResult;
import com.intellect.sovereign.service.AuditStreamService;
import com.intellect.sovereign.service.PurpleFabricOrchestratorService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;

@RestController
@RequestMapping("/api/v1/sovereign")
@CrossOrigin(origins = "*")
public class SovereignEngineController {

    private final PurpleFabricOrchestratorService orchestratorService;
    private final AuditStreamService auditStreamService;

    public SovereignEngineController(PurpleFabricOrchestratorService orchestratorService, AuditStreamService auditStreamService) {
        this.orchestratorService = orchestratorService;
        this.auditStreamService = auditStreamService;
    }

    @PostMapping("/sweeps/evaluate/{customerId}")
    public ResponseEntity<DecisionResult> triggerSweepEvaluation(@PathVariable String customerId) {
        return ResponseEntity.ok(orchestratorService.processAutonomousLiquiditySweep(customerId));
    }

    @GetMapping("/sweeps/pending")
    public ResponseEntity<Collection<DecisionResult>> getPendingApprovals() {
        return ResponseEntity.ok(orchestratorService.getPendingApprovals());
    }

    @PostMapping("/sweeps/confirm/{executionId}")
    public ResponseEntity<DecisionResult> confirmApproval(@PathVariable String executionId, @RequestParam boolean approved) {
        return ResponseEntity.ok(orchestratorService.confirmMakerCheckerApproval(executionId, approved));
    }

    @GetMapping(value = "/stream-audit", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAuditLog() {
        return auditStreamService.subscribe();
    }
}