package com.teduscheduler.repository;

import com.teduscheduler.model.CoursePlanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoursePlanItemRepository extends JpaRepository<CoursePlanItem, Long> {

    List<CoursePlanItem> findByCoursePlanIdOrderBySemesterAscOrderNoAsc(Long planId);

    @Modifying
    @Query("delete from CoursePlanItem i where i.coursePlan.id = :planId")
    void deleteByPlanId(@Param("planId") Long planId);

    /** [planId, itemType, adet] */
    @Query("select i.coursePlan.id, i.itemType, count(i) from CoursePlanItem i group by i.coursePlan.id, i.itemType")
    List<Object[]> countByPlanAndType();
}
