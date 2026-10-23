package io.clientra.security;

import io.clientra.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepo;

    @Override
    public AppUserPrincipal loadUserByUsername(String email) {
        return userRepo.findByEmailIgnoreCase(email)
                .map(AppUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException(email));
    }
}
