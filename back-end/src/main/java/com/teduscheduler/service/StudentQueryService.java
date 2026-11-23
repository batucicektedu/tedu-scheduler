package com.teduscheduler.service;

import com.teduscheduler.dto.CurriculumDto;
import com.teduscheduler.dto.StudentDto;
import com.teduscheduler.model.Student;
import com.teduscheduler.repository.CurriculumRepository;
import com.teduscheduler.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StudentQueryService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CurriculumRepository curriculumRepository;

    public Page<StudentDto> search(int page, int size, String program, Integer entryYear, Long curriculumId, String q) {
        Specification<Student> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (program != null && !program.trim().isEmpty()) {
                ps.add(cb.equal(root.get("program"), program.trim()));
            }
            if (entryYear != null) {
                ps.add(cb.equal(root.get("entryYear"), entryYear));
            }
            if (curriculumId != null) {
                ps.add(cb.equal(root.get("curriculum").get("id"), curriculumId));
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
        return studentRepository.findAll(spec, pr).map(StudentDto::from);
    }

    public Map<String, Object> filters() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("programs", studentRepository.findDistinctPrograms());
        m.put("entryYears", studentRepository.findDistinctEntryYears());
        return m;
    }

    public List<CurriculumDto> curricula() {
        return curriculumRepository.findAllWithStudentCount().stream()
                .map(r -> new CurriculumDto((com.teduscheduler.model.Curriculum) r[0], ((Number) r[1]).longValue()))
                .collect(Collectors.toList());
    }
}
