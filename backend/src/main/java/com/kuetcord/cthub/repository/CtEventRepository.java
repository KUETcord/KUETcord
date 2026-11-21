package com.kuetcord.cthub.repository;

import com.kuetcord.cthub.model.CtEvent;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Optional;

public interface CtEventRepository extends JpaRepository<CtEvent, Long>, JpaSpecificationExecutor<CtEvent> {

    @Override
    @EntityGraph(attributePaths = {"courseOffering", "submittedBy", "verifiedBy"})
    List<CtEvent> findAll(@Nullable Specification<CtEvent> spec, Sort sort);

    @EntityGraph(attributePaths = {"courseOffering", "submittedBy", "verifiedBy"})
    Optional<CtEvent> findWithDetailsById(Long id);
}
