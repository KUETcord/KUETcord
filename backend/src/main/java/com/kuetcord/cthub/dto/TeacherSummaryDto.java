package com.kuetcord.cthub.dto;

import com.kuetcord.auth.model.User;

public record TeacherSummaryDto(
        Long id,
        String displayName,
        String email,
        String department
) {
    public static TeacherSummaryDto from(User user) {
        return new TeacherSummaryDto(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getDepartment()
        );
    }
}
