package com.kuetcord.playgrounds.dto;

import com.kuetcord.playgrounds.model.PlaygroundStatus;
import com.kuetcord.playgrounds.model.PlaygroundVisibility;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Partial update of playground metadata. {@code null} means "leave unchanged"; an empty string clears an
 * optional text/link field and an empty list clears the tags. {@code expectedVersion} is mandatory so two
 * leads editing the workspace at once get a 409 instead of a lost update.
 */
public record UpdatePlaygroundRequest(
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @Size(max = 5000, message = "Problem statement must be at most 5000 characters")
        String problemStatement,

        @Size(max = PlaygroundRequestRules.MAX_TAGS, message = "At most 8 tags are allowed")
        List<@Size(max = PlaygroundRequestRules.MAX_TAG_LENGTH, message = "A tag must be at most 30 characters")
             @Pattern(regexp = PlaygroundRequestRules.TAG, message = "Tags must be non-blank and must not contain commas")
             String> tags,

        PlaygroundVisibility visibility,

        PlaygroundStatus status,

        @Size(max = 255, message = "Repository URL must be at most 255 characters")
        @Pattern(regexp = PlaygroundRequestRules.HTTP_URL_OR_EMPTY, message = "Repository URL must be a valid http(s) link")
        String repoUrl,

        @Size(max = 255, message = "Demo URL must be at most 255 characters")
        @Pattern(regexp = PlaygroundRequestRules.HTTP_URL_OR_EMPTY, message = "Demo URL must be a valid http(s) link")
        String demoUrl,

        @NotNull(message = "expectedVersion is required")
        Long expectedVersion
) {

    @AssertTrue(message = "Title must not be blank")
    public boolean isTitleValid() {
        return title == null || !title.isBlank();
    }
}
