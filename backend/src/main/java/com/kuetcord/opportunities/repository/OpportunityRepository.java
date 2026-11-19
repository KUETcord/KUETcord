package com.kuetcord.opportunities.repository;

import com.kuetcord.opportunities.model.Opportunity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OpportunityRepository extends JpaRepository<Opportunity, Long>, JpaSpecificationExecutor<Opportunity> {
}
