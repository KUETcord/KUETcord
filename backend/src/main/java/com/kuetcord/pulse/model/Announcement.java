package com.kuetcord.pulse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A verified campus notice (Campus Pulse): department seminars, club events, academic and career notices.
 * Only staff roles may publish, which is what makes the feed trustworthy (see AnnouncementController).
 */
@Entity
@Table(name = "announcements")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Publishing body, e.g. {@code CSE_DEPT}, {@code ROBOTICS_CLUB}. */
    @Column(name = "organization", nullable = false, length = 100)
    private String organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private AnnouncementCategory category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @PrePersist
    protected void onCreate() {
        if (publishedAt == null) {
            publishedAt = Instant.now();
        }
    }
}
