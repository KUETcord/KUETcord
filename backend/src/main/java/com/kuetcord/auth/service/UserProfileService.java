package com.kuetcord.auth.service;

import com.kuetcord.auth.dto.UpdateProfileRequest;
import com.kuetcord.auth.dto.UserSummaryDto;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages authenticated student profile reads and partial updates.
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;

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
        if (request.department() != null) {
            user.setDepartment(request.department().trim());
        }
        if (request.batch() != null) {
            user.setBatch(request.batch().trim());
        }

        User updated = userRepository.save(user);
        return UserSummaryDto.from(updated);
    }
}

