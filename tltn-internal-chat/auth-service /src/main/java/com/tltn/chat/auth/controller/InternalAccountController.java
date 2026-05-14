package com.tltn.chat.auth.controller;

import com.tltn.chat.auth.dto.CreateAccountRequest;
import com.tltn.chat.auth.model.Account;
import com.tltn.chat.auth.service.InternalAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/accounts")
@RequiredArgsConstructor
public class InternalAccountController {

    private final InternalAccountService internalAccountService;

    @PostMapping
    public ResponseEntity<?> createAccount(@RequestBody CreateAccountRequest request) {
        internalAccountService.createAccount(request.getEmployeeId(), request.getUsername(), request.getDefaultPassword());
        return ResponseEntity.ok("Account created successfully");
    }

    @GetMapping("/{employeeId}")
    public ResponseEntity<Account> getAccount(@PathVariable String employeeId) {
        return ResponseEntity.ok(internalAccountService.getAccountByEmployeeId(employeeId));
    }

    @PatchMapping("/{employeeId}/disable")
    public ResponseEntity<?> disableAccount(@PathVariable String employeeId) {
        internalAccountService.disableAccount(employeeId);
        return ResponseEntity.ok("Account disabled successfully");
    }
}
