package com.teduscheduler.dto;

import com.teduscheduler.model.Curriculum;

public class CurriculumDto {
    private Long id;
    private String faculty;
    private String program;
    private String catalogCode;
    private String catalogName;
    private String version;
    private long studentCount;

    public CurriculumDto(Curriculum c, long studentCount) {
        this.id = c.getId();
        this.faculty = c.getFaculty();
        this.program = c.getProgram();
        this.catalogCode = c.getCatalogCode();
        this.catalogName = c.getCatalogName();
        this.version = c.getVersion();
        this.studentCount = studentCount;
    }

    public Long getId() { return id; }
    public String getFaculty() { return faculty; }
    public String getProgram() { return program; }
    public String getCatalogCode() { return catalogCode; }
    public String getCatalogName() { return catalogName; }
    public String getVersion() { return version; }
    public long getStudentCount() { return studentCount; }
}
