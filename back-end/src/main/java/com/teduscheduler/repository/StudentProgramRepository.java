package com.teduscheduler.repository;

import com.teduscheduler.model.StudentProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface StudentProgramRepository extends JpaRepository<StudentProgram, Long> {

    @Query("select sp from StudentProgram sp join fetch sp.student left join fetch sp.curriculum")
    List<StudentProgram> findAllWithStudent();

    @Query("select sp from StudentProgram sp left join fetch sp.curriculum where sp.student.id in :ids")
    List<StudentProgram> findByStudentIds(@Param("ids") Collection<Long> ids);

    @Query("select distinct sp.program from StudentProgram sp where sp.program is not null order by sp.program")
    List<String> findDistinctPrograms();
}
