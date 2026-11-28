package com.kuetcord.auth.service;

import com.kuetcord.auth.dto.ChangePasswordRequest;
import com.kuetcord.auth.dto.UpdateProfileRequest;
import com.kuetcord.auth.dto.UserSummaryDto;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.academic.YearTerm;
import com.kuetcord.common.exception.BadRequestException;
import com.kuetcord.common.exception.ConflictException;
import com.kuetcord.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Manages authenticated self-account profile reads, profile/avatar edits, and password updates.
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;

    @Transactional(readOnly = true)
    @Cacheable(value = "user_profiles_min", key = "#userId")
    public UserSummaryDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return UserSummaryDto.from(user);
    }

    @Transactional
    @CacheEvict(value = "user_profiles_min", key = "#userId")
    public UserSummaryDto updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.username() != null && !request.username().isBlank()) {
            String normalizedUsername = request.username().trim().toLowerCase(Locale.ROOT);
            if (userRepository.existsByUsernameIgnoreCaseAndIdNot(normalizedUsername, userId)) {
                throw new ConflictException("Username '" + normalizedUsername + "' is already taken");
            }
            user.setUsername(normalizedUsername);
        }
        if (request.department() != null) {
            String dept = request.department().trim();
            user.setDepartment(dept.isEmpty() ? null : dept.toUpperCase(Locale.ROOT));
        }
        if (request.batch() != null) {
            String batch = request.batch().trim();
            user.setBatch(batch.isEmpty() ? null : batch);
        }
        if (request.yearTerm() != null) {
            user.setYearTerm(YearTerm.normalizeNullable(request.yearTerm()));
        }
        if (request.rollNumber() != null) {
            String roll = request.rollNumber().trim();
            user.setRollNumber(roll.isEmpty() ? null : roll);
        }
        if (request.bio() != null) {
            String bio = request.bio().trim();
            user.setBio(bio.isEmpty() ? null : bio);
        }
        if (Boolean.TRUE.equals(request.clearAvatar())) {
            user.setAvatarUrl(null);
        } else if (request.avatarUrl() != null) {
            String avatar = request.avatarUrl().trim();
            user.setAvatarUrl(avatar.isEmpty() ? null : avatar);
        }

        User updated = userRepository.save(user);
        return UserSummaryDto.from(updated);
    }

    @Transactional
    @CacheEvict(value = "user_profiles_min", key = "#userId")
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean hasExistingPassword = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
        if (hasExistingPassword) {
            if (request.currentPassword() == null || request.currentPassword().isBlank()) {
                throw new BadRequestException("Current password is required");
            }
            if (!passwordService.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new BadCredentialsException("Current password is incorrect");
            }
            if (passwordService.matches(request.newPassword(), user.getPasswordHash())) {
                throw new BadRequestException("New password must be different from your current password");
            }
        }

        user.setPasswordHash(passwordService.hash(request.newPassword()));
        userRepository.save(user);
    }
}
