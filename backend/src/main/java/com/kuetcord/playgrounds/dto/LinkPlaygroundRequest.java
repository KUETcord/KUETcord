package com.kuetcord.playgrounds.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

/**
 * "Form a team for this contest". Three shapes:
 * <ul>
 *   <li>{@code {"playgroundId": 12}}: link an existing playground (caller must be its LEAD).</li>
 *   <li>{@code {"newPlayground": {...}}}: create a playground with those details and link it.</li>
 *   <li>{@code {}}: one-click team; a PUBLIC playground named after the opportunity is created and linked.</li>
 * </ul>
 */
public record LinkPlaygroundRequest(
        Long playgroundId,

        @Valid
        CreatePlaygroundRequest newPlayground
) {

    @AssertTrue(message = "Send either playgroundId or newPlayground, not both")
    public boolean isSingleTarget() {
        return playgroundId == null || newPlayground == null;
    }
}
