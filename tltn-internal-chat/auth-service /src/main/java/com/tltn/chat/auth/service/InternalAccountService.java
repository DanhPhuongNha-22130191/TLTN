package com.tltn.chat.auth.service;

import com.tltn.chat.auth.model.Account;
import com.tltn.chat.auth.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalAccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public void createAccount(String employeeId, String username, String defaultPassword) {
        if (accountRepository.findByEmployeeId(employeeId).isPresent()) {
            throw new RuntimeException("Account for employee already exists");
        }

        Account account = Account.builder()
                .employeeId(employeeId)
                .username(username)
                .passwordHash(passwordEncoder.encode(defaultPassword))
                .status(Account.AccountStatus.PENDING_ACTIVATION)
                .mustChangePassword(true)
                .build();

        accountRepository.save(account);
    }

    public Account getAccountByEmployeeId(String employeeId) {
        return accountRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
    }

    public void disableAccount(String employeeId) {
        Account account = getAccountByEmployeeId(employeeId);
        account.setStatus(Account.AccountStatus.DISABLED);
        accountRepository.save(account);
    }
}
