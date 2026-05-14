package com.tltn.chat.auth.service;

import com.tltn.chat.auth.dto.*;
import com.tltn.chat.auth.model.Account;
import com.tltn.chat.auth.model.AuditLog;
import com.tltn.chat.auth.model.RefreshToken;
import com.tltn.chat.auth.repository.AccountRepository;
import com.tltn.chat.auth.repository.AuditLogRepository;
import com.tltn.chat.auth.repository.RefreshTokenRepository;
import com.tltn.chat.auth.security.jwt.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenDurationMs;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateAccessToken(request.getUsername());

        Account account = accountRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        account.setLastLoginAt(LocalDateTime.now());
        accountRepository.save(account);

        String refreshTokenStr = createRefreshToken(account);

        auditLogRepository.save(AuditLog.builder()
                .account(account)
                .action("LOGIN")
                .build());

        return LoginResponse.builder()
                .accessToken(jwt)
                .refreshToken(refreshTokenStr)
                .username(account.getUsername())
                .employeeId(account.getEmployeeId())
                .mustChangePassword(account.isMustChangePassword())
                .build();
    }

    @Transactional
    public void activate(ActivateRequest request) {
        Account account = accountRepository.findByEmployeeId(request.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee ID not found"));

        if (account.getStatus() != Account.AccountStatus.PENDING_ACTIVATION) {
            throw new RuntimeException("Account is already active or locked");
        }

        if (!passwordEncoder.matches(request.getDefaultPassword(), account.getPasswordHash())) {
            throw new RuntimeException("Invalid default password");
        }

        account.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setMustChangePassword(false);
        accountRepository.save(account);

        auditLogRepository.save(AuditLog.builder()
                .account(account)
                .action("ACTIVATE")
                .build());
    }

    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenRepository.findByToken(requestRefreshToken)
                .map(this::verifyExpiration)
                .map(RefreshToken::getAccount)
                .map(account -> {
                    String token = jwtUtils.generateAccessToken(account.getUsername());
                    return LoginResponse.builder()
                            .accessToken(token)
                            .refreshToken(requestRefreshToken)
                            .username(account.getUsername())
                            .employeeId(account.getEmployeeId())
                            .build();
                })
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
    }

    @Transactional
    public void logout() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        refreshTokenRepository.deleteByAccount_Id(account.getId());
        
        auditLogRepository.save(AuditLog.builder()
                .account(account)
                .action("LOGOUT")
                .build());
    }

    private String createRefreshToken(Account account) {
        RefreshToken refreshToken = RefreshToken.builder()
                .account(account)
                .token(UUID.randomUUID().toString())
                .expiredAt(LocalDateTime.now().plusNanos(refreshTokenDurationMs * 1000000))
                .build();

        refreshToken = refreshTokenRepository.save(refreshToken);
        return refreshToken.getToken();
    }

    private RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiredAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token was expired. Please make a new signin request");
        }
        return token;
    }
}
