package com.teduscheduler.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Müfredat listesi satırı. */
public class CoursePlanDto {
    private Long id;
    private String faculty;
    private String programCode;
    private String programName;
    private String kind;
    private String label;
    private Integer year;
    private long courseCount;
    private long electiveCount;
    private BigDecimal totalCredits;
    private BigDecimal totalEcts;
    private String sourceFile;
    private LocalDateTime importedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFaculty() { return faculty; }
    public void setFaculty(String faculty) { this.faculty = faculty; }
    public String getProgramCode() { return programCode; }
    public void setProgramCode(String programCode) { this.programCode = programCode; }
    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public long getCourseCount() { return courseCount; }
    public void setCourseCount(long courseCount) { this.courseCount = courseCount; }
    public long getElectiveCount() { return electiveCount; }
    public void setElectiveCount(long electiveCount) { this.electiveCount = electiveCount; }
    public BigDecimal getTotalCredits() { return totalCredits; }
    public void setTotalCredits(BigDecimal totalCredits) { this.totalCredits = totalCredits; }
    public BigDecimal getTotalEcts() { return totalEcts; }
    public void setTotalEcts(BigDecimal totalEcts) { this.totalEcts = totalEcts; }
    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
    public LocalDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
}
