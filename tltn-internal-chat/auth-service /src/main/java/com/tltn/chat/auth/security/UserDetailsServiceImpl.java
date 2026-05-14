package com.tltn.chat.auth.security;

import com.tltn.chat.auth.model.Account;
import com.tltn.chat.auth.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + username));

        return new User(
                account.getUsername(),
                account.getPasswordHash(),
                account.getStatus() == Account.AccountStatus.ACTIVE || account.getStatus() == Account.AccountStatus.PENDING_ACTIVATION,
                true, true,
                account.getStatus() != Account.AccountStatus.LOCKED,
                Collections.emptyList()
        );
    }
}
