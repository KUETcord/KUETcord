package com.kuetcord.cthub.service;

import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.cthub.dto.TeacherSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Provides fast read-only lookup of active KUET faculty accounts ({@code ROLE_TEACHER})
 * for CRs proposing Class Test schedules.
 */
@Service
@RequiredArgsConstructor
public class TeacherSearchService {

    private static final int MAX_RESULTS = 20;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<TeacherSummaryDto> searchTeachers(String query, String department) {
        String normalizedDept = (department == null || department.isBlank())
                ? ""
                : department.trim().toLowerCase(Locale.ROOT);
        String pattern = (query == null || query.isBlank())
                ? "%"
                : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";

        return userRepository
                .searchActiveUsersByRole(RoleName.ROLE_TEACHER, normalizedDept, pattern, PageRequest.of(0, MAX_RESULTS))
                .stream()
                .map(TeacherSummaryDto::from)
                .toList();
    }
}
