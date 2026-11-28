package com.kuetcord.termhub.repository;

import com.kuetcord.termhub.model.TermFinalSchedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TermFinalScheduleRepository extends JpaRepository<TermFinalSchedule, Long> {

    @EntityGraph(attributePaths = {"createdBy", "entries", "entries.courseOffering"})
    @Query("SELECT s FROM TermFinalSchedule s "
            + "WHERE (:dept IS NULL OR LOWER(s.targetDepartment) = LOWER(CAST(:dept AS string))) "
            + "AND (:term IS NULL OR s.yearTerm = CAST(:term AS string)) "
            + "AND (:batch IS NULL OR LOWER(s.batch) = LOWER(CAST(:batch AS string))) "
            + "AND (:session IS NULL OR LOWER(s.session) = LOWER(CAST(:session AS string))) "
            + "ORDER BY s.createdAt DESC")
    List<TermFinalSchedule> search(
            @Param("dept") String dept,
            @Param("term") String term,
            @Param("batch") String batch,
            @Param("session") String session);

    @EntityGraph(attributePaths = {"createdBy", "entries", "entries.courseOffering"})
    Optional<TermFinalSchedule> findWithEntriesById(Long id);
}
