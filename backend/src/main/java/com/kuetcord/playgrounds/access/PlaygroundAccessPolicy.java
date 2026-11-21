package com.kuetcord.playgrounds.access;

import com.kuetcord.common.exception.ConflictException;
import com.kuetcord.common.exception.ForbiddenException;
import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.playgrounds.model.Playground;
import com.kuetcord.playgrounds.model.PlaygroundPermission;
import com.kuetcord.playgrounds.model.PlaygroundRole;
import com.kuetcord.playgrounds.model.PlaygroundVisibility;
import com.kuetcord.playgrounds.repository.PlaygroundMemberRepository;
import com.kuetcord.playgrounds.repository.PlaygroundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Single place that answers "may this student do X in this playground?".
 * <ul>
 *   <li>Members get what their {@link PlaygroundRole} grants.</li>
 *   <li>Non-members may only VIEW, and only PUBLIC playgrounds.</li>
 *   <li>A PRIVATE playground answers 404 to non-members, so its existence is not leaked.</li>
 *   <li>Board writes on an ARCHIVED playground answer 409 until a lead reopens it.</li>
 * </ul>
 * Role checks live here rather than in {@code @PreAuthorize} because they depend on per-playground
 * membership, not on the global JWT roles.
 */
@Component
@RequiredArgsConstructor
public class PlaygroundAccessPolicy {

    private final PlaygroundRepository playgroundRepository;
    private final PlaygroundMemberRepository memberRepository;

    public PlaygroundAccess require(Long playgroundId, Long userId, PlaygroundPermission permission) {
        Playground playground = playgroundRepository.findById(playgroundId)
                .orElseThrow(PlaygroundAccessPolicy::notFound);
        PlaygroundRole role = memberRepository.findRole(playgroundId, userId).orElse(null);

        if (role == null) {
            if (playground.getVisibility() == PlaygroundVisibility.PRIVATE) {
                throw notFound();
            }
            if (permission != PlaygroundPermission.VIEW) {
                throw new ForbiddenException("Only members of this playground can do that");
            }
        } else if (!role.grants(permission)) {
            throw new ForbiddenException(permission == PlaygroundPermission.MANAGE
                    ? "Only a playground LEAD can do that"
                    : "VIEWER members have read-only access");
        }
        return new PlaygroundAccess(playground, role);
    }

    /** {@link #require} plus a guard against editing a frozen (ARCHIVED) playground. */
    public PlaygroundAccess requireWritable(Long playgroundId, Long userId, PlaygroundPermission permission) {
        PlaygroundAccess access = require(playgroundId, userId, permission);
        if (access.playground().isArchived()) {
            throw new ConflictException("Playground is archived; a lead must reopen it before it can be edited");
        }
        return access;
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Playground not found");
    }
}
