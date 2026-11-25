package com.kuetcord.pulse.repository;

import com.kuetcord.pulse.model.Announcement;
import com.kuetcord.pulse.model.AnnouncementCategory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long>, JpaSpecificationExecutor<Announcement> {

    /**
     * Feed filters. A category filter alone is served by {@code idx_announcements_cat_date};
     * the unfiltered feed by {@code idx_announcements_published}.
     */
    static Specification<Announcement> matching(AnnouncementCategory category, String organization) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (organization != null && !organization.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("organization")),
                        organization.strip().toLowerCase(Locale.ROOT)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
