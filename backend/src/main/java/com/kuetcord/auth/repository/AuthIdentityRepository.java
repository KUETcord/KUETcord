package com.kuetcord.auth.repository;

import com.kuetcord.auth.model.AuthIdentity;
import com.kuetcord.auth.model.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {

    Optional<AuthIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);

    boolean existsByUserIdAndProvider(Long userId, AuthProvider provider);
}

