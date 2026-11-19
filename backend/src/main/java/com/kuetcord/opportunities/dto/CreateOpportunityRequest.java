package com.kuetcord.opportunities.dto;

import com.kuetcord.opportunities.model.OpportunityCategory;
import com.kuetcord.opportunities.model.OpportunityMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Body of {@code POST /api/v1/opportunities}. New listings always start {@code UNVERIFIED}.
 */
public record CreateOpportunityRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @NotNull(message = "Category is required")
        OpportunityCategory category,

        @NotBlank(message = "Organizer is required")
        @Size(max = 100, message = "Organizer must be at most 100 characters")
        String organizer,

        @NotBlank(message = "Official source link is required")
        @Size(max = 500, message = "Source URL must be at most 500 characters")
        @Pattern(regexp = "^https?://[^\\s/$.?#][^\\s]*$", message = "Source URL must be a valid http(s) link")
        String sourceUrl,

        @NotNull(message = "Registration deadline is required")
        @Future(message = "Registration deadline must be in the future")
        Instant registrationDeadline,

        @NotNull(message = "Start date is required")
        Instant startDate,

        @NotNull(message = "End date is required")
        Instant endDate,

        /* Defaults to ONLINE when omitted (matches the V3 column default). */
        OpportunityMode mode,

        @Size(max = 2000, message = "Eligibility must be at most 2000 characters")
        String eligibility,

        @Min(value = 1, message = "Minimum team size must be at least 1")
        @Max(value = 20, message = "Minimum team size must be at most 20")
        Integer teamSizeMin,

        @Min(value = 1, message = "Maximum team size must be at least 1")
        @Max(value = 20, message = "Maximum team size must be at most 20")
        Integer teamSizeMax,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description
) {
    public static final int DEFAULT_TEAM_SIZE_MIN = 1;
    public static final int DEFAULT_TEAM_SIZE_MAX = 4;

    public int resolvedTeamSizeMin() {
        return teamSizeMin != null ? teamSizeMin : DEFAULT_TEAM_SIZE_MIN;
    }

    public int resolvedTeamSizeMax() {
        return teamSizeMax != null ? teamSizeMax : Math.max(DEFAULT_TEAM_SIZE_MAX, resolvedTeamSizeMin());
    }

    public OpportunityMode resolvedMode() {
        return mode != null ? mode : OpportunityMode.ONLINE;
    }

    @AssertTrue(message = "End date must not be before start date")
    public boolean isEventWindowValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "Registration deadline must not be after the event ends")
    public boolean isDeadlineWithinEvent() {
        return registrationDeadline == null || endDate == null || !registrationDeadline.isAfter(endDate);
    }

    @AssertTrue(message = "Minimum team size cannot exceed maximum team size")
    public boolean isTeamSizeRangeValid() {
        return resolvedTeamSizeMin() <= resolvedTeamSizeMax();
    }
}
