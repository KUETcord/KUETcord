package com.kuetcord.playgrounds.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaygroundRoleTest {

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @CsvSource({
            "LEAD,        VIEW,       true",
            "LEAD,        EDIT_BOARD, true",
            "LEAD,        MANAGE,     true",
            "CONTRIBUTOR, VIEW,       true",
            "CONTRIBUTOR, EDIT_BOARD, true",
            "CONTRIBUTOR, MANAGE,     false",
            "VIEWER,      VIEW,       true",
            "VIEWER,      EDIT_BOARD, false",
            "VIEWER,      MANAGE,     false"
    })
    @DisplayName("Role permission matrix: LEAD manages, CONTRIBUTOR edits the board, VIEWER only reads")
    void shouldGrantPermissionsByRole(PlaygroundRole role, PlaygroundPermission permission, boolean expected) {
        assertEquals(expected, role.grants(permission));
    }
}
