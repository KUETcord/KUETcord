package com.kuetcord.playgrounds.model;

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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * A team workspace for a hackathon entry, research project or course project.
 * Backed by {@code playgrounds} (V3 + V9). Metadata edits are guarded by {@code @Version}.
 */
@Entity
@Table(name = "playgrounds")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Playground {

    /** Tags are stored as one comma-separated column (V3 {@code tags VARCHAR(255)}). */
    public static final String TAG_SEPARATOR = ", ";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "problem_statement")
    private String problemStatement;

    @Column(name = "tags", length = 255)
    private String tags;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 30)
    private PlaygroundVisibility visibility = PlaygroundVisibility.PUBLIC;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PlaygroundStatus status = PlaygroundStatus.PLANNING;

    @Column(name = "repo_url", length = 255)
    private String repoUrl;

    @Column(name = "demo_url", length = 255)
    private String demoUrl;

    /** Shared chat thread owned by the messaging module; linked in a later sprint. */
    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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

    public boolean isArchived() {
        return status == PlaygroundStatus.ARCHIVED;
    }

    public List<String> tagList() {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::strip)
                .filter(tag -> !tag.isEmpty())
                .toList();
    }

    public void replaceTags(List<String> newTags) {
        this.tags = newTags == null || newTags.isEmpty() ? null : String.join(TAG_SEPARATOR, newTags);
    }
}
