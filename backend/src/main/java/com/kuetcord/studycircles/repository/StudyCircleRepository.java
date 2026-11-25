package com.kuetcord.studycircles.repository;

import com.kuetcord.studycircles.model.StudyCircle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StudyCircleRepository extends JpaRepository<StudyCircle, Long>, JpaSpecificationExecutor<StudyCircle> {
}
