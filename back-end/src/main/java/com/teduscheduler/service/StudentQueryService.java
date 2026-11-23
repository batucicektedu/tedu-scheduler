package com.teduscheduler.service;

import com.teduscheduler.dto.CurriculumDto;
import com.teduscheduler.dto.StudentDto;
import com.teduscheduler.model.Student;
import com.teduscheduler.model.StudentProgram;
import com.teduscheduler.repository.CurriculumRepository;
import com.teduscheduler.repository.StudentProgramRepository;
import com.teduscheduler.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StudentQueryService {

    /** Primary first, then by registration date (nulls last), then id. */
    private static final Comparator<StudentProgram> PROGRAM_ORDER = Comparator
            .comparing((StudentProgram p) -> !p.isPrimaryProgram())
            .thenComparing(StudentProgram::getRegistrationDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StudentProgram::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentProgramRepository studentProgramRepository;

    @Autowired
    private CurriculumRepository curriculumRepository;

    public Page<StudentDto> search(int page, int size, String program, Integer entryYear, Long curriculumId, String q) {
        Specification<Student> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            boolean hasProgram = program != null && !program.trim().isEmpty();
            if (hasProgram || curriculumId != null) {
                // Both filters must hold for the SAME programme record.
                Subquery<Long> sq = query.subquery(Long.class);
                Root<StudentProgram> sp = sq.from(StudentProgram.class);
                List<Predicate> sps = new ArrayList<>();
                sps.add(cb.equal(sp.get("student"), root));
                if (hasProgram) sps.add(cb.equal(sp.get("program"), program.trim()));
                if (curriculumId != null) sps.add(cb.equal(sp.get("curriculum").get("id"), curriculumId));
                sq.select(sp.<Long>get("id")).where(sps.toArray(new Predicate[0]));
                ps.add(cb.exists(sq));
            }
            if (entryYear != null) {
                ps.add(cb.equal(root.get("entryYear"), entryYear));
            }
            if (q != null && !q.trim().isEmpty()) {
                String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("studentNo")), like, '\\'),
                        cb.like(cb.lower(root.get("firstName")), like, '\\'),
                        cb.like(cb.lower(root.get("lastName")), like, '\\')));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500),
                Sort.by("program", "studentNo"));
        Page<Student> students = studentRepository.findAll(spec, pr);

        Map<Long, List<StudentProgram>> byStudent = new HashMap<>();
        List<Long> ids = students.getContent().stream().map(Student::getId).collect(Collectors.toList());
        if (!ids.isEmpty()) {
            for (StudentProgram sp : studentProgramRepository.findByStudentIds(ids)) {
                byStudent.computeIfAbsent(sp.getStudent().getId(), k -> new ArrayList<>()).add(sp);
            }
            for (List<StudentProgram> l : byStudent.values()) l.sort(PROGRAM_ORDER);
        }
        return students.map(s -> StudentDto.from(s, byStudent.getOrDefault(s.getId(), Collections.emptyList())));
    }

    public Map<String, Object> filters() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("programs", studentProgramRepository.findDistinctPrograms());
        m.put("entryYears", studentRepository.findDistinctEntryYears());
        return m;
    }

    public List<CurriculumDto> curricula() {
        return curriculumRepository.findAllWithStudentCount().stream()
                .map(r -> new CurriculumDto((com.teduscheduler.model.Curriculum) r[0], ((Number) r[1]).longValue()))
                .collect(Collectors.toList());
    }
}
