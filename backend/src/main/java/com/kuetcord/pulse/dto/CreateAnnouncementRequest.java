package com.kuetcord.pulse.dto;

import com.kuetcord.pulse.model.AnnouncementCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAnnouncementRequest(
        @NotBlank(message = "Organization is required")
        @Size(max = 100, message = "Organization must be at most 100 characters")
        String organization,

        @NotNull(message = "category is required")
        AnnouncementCategory category,

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @NotBlank(message = "Body is required")
        @Size(max = 10000, message = "Body must be at most 10000 characters")
        String body,

        @Size(max = 500, message = "Source URL must be at most 500 characters")
        @Pattern(regexp = "^https?://[^\\s/$.?#][^\\s]*$", message = "Source URL must be a valid http(s) link")
        String sourceUrl
) {
}
