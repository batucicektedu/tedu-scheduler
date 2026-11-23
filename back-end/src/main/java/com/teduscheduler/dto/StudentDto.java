package com.teduscheduler.dto;

import com.teduscheduler.model.Curriculum;
import com.teduscheduler.model.ProgramType;
import com.teduscheduler.model.Student;
import com.teduscheduler.model.StudentProgram;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class StudentDto {
    public static class CurriculumRef {
        private Long id;
        private String catalogCode;
        private String catalogName;
        private String version;

        public CurriculumRef(Curriculum c) {
            this.id = c.getId();
            this.catalogCode = c.getCatalogCode();
            this.catalogName = c.getCatalogName();
            this.version = c.getVersion();
        }

        public Long getId() { return id; }
        public String getCatalogCode() { return catalogCode; }
        public String getCatalogName() { return catalogName; }
        public String getVersion() { return version; }
    }

    public static class ProgramDto {
        private Long id;
        private String program;
        private String faculty;
        private ProgramType programType;
        private boolean primary;
        private Integer entryYear;
        private String classLevel;
        private String academicStatus;
        private String additionalProgram;
        private LocalDate registrationDate;
        private CurriculumRef curriculum;

        public static ProgramDto from(StudentProgram p) {
            ProgramDto d = new ProgramDto();
            d.id = p.getId();
            d.program = p.getProgram();
            d.faculty = p.getFaculty();
            d.programType = p.getProgramType();
            d.primary = p.isPrimaryProgram();
            d.entryYear = p.getEntryYear();
            d.classLevel = p.getClassLevel();
            d.academicStatus = p.getAcademicStatus();
            d.additionalProgram = p.getAdditionalProgram();
            d.registrationDate = p.getRegistrationDate();
            d.curriculum = p.getCurriculum() == null ? null : new CurriculumRef(p.getCurriculum());
            return d;
        }

        public Long getId() { return id; }
        public String getProgram() { return program; }
        public String getFaculty() { return faculty; }
        public ProgramType getProgramType() { return programType; }
        public boolean isPrimary() { return primary; }
        public Integer getEntryYear() { return entryYear; }
        public String getClassLevel() { return classLevel; }
        public String getAcademicStatus() { return academicStatus; }
        public String getAdditionalProgram() { return additionalProgram; }
        public LocalDate getRegistrationDate() { return registrationDate; }
        public CurriculumRef getCurriculum() { return curriculum; }
    }

    private Long id;
    private String studentNo;
    private String firstName;
    private String lastName;
    private Integer entryYear;
    private String classLevel;
    private String academicStatus;
    private String faculty;
    private String program;
    private String additionalProgram;
    private LocalDate registrationDate;
    private LocalDateTime importedAt;
    private CurriculumRef curriculum;
    private List<ProgramDto> programs = new ArrayList<>();

    public static StudentDto from(Student s) {
        StudentDto d = new StudentDto();
        d.id = s.getId();
        d.studentNo = s.getStudentNo();
        d.firstName = s.getFirstName();
        d.lastName = s.getLastName();
        d.entryYear = s.getEntryYear();
        d.classLevel = s.getClassLevel();
        d.academicStatus = s.getAcademicStatus();
        d.faculty = s.getFaculty();
        d.program = s.getProgram();
        d.additionalProgram = s.getAdditionalProgram();
        d.registrationDate = s.getRegistrationDate();
        d.importedAt = s.getImportedAt();
        d.curriculum = s.getCurriculum() == null ? null : new CurriculumRef(s.getCurriculum());
        return d;
    }

    /** {@code programs} must already be in display order (primary first). */
    public static StudentDto from(Student s, List<StudentProgram> programs) {
        StudentDto d = from(s);
        if (programs != null) {
            for (StudentProgram p : programs) d.programs.add(ProgramDto.from(p));
        }
        return d;
    }

    public Long getId() { return id; }
    public String getStudentNo() { return studentNo; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Integer getEntryYear() { return entryYear; }
    public String getClassLevel() { return classLevel; }
    public String getAcademicStatus() { return academicStatus; }
    public String getFaculty() { return faculty; }
    public String getProgram() { return program; }
    public String getAdditionalProgram() { return additionalProgram; }
    public LocalDate getRegistrationDate() { return registrationDate; }
    public LocalDateTime getImportedAt() { return importedAt; }
    public CurriculumRef getCurriculum() { return curriculum; }
    public List<ProgramDto> getPrograms() { return programs; }
}
