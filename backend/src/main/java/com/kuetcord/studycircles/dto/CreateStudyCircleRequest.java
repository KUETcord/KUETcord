package com.kuetcord.studycircles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStudyCircleRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        /** Optional link to a course in the CT Hub catalog. */
        Long courseOfferingId
) {
}
