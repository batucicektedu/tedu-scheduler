package com.teduscheduler.repository;

import com.teduscheduler.model.Curriculum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CurriculumRepository extends JpaRepository<Curriculum, Long> {

    @Query("select c, (select count(s) from Student s where s.curriculum = c) from Curriculum c order by c.program, c.version")
    List<Object[]> findAllWithStudentCount();
}
