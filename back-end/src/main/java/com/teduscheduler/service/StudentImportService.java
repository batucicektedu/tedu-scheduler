package com.teduscheduler.service;

import com.teduscheduler.dto.ImportResult;
import com.teduscheduler.model.Curriculum;
import com.teduscheduler.model.Student;
import com.teduscheduler.repository.CurriculumRepository;
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
        Map<String, Curriculum> curricula = new HashMap<>();
        for (Curriculum c : curriculumRepository.findAll()) {
            curricula.put(curriculumKey(c.getProgram(), c.getCatalogCode(), c.getVersion()), c);
        }

        LocalDateTime now = LocalDateTime.now();
        Set<Student> toSave = new LinkedHashSet<>();
        int created = 0, updated = 0, skipped = 0, curriculaCreated = 0, total = 0, withoutCurriculum = 0;

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

                Student s = students.get(no);
                boolean isNew = s == null;
                if (isNew) {
                    s = new Student();
                    s.setStudentNo(no);
                    students.put(no, s);
                }
                s.setFirstName(text(row, cols, H_FIRST, fmt));
                s.setLastName(text(row, cols, H_LAST, fmt));
                s.setEntryYear(year);
                s.setClassLevel(text(row, cols, H_CLASS, fmt));
                s.setAcademicStatus(text(row, cols, H_STATUS, fmt));
                s.setFaculty(text(row, cols, H_FACULTY, fmt));
                s.setProgram(program);
                s.setCurriculum(curriculum);
                String extra = text(row, cols, H_ADDITIONAL, fmt);
                s.setAdditionalProgram(extra.isEmpty() ? null : extra);
                s.setRegistrationDate(date(row, cols, H_REG_DATE, fmt));
                s.setImportedAt(now);
                // A student repeated within the file counts once, as the first occurrence decided.
                if (toSave.add(s)) {
                    if (curriculum == null) withoutCurriculum++;
                    if (isNew) created++; else updated++;
                }
            } catch (Exception e) {
                result.addError(rowNumber, e.getMessage());
                skipped++;
            }
        }

        studentRepository.saveAll(toSave);

        result.setTotalRows(total);
        result.setCreated(created);
        result.setUpdated(updated);
        result.setSkipped(skipped);
        result.setCurriculaCreated(curriculaCreated);
        result.setWithoutCurriculum(withoutCurriculum);
        return result;
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
