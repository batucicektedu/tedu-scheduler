package com.teduscheduler.model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Müfredat Excel'inden içe aktarılan bir plan (öğrenci raporundaki Curriculum'dan ayrıdır). */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"faculty", "programCode", "kind", "label"}))
public class CoursePlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String faculty;
    private String programCode;
    private String programName;

    @Enumerated(EnumType.STRING)
    private CoursePlanKind kind;

    private String label;
    private Integer planYear;
    private String sourceFile;
    private LocalDateTime importedAt;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalCredits;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalEcts;

    public CoursePlan() { }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFaculty() { return faculty; }
    public void setFaculty(String faculty) { this.faculty = faculty; }
    public String getProgramCode() { return programCode; }
    public void setProgramCode(String programCode) { this.programCode = programCode; }
    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }
    public CoursePlanKind getKind() { return kind; }
    public void setKind(CoursePlanKind kind) { this.kind = kind; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Integer getPlanYear() { return planYear; }
    public void setPlanYear(Integer planYear) { this.planYear = planYear; }
    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
    public LocalDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
    public BigDecimal getTotalCredits() { return totalCredits; }
    public void setTotalCredits(BigDecimal totalCredits) { this.totalCredits = totalCredits; }
    public BigDecimal getTotalEcts() { return totalEcts; }
    public void setTotalEcts(BigDecimal totalEcts) { this.totalEcts = totalEcts; }
}
