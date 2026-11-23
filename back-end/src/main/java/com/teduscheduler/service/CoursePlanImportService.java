package com.teduscheduler.service;

import com.teduscheduler.dto.CoursePlanImportResult;
import com.teduscheduler.model.CoursePlan;
import com.teduscheduler.model.CoursePlanItem;
import com.teduscheduler.model.CoursePlanKind;
import com.teduscheduler.repository.CoursePlanItemRepository;
import com.teduscheduler.repository.CoursePlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CoursePlanImportService {

    /** Yüklenen dosya: ad + içerik. */
    public static class FileInput {
        private final String fileName;
        private final byte[] data;

        public FileInput(String fileName, byte[] data) {
            this.fileName = fileName;
            this.data = data;
        }

        public String getFileName() { return fileName; }
        public byte[] getData() { return data; }
    }

    @Autowired
    private CoursePlanRepository planRepository;

    @Autowired
    private CoursePlanItemRepository itemRepository;

    /**
     * Önce bütün dosyalar çözülür (biri bozuksa InvalidFileException, hiçbir şey yazılmaz);
     * sonra planlar anahtarıyla bulunup güncellenir ya da eklenir. Dosyada olmayan planlara dokunulmaz.
     */
    @Transactional
    public CoursePlanImportResult importFiles(List<FileInput> files, boolean dryRun) {
        List<CoursePlanParser.ParsedFile> parsed = new ArrayList<>();
        for (FileInput f : files) {
            parsed.add(CoursePlanParser.parse(new ByteArrayInputStream(f.getData()), f.getFileName()));
        }

        // dosyalar arası aynı plan: ilki kalır, sonrakiler atlanır
        Set<String> seen = new HashSet<>();
        for (CoursePlanParser.ParsedFile pf : parsed) {
            for (Iterator<CoursePlanParser.ParsedPlan> it = pf.plans.iterator(); it.hasNext(); ) {
                CoursePlanParser.ParsedPlan p = it.next();
                if (!seen.add(p.identityKey())) {
                    it.remove();
                    pf.warnings.add(new CoursePlanParser.ParseWarning(p.sheetName, CoursePlanParser.T_DUPLICATE_PLAN,
                            "\"" + p.programName + " / " + p.label + "\" planı başka bir dosyada da var; bu dosyadaki atlandı."));
                }
            }
        }

        Map<String, CoursePlan> existing = new HashMap<>();
        for (CoursePlan cp : planRepository.findAll()) {
            existing.putIfAbsent(keyOf(cp), cp);
        }

        CoursePlanImportResult result = new CoursePlanImportResult();
        result.setDryRun(dryRun);
        LocalDateTime now = LocalDateTime.now();
        int created = 0, updated = 0, items = 0;

        for (CoursePlanParser.ParsedFile pf : parsed) {
            CoursePlanImportResult.FileResult fr = new CoursePlanImportResult.FileResult();
            fr.setFileName(pf.fileName);
            fr.setFaculty(pf.faculty);
            fr.getSkippedSheets().addAll(pf.skippedSheets);
            for (CoursePlanParser.ParseWarning w : pf.warnings) {
                fr.getWarnings().add(new CoursePlanImportResult.Warning(w.getSheet(), w.getType(), w.getMessage()));
            }
            for (CoursePlanParser.ParsedPlan p : pf.plans) {
                CoursePlan plan = existing.get(p.identityKey());
                boolean isNew = plan == null;
                if (isNew) created++; else updated++;

                CoursePlanImportResult.PlanSummary s = new CoursePlanImportResult.PlanSummary();
                s.setProgramCode(p.programCode);
                s.setProgramName(p.programName);
                s.setKind(p.kind.name());
                s.setLabel(p.label);
                s.setSemesters(p.semesters);
                s.setCourses(p.courseCount());
                s.setElectives(p.electiveCount());
                s.setTotalCredits(p.totalCredits);
                s.setTotalEcts(p.totalEcts);
                s.setStatus(isNew ? "NEW" : "UPDATED");
                fr.getPlans().add(s);

                if (dryRun) continue;
                if (isNew) plan = new CoursePlan();
                plan.setFaculty(p.faculty);
                plan.setProgramCode(p.programCode);
                plan.setProgramName(p.programName);
                plan.setKind(p.kind);
                plan.setLabel(p.label);
                plan.setPlanYear(p.year);
                plan.setSourceFile(pf.fileName);
                plan.setImportedAt(now);
                plan.setTotalCredits(p.totalCredits);
                plan.setTotalEcts(p.totalEcts);
                plan = planRepository.save(plan);
                if (!isNew) {
                    itemRepository.deleteByPlanId(plan.getId());
                }
                List<CoursePlanItem> toSave = new ArrayList<>();
                for (CoursePlanParser.ParsedItem pi : p.items) {
                    CoursePlanItem item = new CoursePlanItem();
                    item.setCoursePlan(plan);
                    item.setSemester(pi.semester);
                    item.setOrderNo(pi.orderNo);
                    item.setItemType(pi.itemType);
                    item.setCode(pi.code);
                    item.setAlternativeCode(pi.alternativeCode);
                    item.setName(pi.name);
                    item.setTheory(pi.theory);
                    item.setPractice(pi.practice);
                    item.setLab(pi.lab);
                    item.setCredits(pi.credits);
                    item.setEcts(pi.ects);
                    toSave.add(item);
                }
                if (!toSave.isEmpty()) itemRepository.saveAll(toSave);
                items += toSave.size();
            }
            result.getFiles().add(fr);
        }
        result.setPlansCreated(created);
        result.setPlansUpdated(updated);
        result.setItemsSaved(items);
        return result;
    }

    private static String keyOf(CoursePlan cp) {
        String base = cp.getFaculty() + "\u0000" + cp.getProgramCode() + "\u0000" + cp.getKind();
        return cp.getKind() == CoursePlanKind.MINOR ? base : base + "\u0000" + cp.getLabel();
    }
}
