package com.teduscheduler.service;

import com.teduscheduler.dto.ImportResult;
import com.teduscheduler.model.Curriculum;
import com.teduscheduler.model.ProgramType;
import com.teduscheduler.model.Student;
import com.teduscheduler.model.StudentProgram;
import com.teduscheduler.repository.CurriculumRepository;
import com.teduscheduler.repository.StudentProgramRepository;
import com.teduscheduler.repository.StudentRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class StudentImportService {

    private static final String H_NO = "Öğrenci No";
    private static final String H_FIRST = "Ad";
    private static final String H_LAST = "Soyad";
    private static final String H_YEAR = "Giriş Yılı";
    private static final String H_CLASS = "Sınıf";
    private static final String H_STATUS = "Akademik Durum";
    private static final String H_FACULTY = "Fakülte";
    private static final String H_PROGRAM = "Program";
    private static final String H_CATALOG = "Katalog";
    private static final String H_CATALOG_NAME = "Katalog (Tanım)";
    private static final String H_VERSION = "Versiyon";
    private static final String H_ADDITIONAL = "Ek Dal";
    private static final String H_REG_DATE = "Kayıt Tarihi";

    private static final Locale TR = new Locale("tr", "TR");

    private static final List<String> REQUIRED = Arrays.asList(H_NO, H_PROGRAM, H_YEAR, H_CATALOG, H_VERSION);

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    };

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CurriculumRepository curriculumRepository;

    @Autowired
    private StudentProgramRepository studentProgramRepository;

    /** Thrown for problems with the file itself (mapped to HTTP 400). */
    public static class InvalidImportFileException extends RuntimeException {
        public InvalidImportFileException(String message) {
            super(message);
        }
    }

    @Transactional
    public ImportResult importStudents(InputStream in) throws IOException {
        Workbook workbook;
        try {
            workbook = WorkbookFactory.create(in);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidImportFileException("Could not read Excel file.");
        }
        try {
            return process(workbook);
        } finally {
            workbook.close();
        }
    }

    private ImportResult process(Workbook workbook) {
        ImportResult result = new ImportResult();
        Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
        if (sheet == null || sheet.getRow(sheet.getFirstRowNum()) == null) {
            throw new InvalidImportFileException("Sheet is empty.");
        }
        DataFormatter fmt = new DataFormatter(new Locale("tr", "TR"));
        Row header = sheet.getRow(sheet.getFirstRowNum());

        Map<String, Integer> cols = new HashMap<>();
        for (Cell c : header) {
            String name = fmt.formatCellValue(c).trim();
            if (!name.isEmpty()) {
                cols.putIfAbsent(name, c.getColumnIndex());
            }
        }
        List<String> missing = new ArrayList<>();
        for (String h : REQUIRED) {
            if (!cols.containsKey(h)) missing.add(h);
        }
        if (!missing.isEmpty()) {
            throw new InvalidImportFileException("Missing required columns: " + String.join(", ", missing));
        }

        Map<String, Student> students = new HashMap<>();
        for (Student s : studentRepository.findAll()) {
            students.put(s.getStudentNo(), s);
        }
        // existing programme records: student no -> (program -> record)
        Map<String, Map<String, StudentProgram>> existingPrograms = new HashMap<>();
        for (StudentProgram sp : studentProgramRepository.findAllWithStudent()) {
            existingPrograms.computeIfAbsent(sp.getStudent().getStudentNo(), k -> new LinkedHashMap<>())
                    .put(sp.getProgram(), sp);
        }
        Map<String, Curriculum> curricula = new HashMap<>();
        for (Curriculum c : curriculumRepository.findAll()) {
            curricula.put(curriculumKey(c.getProgram(), c.getCatalogCode(), c.getVersion()), c);
        }

        LocalDateTime now = LocalDateTime.now();
        int skipped = 0, curriculaCreated = 0, total = 0;

        // Pass 1: parse every row into per-student groups (file order preserved).
        Map<String, List<ProgramRow>> groups = new LinkedHashMap<>();
        Set<String> studentsWithErrors = new HashSet<>();
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String no = text(row, cols, H_NO, fmt);
            if (no.isEmpty()) {
                skipped++;
                continue;
            }
            total++;
            int rowNumber = r + 1;
            try {
                String program = text(row, cols, H_PROGRAM, fmt);
                String code = text(row, cols, H_CATALOG, fmt);
                String version = text(row, cols, H_VERSION, fmt);
                String yearStr = text(row, cols, H_YEAR, fmt);
                if (program.isEmpty()) throw new IllegalArgumentException("Program is empty");
                List<ProgramRow> group = groups.get(no);
                if (group != null) {
                    for (ProgramRow other : group) {
                        if (other.program.equals(program)) {
                            throw new IllegalArgumentException("Aynı öğrenci ve program dosyada tekrar ediyor");
                        }
                    }
                }
                Integer year = null;
                try {
                    if (!yearStr.isEmpty()) year = Integer.valueOf(yearStr);
                } catch (NumberFormatException e) {
                    year = null;
                }

                Curriculum curriculum = null;
                if (!code.isEmpty() && !version.isEmpty()) {
                    String key = curriculumKey(program, code, version);
                    curriculum = curricula.get(key);
                    if (curriculum == null) {
                        curriculum = new Curriculum();
                        curriculum.setFaculty(text(row, cols, H_FACULTY, fmt));
                        curriculum.setProgram(program);
                        curriculum.setCatalogCode(code);
                        curriculum.setCatalogName(text(row, cols, H_CATALOG_NAME, fmt));
                        curriculum.setVersion(version);
                        curriculum = curriculumRepository.save(curriculum);
                        curricula.put(key, curriculum);
                        curriculaCreated++;
                    }
                }

                ProgramRow pr = new ProgramRow();
                pr.firstName = text(row, cols, H_FIRST, fmt);
                pr.lastName = text(row, cols, H_LAST, fmt);
                pr.program = program;
                pr.code = code;
                pr.entryYear = year;
                pr.classLevel = text(row, cols, H_CLASS, fmt);
                pr.academicStatus = text(row, cols, H_STATUS, fmt);
                pr.faculty = text(row, cols, H_FACULTY, fmt);
                pr.curriculum = curriculum;
                String extra = text(row, cols, H_ADDITIONAL, fmt);
                pr.additionalProgram = extra.isEmpty() ? null : extra;
                pr.registrationDate = date(row, cols, H_REG_DATE, fmt);
                if (group == null) {
                    group = new ArrayList<>();
                    groups.put(no, group);
                }
                group.add(pr);
            } catch (Exception e) {
                result.addError(rowNumber, e.getMessage());
                studentsWithErrors.add(no);
                skipped++;
            }
        }

        // Pass 2: per student decide primary/type, upsert student + programme records.
        List<Student> studentsToSave = new ArrayList<>();
        List<StudentProgram> programsToSave = new ArrayList<>();
        List<StudentProgram> programsToDelete = new ArrayList<>();
        int created = 0, updated = 0, withoutCurriculum = 0;
        int programsCreated = 0, programsUpdated = 0, multiProgramStudents = 0;

        for (Map.Entry<String, List<ProgramRow>> entry : groups.entrySet()) {
            String no = entry.getKey();
            List<ProgramRow> rows = entry.getValue();
            Map<String, StudentProgram> existing = existingPrograms.containsKey(no)
                    ? existingPrograms.get(no) : Collections.<String, StudentProgram>emptyMap();

            Set<String> inFile = new HashSet<>();
            List<Candidate> candidates = new ArrayList<>();
            for (ProgramRow pr : rows) {
                inFile.add(pr.program);
                candidates.add(new Candidate(pr, null));
                if (pr.curriculum == null) withoutCurriculum++;
            }
            boolean keepAll = studentsWithErrors.contains(no);
            for (StudentProgram sp : existing.values()) {
                if (inFile.contains(sp.getProgram())) continue;
                if (keepAll) {
                    candidates.add(new Candidate(null, sp));
                } else {
                    programsToDelete.add(sp);
                }
            }
            if (rows.size() > 1) multiProgramStudents++;

            Candidate primary = null;
            for (Candidate c : candidates) {
                if (primary == null || betterPrimary(c, primary)) primary = c;
            }
            for (Candidate c : candidates) {
                c.primary = c == primary;
                if (c.minor) {
                    c.type = ProgramType.MINOR;
                } else if (c.primary) {
                    c.type = ProgramType.MAIN;
                } else if ("0020".equals(c.code) && "0020".equals(primary.code)) {
                    c.type = ProgramType.DOUBLE_MAJOR;
                } else {
                    c.type = ProgramType.OTHER;
                }
            }

            // Student mirrors the primary programme; names come from the primary row (or the first valid row).
            Student s = students.get(no);
            if (s == null) {
                s = new Student();
                s.setStudentNo(no);
                students.put(no, s);
                created++;
            } else {
                updated++;
            }
            ProgramRow nameRow = primary.row != null ? primary.row : rows.get(0);
            s.setFirstName(nameRow.firstName);
            s.setLastName(nameRow.lastName);
            if (primary.row != null) {
                ProgramRow p = primary.row;
                s.setEntryYear(p.entryYear);
                s.setClassLevel(p.classLevel);
                s.setAcademicStatus(p.academicStatus);
                s.setFaculty(p.faculty);
                s.setProgram(p.program);
                s.setCurriculum(p.curriculum);
                s.setAdditionalProgram(p.additionalProgram);
                s.setRegistrationDate(p.registrationDate);
            } else {
                StudentProgram p = primary.existing;
                s.setEntryYear(p.getEntryYear());
                s.setClassLevel(p.getClassLevel());
                s.setAcademicStatus(p.getAcademicStatus());
                s.setFaculty(p.getFaculty());
                s.setProgram(p.getProgram());
                s.setCurriculum(p.getCurriculum());
                s.setAdditionalProgram(p.getAdditionalProgram());
                s.setRegistrationDate(p.getRegistrationDate());
            }
            s.setImportedAt(now);
            studentsToSave.add(s);

            for (Candidate c : candidates) {
                StudentProgram sp;
                if (c.row != null) {
                    ProgramRow p = c.row;
                    sp = existing.get(p.program);
                    if (sp == null) {
                        sp = new StudentProgram();
                        sp.setStudent(s);
                        sp.setProgram(p.program);
                        programsCreated++;
                    } else {
                        programsUpdated++;
                    }
                    sp.setFaculty(p.faculty);
                    sp.setEntryYear(p.entryYear);
                    sp.setClassLevel(p.classLevel);
                    sp.setAcademicStatus(p.academicStatus);
                    sp.setAdditionalProgram(p.additionalProgram);
                    sp.setRegistrationDate(p.registrationDate);
                    sp.setCurriculum(p.curriculum);
                    sp.setImportedAt(now);
                } else {
                    sp = c.existing;
                }
                sp.setProgramType(c.type);
                sp.setPrimaryProgram(c.primary);
                programsToSave.add(sp);
            }
        }

        // students first (programmes reference them), then removals and programme records
        studentRepository.saveAll(studentsToSave);
        if (!programsToDelete.isEmpty()) {
            studentProgramRepository.deleteAll(programsToDelete);
        }
        studentProgramRepository.saveAll(programsToSave);

        result.setTotalRows(total);
        result.setCreated(created);
        result.setUpdated(updated);
        result.setSkipped(skipped);
        result.setCurriculaCreated(curriculaCreated);
        result.setWithoutCurriculum(withoutCurriculum);
        result.setProgramsCreated(programsCreated);
        result.setProgramsUpdated(programsUpdated);
        result.setProgramsRemoved(programsToDelete.size());
        result.setMultiProgramStudents(multiProgramStudents);
        return result;
    }

    /** One valid Excel row, before it is matched to a StudentProgram. */
    private static class ProgramRow {
        String firstName;
        String lastName;
        String program;
        String code;
        Integer entryYear;
        String classLevel;
        String academicStatus;
        String faculty;
        Curriculum curriculum;
        String additionalProgram;
        LocalDate registrationDate;
    }

    /** A programme of one student taking part in the primary/type decision: a file row or a retained record. */
    private static class Candidate {
        final ProgramRow row;
        final StudentProgram existing;
        final String code;
        final LocalDate date;
        final boolean minor;
        boolean primary;
        ProgramType type;

        Candidate(ProgramRow row, StudentProgram existing) {
            this.row = row;
            this.existing = existing;
            String name;
            if (row != null) {
                name = row.program;
                code = row.code;
                date = row.registrationDate;
            } else {
                name = existing.getProgram();
                code = existing.getCurriculum() == null ? "" : existing.getCurriculum().getCatalogCode();
                date = existing.getRegistrationDate();
            }
            this.minor = isMinor(code, name);
        }
    }

    private static boolean isMinor(String code, String programName) {
        if ("0050".equals(code)) return true;
        String lower = programName == null ? "" : programName.toLowerCase(TR);
        return lower.contains("yandal") || lower.contains("yan dal");
    }

    /** Non-minors beat minors; then earlier registration date (null last); ties keep the earlier candidate. */
    private static boolean betterPrimary(Candidate c, Candidate best) {
        if (c.minor != best.minor) return !c.minor;
        if (c.date == null) return false;
        return best.date == null || c.date.isBefore(best.date);
    }

    private static String curriculumKey(String program, String code, String version) {
        return program + "\u0000" + code + "\u0000" + version;
    }

    private static String text(Row row, Map<String, Integer> cols, String header, DataFormatter fmt) {
        Integer idx = cols.get(header);
        if (idx == null) return "";
        Cell cell = row.getCell(idx);
        if (cell == null) return "";
        String v = fmt.formatCellValue(cell);
        return v == null ? "" : v.trim();
    }

    private static LocalDate date(Row row, Map<String, Integer> cols, String header, DataFormatter fmt) {
        Integer idx = cols.get(header);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        try {
            if (cell.getCellTypeEnum() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        } catch (Exception ignored) {
            return null;
        }
        String v = fmt.formatCellValue(cell).trim();
        if (v.isEmpty()) return null;
        if (v.length() > 10 && v.charAt(10) == ' ') v = v.substring(0, 10);
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(v, f);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }
}
