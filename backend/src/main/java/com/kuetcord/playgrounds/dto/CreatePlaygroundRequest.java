package com.kuetcord.playgrounds.dto;

import com.kuetcord.playgrounds.model.PlaygroundVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePlaygroundRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @Size(max = 5000, message = "Problem statement must be at most 5000 characters")
        String problemStatement,

        @Size(max = PlaygroundRequestRules.MAX_TAGS, message = "At most 8 tags are allowed")
        List<@Size(max = PlaygroundRequestRules.MAX_TAG_LENGTH, message = "A tag must be at most 30 characters")
             @Pattern(regexp = PlaygroundRequestRules.TAG, message = "Tags must be non-blank and must not contain commas")
             String> tags,

        PlaygroundVisibility visibility,

        @Size(max = 255, message = "Repository URL must be at most 255 characters")
        @Pattern(regexp = PlaygroundRequestRules.HTTP_URL, message = "Repository URL must be a valid http(s) link")
        String repoUrl,

        @Size(max = 255, message = "Demo URL must be at most 255 characters")
        @Pattern(regexp = PlaygroundRequestRules.HTTP_URL, message = "Demo URL must be a valid http(s) link")
        String demoUrl
) {

    public PlaygroundVisibility resolvedVisibility() {
        return visibility == null ? PlaygroundVisibility.PUBLIC : visibility;
    }
}
