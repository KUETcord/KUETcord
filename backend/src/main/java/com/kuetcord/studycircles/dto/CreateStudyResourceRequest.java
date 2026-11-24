package com.kuetcord.studycircles.dto;

import com.kuetcord.studycircles.model.ResourceCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStudyResourceRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @NotBlank(message = "url is required")
        @Size(max = 500, message = "URL must be at most 500 characters")
        @Pattern(regexp = "^https?://[^\\s/$.?#][^\\s]*$", message = "URL must be a valid http(s) link")
        String url,

        @NotNull(message = "category is required")
        ResourceCategory category
) {
}
