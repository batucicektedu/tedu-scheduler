package com.teduscheduler.model;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One programme record of a student (main programme, double major, minor ...). */
@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "program"}),
        indexes = {
                @Index(columnList = "student_id"),
                @Index(columnList = "curriculum_id")
        })
public class StudentProgram {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id")
    private Curriculum curriculum;

    @Column(nullable = false)
    private String program;
    private String faculty;
    private Integer entryYear;
    private String classLevel;
    private String academicStatus;
    private String additionalProgram;
    private LocalDate registrationDate;
    private LocalDateTime importedAt;

    @Enumerated(EnumType.STRING)
    private ProgramType programType;

    /** True for the student's primary programme (exactly one per student). */
    private boolean primaryProgram;

    public StudentProgram() { }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public Curriculum getCurriculum() { return curriculum; }
    public void setCurriculum(Curriculum curriculum) { this.curriculum = curriculum; }
    public String getProgram() { return program; }
    public void setProgram(String program) { this.program = program; }
    public String getFaculty() { return faculty; }
    public void setFaculty(String faculty) { this.faculty = faculty; }
    public Integer getEntryYear() { return entryYear; }
    public void setEntryYear(Integer entryYear) { this.entryYear = entryYear; }
    public String getClassLevel() { return classLevel; }
    public void setClassLevel(String classLevel) { this.classLevel = classLevel; }
    public String getAcademicStatus() { return academicStatus; }
    public void setAcademicStatus(String academicStatus) { this.academicStatus = academicStatus; }
    public String getAdditionalProgram() { return additionalProgram; }
    public void setAdditionalProgram(String additionalProgram) { this.additionalProgram = additionalProgram; }
    public LocalDate getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(LocalDate registrationDate) { this.registrationDate = registrationDate; }
    public LocalDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
    public ProgramType getProgramType() { return programType; }
    public void setProgramType(ProgramType programType) { this.programType = programType; }
    public boolean isPrimaryProgram() { return primaryProgram; }
    public void setPrimaryProgram(boolean primaryProgram) { this.primaryProgram = primaryProgram; }
}
