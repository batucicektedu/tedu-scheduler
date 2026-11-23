package com.teduscheduler.service;

import com.teduscheduler.dto.CoursePlanDetailDto;
import com.teduscheduler.dto.CoursePlanDto;
import com.teduscheduler.model.CoursePlan;
import com.teduscheduler.model.CoursePlanItem;
import com.teduscheduler.model.CoursePlanItemType;
import com.teduscheduler.repository.CoursePlanItemRepository;
import com.teduscheduler.repository.CoursePlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CoursePlanQueryService {

    @Autowired
    private CoursePlanRepository planRepository;

    @Autowired
    private CoursePlanItemRepository itemRepository;

    @Transactional(readOnly = true)
    public List<CoursePlanDto> list() {
        Map<Long, long[]> counts = new HashMap<>(); // [ders, seçmeli]
        for (Object[] row : itemRepository.countByPlanAndType()) {
            Long planId = (Long) row[0];
            long[] c = counts.get(planId);
            if (c == null) {
                c = new long[2];
                counts.put(planId, c);
            }
            if (row[1] == CoursePlanItemType.COURSE) c[0] += (Long) row[2];
            else c[1] += (Long) row[2];
        }
        List<CoursePlanDto> result = new ArrayList<>();
        for (CoursePlan p : planRepository.findAll(Sort.by("faculty", "programName", "kind", "label"))) {
            long[] c = counts.containsKey(p.getId()) ? counts.get(p.getId()) : new long[2];
            result.add(toDto(p, c[0], c[1]));
        }
        return result;
    }

    /** Plan yoksa null. */
    @Transactional(readOnly = true)
    public CoursePlanDetailDto detail(Long id) {
        CoursePlan p = planRepository.findById(id).orElse(null);
        if (p == null) return null;
        CoursePlanDetailDto dto = new CoursePlanDetailDto();
        Map<Integer, CoursePlanDetailDto.Semester> bySem = new TreeMap<>();
        long courses = 0, electives = 0;
        for (CoursePlanItem i : itemRepository.findByCoursePlanIdOrderBySemesterAscOrderNoAsc(id)) {
            CoursePlanDetailDto.Semester s = bySem.get(i.getSemester());
            if (s == null) {
                s = new CoursePlanDetailDto.Semester();
                s.setSemester(i.getSemester());
                bySem.put(i.getSemester(), s);
            }
            CoursePlanDetailDto.Item it = new CoursePlanDetailDto.Item();
            it.setItemType(i.getItemType().name());
            it.setCode(i.getCode());
            it.setAlternativeCode(i.getAlternativeCode());
            it.setName(i.getName());
            it.setTheory(i.getTheory());
            it.setPractice(i.getPractice());
            it.setLab(i.getLab());
            it.setCredits(i.getCredits());
            it.setEcts(i.getEcts());
            s.getItems().add(it);
            if (i.getCredits() != null) s.setTotalCredits(s.getTotalCredits().add(i.getCredits()));
            if (i.getEcts() != null) s.setTotalEcts(s.getTotalEcts().add(i.getEcts()));
            if (i.getItemType() == CoursePlanItemType.COURSE) courses++; else electives++;
        }
        dto.setPlan(toDto(p, courses, electives));
        dto.getSemesters().addAll(bySem.values());
        return dto;
    }

    private static CoursePlanDto toDto(CoursePlan p, long courses, long electives) {
        CoursePlanDto d = new CoursePlanDto();
        d.setId(p.getId());
        d.setFaculty(p.getFaculty());
        d.setProgramCode(p.getProgramCode());
        d.setProgramName(p.getProgramName());
        d.setKind(p.getKind() == null ? null : p.getKind().name());
        d.setLabel(p.getLabel());
        d.setYear(p.getPlanYear());
        d.setCourseCount(courses);
        d.setElectiveCount(electives);
        d.setTotalCredits(p.getTotalCredits());
        d.setTotalEcts(p.getTotalEcts());
        d.setSourceFile(p.getSourceFile());
        d.setImportedAt(p.getImportedAt());
        return d;
    }
}
