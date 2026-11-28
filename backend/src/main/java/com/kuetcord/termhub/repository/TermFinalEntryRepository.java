package com.kuetcord.termhub.repository;

import com.kuetcord.termhub.model.TermFinalEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TermFinalEntryRepository extends JpaRepository<TermFinalEntry, Long> {

    @EntityGraph(attributePaths = {"schedule", "courseOffering"})
    @Query("""
            SELECT e FROM TermFinalEntry e
            JOIN e.schedule s
            WHERE e.examDate >= :fromDate
              AND e.examDate <= :toDate
              AND (:department IS NULL OR LOWER(s.targetDepartment) = LOWER(CAST(:department AS string)))
              AND (:yearTerm IS NULL OR s.yearTerm = CAST(:yearTerm AS string))
              AND (:batch IS NULL OR LOWER(s.batch) = LOWER(CAST(:batch AS string)))
            ORDER BY e.examDate ASC, s.examStartTime ASC
            """)
    List<TermFinalEntry> findForCalendar(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("department") String department,
            @Param("yearTerm") String yearTerm,
            @Param("batch") String batch);
}
