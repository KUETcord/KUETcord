package com.kuetcord.admin.service;

import com.kuetcord.admin.dto.AdminCreateTeacherRequest;
import com.kuetcord.admin.dto.AdminCreateUserRequest;
import com.kuetcord.admin.dto.AdminUpdateUserRequest;
import com.kuetcord.auth.dto.UserSummaryDto;
import com.kuetcord.auth.model.AuthProvider;
import com.kuetcord.auth.model.Role;
import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.model.UserStatus;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.auth.service.AuthAccountProvisioner;
import com.kuetcord.auth.service.PasswordService;
import com.kuetcord.auth.validator.EmailDomainValidator;
import com.kuetcord.common.academic.YearTerm;
import com.kuetcord.common.exception.BadRequestException;
import com.kuetcord.common.exception.ConflictException;
import com.kuetcord.common.exception.ForbiddenException;
import com.kuetcord.common.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Governance service allowing {@code ROLE_ADMIN} and {@code ROLE_MODERATOR} to directly provision
 * verified Teacher accounts and perform full CRUD across institutional user accounts.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final EmailDomainValidator emailDomainValidator;
    private final PasswordService passwordService;
    private final AuthAccountProvisioner accountProvisioner;

    @Transactional(readOnly = true)
    public List<UserSummaryDto> listUsers(
            RoleName role,
            String department,
            String yearTerm,
            UserStatus status,
            String query) {
        String normalizedYearTerm = (yearTerm != null && !yearTerm.isBlank())
                ? YearTerm.normalizeNullable(yearTerm)
                : null;

        Specification<User> spec = (root, cq, cb) -> {
            if (cq != null) {
                cq.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                Join<User, Role> roleJoin = root.join("roles");
                predicates.add(cb.equal(roleJoin.get("name"), role));
            }
            if (department != null && !department.isBlank()) {
                predicates.add(cb.equal(
                        cb.lower(root.get("department")),
                        department.trim().toLowerCase(Locale.ROOT)));
            }
            if (normalizedYearTerm != null) {
                predicates.add(cb.equal(root.get("yearTerm"), normalizedYearTerm));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("displayName")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("username")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return userRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(UserSummaryDto::from)
                .toList();
    }

    @Transactional
    public UserSummaryDto createTeacherDirectly(Long actorId, AdminCreateTeacherRequest request) {
        requireActor(actorId);
        String normalizedEmail = normalizeAndValidateEmail(request.email());

        Role teacherRole = accountProvisioner.requireRole(RoleName.ROLE_TEACHER);
        User created = User.builder()
                .email(normalizedEmail)
                .displayName(request.displayName().trim())
                .username(accountProvisioner.deriveUniqueUsername(normalizedEmail))
                .passwordHash(passwordService.hash(request.initialPassword()))
                .department(request.department().trim().toUpperCase(Locale.ROOT))
                .batch("Faculty")
                .bio(trimNullable(request.bio()))
                .emailVerifiedAt(Instant.now())
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(teacherRole)))
                .build();

        User saved = userRepository.save(created);
        accountProvisioner.linkIdentityIfAbsent(saved, AuthProvider.LOCAL, normalizedEmail);
        return UserSummaryDto.from(saved);
    }

    @Transactional
    public UserSummaryDto createUserDirectly(Long actorId, AdminCreateUserRequest request) {
        User actor = requireActor(actorId);
        boolean actorIsAdmin = hasRole(actor, RoleName.ROLE_ADMIN);
        if (!actorIsAdmin && request.roles().contains(RoleName.ROLE_ADMIN)) {
            throw new ForbiddenException("Only platform administrators may grant ROLE_ADMIN");
        }

        String normalizedEmail = normalizeAndValidateEmail(request.email());
        Set<Role> resolvedRoles = request.roles().stream()
                .map(accountProvisioner::requireRole)
                .collect(Collectors.toCollection(HashSet::new));

        String dept = trimNullable(request.department());
        if (dept != null) {
            dept = dept.toUpperCase(Locale.ROOT);
        }

        User created = User.builder()
                .email(normalizedEmail)
                .displayName(request.displayName().trim())
                .username(accountProvisioner.deriveUniqueUsername(normalizedEmail))
                .passwordHash(passwordService.hash(request.initialPassword()))
                .department(dept)
                .batch(trimNullable(request.batch()))
                .yearTerm(YearTerm.normalizeNullable(request.yearTerm()))
                .rollNumber(trimNullable(request.rollNumber()))
                .bio(trimNullable(request.bio()))
                .emailVerifiedAt(Instant.now())
                .status(UserStatus.ACTIVE)
                .roles(resolvedRoles)
                .build();

        User saved = userRepository.save(created);
        accountProvisioner.linkIdentityIfAbsent(saved, AuthProvider.LOCAL, normalizedEmail);
        return UserSummaryDto.from(saved);
    }

    @Transactional
    @CacheEvict(value = "user_profiles_min", key = "#targetUserId")
    public UserSummaryDto updateUser(Long actorId, Long targetUserId, AdminUpdateUserRequest request) {
        User actor = requireActor(actorId);
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));

        boolean actorIsAdmin = hasRole(actor, RoleName.ROLE_ADMIN);
        boolean targetIsAdmin = hasRole(target, RoleName.ROLE_ADMIN);

        if (!actorIsAdmin && targetIsAdmin) {
            throw new ForbiddenException("Moderators cannot modify administrator accounts");
        }

        if (request.roles() != null && !request.roles().isEmpty()) {
            if (!actorIsAdmin && request.roles().contains(RoleName.ROLE_ADMIN)) {
                throw new ForbiddenException("Only platform administrators may grant ROLE_ADMIN");
            }
            if (targetIsAdmin && !request.roles().contains(RoleName.ROLE_ADMIN)) {
                ensureAnotherActiveAdminRemains(target);
            }
            Set<Role> newRoles = request.roles().stream()
                    .map(accountProvisioner::requireRole)
                    .collect(Collectors.toCollection(HashSet::new));
            target.setRoles(newRoles);
        }

        if (request.status() != null && request.status() != target.getStatus()) {
            if (actorId.equals(targetUserId) && request.status() != UserStatus.ACTIVE) {
                throw new BadRequestException("You cannot suspend your own account");
            }
            if (targetIsAdmin && request.status() != UserStatus.ACTIVE) {
                ensureAnotherActiveAdminRemains(target);
            }
            target.setStatus(request.status());
        }

        if (request.displayName() != null && !request.displayName().isBlank()) {
            target.setDisplayName(request.displayName().trim());
        }
        if (request.department() != null) {
            String dept = trimNullable(request.department());
            target.setDepartment(dept == null ? null : dept.toUpperCase(Locale.ROOT));
        }
        if (request.batch() != null) {
            target.setBatch(trimNullable(request.batch()));
        }
        if (request.yearTerm() != null) {
            target.setYearTerm(YearTerm.normalizeNullable(request.yearTerm()));
        }
        if (request.rollNumber() != null) {
            target.setRollNumber(trimNullable(request.rollNumber()));
        }
        if (request.bio() != null) {
            target.setBio(trimNullable(request.bio()));
        }
        if (request.resetPassword() != null && !request.resetPassword().isBlank()) {
            target.setPasswordHash(passwordService.hash(request.resetPassword()));
        }
        if (request.emailVerified() != null) {
            if (request.emailVerified() && target.getEmailVerifiedAt() == null) {
                target.setEmailVerifiedAt(Instant.now());
            } else if (!request.emailVerified()) {
                target.setEmailVerifiedAt(null);
            }
        }

        User saved = userRepository.save(target);
        return UserSummaryDto.from(saved);
    }

    @Transactional
    @CacheEvict(value = "user_profiles_min", key = "#targetUserId")
    public void deleteUser(Long actorId, Long targetUserId) {
        if (actorId.equals(targetUserId)) {
            throw new BadRequestException("You cannot delete your own account");
        }
        User actor = requireActor(actorId);
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));

        boolean actorIsAdmin = hasRole(actor, RoleName.ROLE_ADMIN);
        boolean targetIsAdmin = hasRole(target, RoleName.ROLE_ADMIN);

        if (!actorIsAdmin && targetIsAdmin) {
            throw new ForbiddenException("Moderators cannot delete administrator accounts");
        }
        if (targetIsAdmin) {
            ensureAnotherActiveAdminRemains(target);
        }

        try {
            userRepository.delete(target);
            userRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(
                    "User owns linked academic or collaboration records; suspend the account instead of deleting");
        }
    }

    private void ensureAnotherActiveAdminRemains(User target) {
        if (target.isActive() && userRepository.countActiveByRole(RoleName.ROLE_ADMIN) <= 1) {
            throw new BadRequestException("At least one active ROLE_ADMIN account must remain on the platform");
        }
    }

    private String normalizeAndValidateEmail(String rawEmail) {
        String normalized = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!emailDomainValidator.isValid(normalized)) {
            throw new BadRequestException("Email must belong to an approved KUET student or faculty domain");
        }
        if (userRepository.existsByEmailIgnoreCase(normalized)) {
            throw new ConflictException("An account with email '" + normalized + "' already exists");
        }
        return normalized;
    }

    private User requireActor(Long actorId) {
        return userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Actor not found: " + actorId));
    }

    private static boolean hasRole(User user, RoleName roleName) {
        return user.getRoles().stream().anyMatch(r -> r.getName() == roleName);
    }

    private static String trimNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
