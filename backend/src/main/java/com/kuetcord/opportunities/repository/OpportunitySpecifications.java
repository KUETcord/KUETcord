package com.kuetcord.opportunities.repository;

import com.kuetcord.opportunities.dto.OpportunitySearchCriteria;
import com.kuetcord.opportunities.model.Opportunity;
import com.kuetcord.opportunities.model.OpportunityBookmark;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the multi-facet WHERE clause for the opportunity browse API.
 * Only filters that were actually supplied become predicates, so no nullable-parameter
 * {@code (:x IS NULL OR ...)} tricks reach PostgreSQL and the planner can use
 * {@code idx_opp_cat_status_deadline}.
 */
public final class OpportunitySpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private OpportunitySpecifications() {
    }

    public static Specification<Opportunity> matching(OpportunitySearchCriteria criteria, Long viewerId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.category() != null) {
                predicates.add(cb.equal(root.get("category"), criteria.category()));
            }
            if (criteria.mode() != null) {
                predicates.add(cb.equal(root.get("mode"), criteria.mode()));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("verificationStatus"), criteria.status()));
            }
            if (criteria.deadlineBefore() != null) {
                predicates.add(cb.lessThan(root.get("registrationDeadline"), criteria.deadlineBefore()));
            }
            if (criteria.query() != null) {
                String pattern = "%" + escapeLike(criteria.query().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("organizer")), pattern, LIKE_ESCAPE)));
            }
            if (criteria.bookmarkedOnly()) {
                Subquery<Long> bookmarked = query.subquery(Long.class);
                Root<OpportunityBookmark> bookmark = bookmarked.from(OpportunityBookmark.class);
                bookmarked.select(bookmark.get("opportunityId"))
                        .where(cb.equal(bookmark.get("userId"), viewerId),
                                cb.equal(bookmark.get("opportunityId"), root.get("id")));
                predicates.add(cb.exists(bookmarked));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Treat user input literally: searching {@code 100%} must not match everything. */
    static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
