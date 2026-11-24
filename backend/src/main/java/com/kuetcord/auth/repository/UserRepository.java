package com.kuetcord.auth.repository;

import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    @Query("""
            SELECT DISTINCT u FROM User u
            JOIN u.roles r
            WHERE r.name = :roleName
              AND u.status = com.kuetcord.auth.model.UserStatus.ACTIVE
              AND (:department = '' OR LOWER(COALESCE(u.department, '')) = :department)
              AND (LOWER(u.displayName) LIKE :pattern OR LOWER(u.email) LIKE :pattern)
            ORDER BY u.displayName ASC
            """)
    List<User> searchActiveUsersByRole(
            @Param("roleName") RoleName roleName,
            @Param("department") String department,
            @Param("pattern") String pattern,
            Pageable pageable);
}
