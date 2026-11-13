package com.kuetcord.auth.repository;

import com.kuetcord.auth.model.Role;
import com.kuetcord.auth.model.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}

