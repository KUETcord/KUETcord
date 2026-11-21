package com.kuetcord.playgrounds.access;

import com.kuetcord.common.exception.ConflictException;
import com.kuetcord.common.exception.ForbiddenException;
import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.playgrounds.model.Playground;
import com.kuetcord.playgrounds.model.PlaygroundPermission;
import com.kuetcord.playgrounds.model.PlaygroundRole;
import com.kuetcord.playgrounds.model.PlaygroundStatus;
import com.kuetcord.playgrounds.model.PlaygroundVisibility;
import com.kuetcord.playgrounds.repository.PlaygroundMemberRepository;
import com.kuetcord.playgrounds.repository.PlaygroundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaygroundAccessPolicyTest {

    private static final long PLAYGROUND_ID = 7L;
    private static final long USER_ID = 42L;

    private PlaygroundRepository playgroundRepository;
    private PlaygroundMemberRepository memberRepository;
    private PlaygroundAccessPolicy policy;

    @BeforeEach
    void setUp() {
        playgroundRepository = mock(PlaygroundRepository.class);
        memberRepository = mock(PlaygroundMemberRepository.class);
        policy = new PlaygroundAccessPolicy(playgroundRepository, memberRepository);
    }

    @Test
    @DisplayName("Members get their role back; a VIEWER is refused board edits with 403")
    void shouldApplyMemberRole() {
        givenPlayground(PlaygroundVisibility.PRIVATE, PlaygroundStatus.IN_PROGRESS);
        givenRole(PlaygroundRole.CONTRIBUTOR);
        assertEquals(PlaygroundRole.CONTRIBUTOR,
                policy.require(PLAYGROUND_ID, USER_ID, PlaygroundPermission.EDIT_BOARD).role());

        givenRole(PlaygroundRole.VIEWER);
        assertEquals(PlaygroundRole.VIEWER, policy.require(PLAYGROUND_ID, USER_ID, PlaygroundPermission.VIEW).role());
        assertThrows(ForbiddenException.class,
                () -> policy.require(PLAYGROUND_ID, USER_ID, PlaygroundPermission.EDIT_BOARD));
        assertThrows(ForbiddenException.class,
                () -> policy.require(PLAYGROUND_ID, USER_ID, PlaygroundPermission.MANAGE));
    }

    @Test
    @DisplayName("Non-members may read a PUBLIC playground but not change it")
    void shouldLetOutsidersReadPublicPlaygrounds() {
        givenPlayground(PlaygroundVisibility.PUBLIC, PlaygroundStatus.PLANNING);
        givenRole(null);

        PlaygroundAccess access = policy.require(PLAYGROUND_ID, USER_ID, PlaygroundPermission.VIEW);
        assertFalse(access.isMember());
        assertNull(access.role());
        assertThrows(ForbiddenException.class,
                () -> policy.require(PLAYGROUND_ID, USER_ID, PlaygroundPermission.EDIT_BOARD));
    }

    @Test
    @DisplayName("A PRIVATE playground answers 404 to non-members, exactly like a missing one")
    void shouldHidePrivatePlaygroundsFromOutsiders() {
        givenPlayground(PlaygroundVisibility.PRIVATE, PlaygroundStatus.PLANNING);
        givenRole(null);
        ResourceNotFoundException hidden = assertThrows(ResourceNotFoundException.class,
                () -> policy.require(PLAYGROUND_ID, USER_ID, PlaygroundPermission.VIEW));

        when(playgroundRepository.findById(PLAYGROUND_ID)).thenReturn(Optional.empty());
        ResourceNotFoundException missing = assertThrows(ResourceNotFoundException.class,
                () -> policy.require(PLAYGROUND_ID, USER_ID, PlaygroundPermission.VIEW));

        assertEquals(missing.getMessage(), hidden.getMessage());
    }

    @Test
    @DisplayName("Writes to an ARCHIVED playground are a 409 even for a LEAD; reads still work")
    void shouldFreezeArchivedPlaygrounds() {
        givenPlayground(PlaygroundVisibility.PUBLIC, PlaygroundStatus.ARCHIVED);
        givenRole(PlaygroundRole.LEAD);

        assertThrows(ConflictException.class,
                () -> policy.requireWritable(PLAYGROUND_ID, USER_ID, PlaygroundPermission.EDIT_BOARD));
        assertEquals(PlaygroundRole.LEAD, policy.require(PLAYGROUND_ID, USER_ID, PlaygroundPermission.MANAGE).role());
    }

    private void givenPlayground(PlaygroundVisibility visibility, PlaygroundStatus status) {
        Playground playground = Playground.builder()
                .id(PLAYGROUND_ID).title("Team Alpha").ownerId(1L).visibility(visibility).status(status).version(0L)
                .build();
        when(playgroundRepository.findById(PLAYGROUND_ID)).thenReturn(Optional.of(playground));
    }

    private void givenRole(PlaygroundRole role) {
        when(memberRepository.findRole(PLAYGROUND_ID, USER_ID)).thenReturn(Optional.ofNullable(role));
    }
}
