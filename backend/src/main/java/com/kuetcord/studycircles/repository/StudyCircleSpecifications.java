package com.kuetcord.studycircles.repository;

import com.kuetcord.studycircles.model.StudyCircle;
import com.kuetcord.studycircles.model.StudyCircleMember;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Directory filters; only supplied filters become predicates. */
public final class StudyCircleSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private StudyCircleSpecifications() {
    }

    public static Specification<StudyCircle> matching(Long courseOfferingId, String query, boolean mineOnly, Long viewerId) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (courseOfferingId != null) {
                predicates.add(cb.equal(root.get("courseOfferingId"), courseOfferingId));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + escapeLike(query.strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE));
            }
            if (mineOnly) {
                Subquery<Long> membership = criteriaQuery.subquery(Long.class);
                var member = membership.from(StudyCircleMember.class);
                membership.select(member.get("id")).where(
                        cb.equal(member.get("studyCircleId"), root.get("id")),
                        cb.equal(member.get("userId"), viewerId));
                predicates.add(cb.exists(membership));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
