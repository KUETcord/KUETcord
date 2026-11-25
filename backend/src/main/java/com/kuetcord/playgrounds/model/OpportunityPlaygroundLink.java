package com.kuetcord.playgrounds.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * "This team was formed for that contest" ({@code opportunity_playgrounds}, V3 + V12).
 * Rows are written with {@code INSERT ... ON CONFLICT DO NOTHING}, so JPA only reads them.
 */
@Entity
@Table(name = "opportunity_playgrounds")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OpportunityPlaygroundLink {

    @EmbeddedId
    private Key id;

    @Column(name = "linked_by", insertable = false, updatable = false)
    private Long linkedBy;

    @Column(name = "linked_at", nullable = false, insertable = false, updatable = false)
    private Instant linkedAt;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "opportunity_id", nullable = false)
        private Long opportunityId;

        @Column(name = "playground_id", nullable = false)
        private Long playgroundId;
    }
}
