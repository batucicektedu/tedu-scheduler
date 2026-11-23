package com.teduscheduler.repository;

import com.teduscheduler.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    @Query("select distinct s.entryYear from Student s where s.entryYear is not null order by s.entryYear")
    List<Integer> findDistinctEntryYears();
}
