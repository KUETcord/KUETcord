package com.kuetcord.opportunities.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A hackathon, programming contest, robotics event, research call or case competition
 * submitted by a student. Backed by {@code opportunities} (V3 + V6).
 */
@Entity
@Table(name = "opportunities")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Opportunity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private OpportunityCategory category;

    @Column(name = "organizer", nullable = false, length = 100)
    private String organizer;

    @Column(name = "source_url", nullable = false, length = 500)
    private String sourceUrl;

    @Column(name = "registration_deadline", nullable = false)
    private Instant registrationDeadline;

    @Column(name = "event_start", nullable = false)
    private Instant eventStart;

    @Column(name = "event_end", nullable = false)
    private Instant eventEnd;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 30)
    private OpportunityMode mode = OpportunityMode.ONLINE;

    @Column(name = "eligibility")
    private String eligibility;

    @Builder.Default
    @Column(name = "team_size_min", nullable = false)
    private Integer teamSizeMin = 1;

    @Builder.Default
    @Column(name = "team_size_max", nullable = false)
    private Integer teamSizeMax = 4;

    @Column(name = "description")
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(name = "submitted_by", nullable = false, updatable = false)
    private Long submittedBy;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isExpiredAt(Instant now) {
        return registrationDeadline.isBefore(now);
    }

    public boolean isVerified() {
        return verificationStatus == VerificationStatus.VERIFIED;
    }

    public void markVerified(Long moderatorId, Instant at) {
        this.verificationStatus = VerificationStatus.VERIFIED;
        this.verifiedBy = moderatorId;
        this.verifiedAt = at;
    }
}
