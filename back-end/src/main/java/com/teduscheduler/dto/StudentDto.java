package com.teduscheduler.dto;

import com.teduscheduler.model.Curriculum;
import com.teduscheduler.model.Student;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
}
