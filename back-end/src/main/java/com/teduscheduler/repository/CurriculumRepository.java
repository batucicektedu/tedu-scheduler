package com.teduscheduler.repository;

import com.teduscheduler.model.Curriculum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CurriculumRepository extends JpaRepository<Curriculum, Long> {

    @Query("select c, (select count(sp) from StudentProgram sp where sp.curriculum = c) from Curriculum c order by c.program, c.version")
    List<Object[]> findAllWithStudentCount();
}
