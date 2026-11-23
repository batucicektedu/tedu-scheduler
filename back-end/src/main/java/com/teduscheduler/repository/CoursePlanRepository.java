package com.teduscheduler.repository;

import com.teduscheduler.model.CoursePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoursePlanRepository extends JpaRepository<CoursePlan, Long> {
}
