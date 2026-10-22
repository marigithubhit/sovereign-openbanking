package com.intellect.sovereign.controller;

import com.intellect.sovereign.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/emach/open-banking")
@CrossOrigin(origins = "*")
public class EmachOpenBankingGatewayController {

    private final Map<String, List<BankAccount>> mockAccountLedger = new ConcurrentHashMap<>();

    public EmachOpenBankingGatewayController() {
        mockAccountLedger.put("CUST_IN_9901", new ArrayList<>(List.of(
                new BankAccount("ABC_UAE", "ABC Middle East", "ACC-UAE-001", AccountType.CHECKING, new BigDecimal("120000.00"), new BigDecimal("0.005")),
                new BankAccount("EFG", "EFG", "ACC-ENBD-002", AccountType.SAVINGS, new BigDecimal("25000.00"), new BigDecimal("0.042")),
                new BankAccount("HIJ", "HIJ", "ACC-FAB-003", AccountType.MONEY_MARKET, new BigDecimal("60000.00"), new BigDecimal("0.051"))
        )));
    }

    @GetMapping("/customers/{customerId}/accounts")
    public ResponseEntity<List<BankAccount>> getConsolidatedAccounts(@PathVariable String customerId) {
        List<BankAccount> accounts = mockAccountLedger.get(customerId);
        if (accounts == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return ResponseEntity.ok(accounts);
    }

    @PostMapping("/transactions/sweep")
    public ResponseEntity<SweepResponse> executePaymentSweep(@RequestBody SweepRequest request) {
        List<BankAccount> accounts = mockAccountLedger.get("CUST_IN_9901");
        if (accounts != null) {
            for (int i = 0; i < accounts.size(); i++) {
                BankAccount acc = accounts.get(i);
                if (acc.bankId().equals(request.sourceBankId())) {
                    accounts.set(i, new BankAccount(acc.bankId(), acc.bankName(), acc.accountId(), acc.type(), acc.balance().subtract(request.amount()), acc.apyPercentage()));
                } else if (acc.bankId().equals(request.targetBankId())) {
                    accounts.set(i, new BankAccount(acc.bankId(), acc.bankName(), acc.accountId(), acc.type(), acc.balance().add(request.amount()), acc.apyPercentage()));
                }
            }
        }

        String endToEndId = "E2E-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        return ResponseEntity.ok(new SweepResponse(
                "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "SETTLED", endToEndId, Instant.now()
        ));
    }
}