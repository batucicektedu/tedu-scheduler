package com.teduscheduler.model;

import javax.persistence.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"program", "catalogCode", "version"}))
public class Curriculum {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String faculty;
    private String program;
    private String catalogCode;
    private String catalogName;
    private String version;

    public Curriculum() { }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFaculty() { return faculty; }
    public void setFaculty(String faculty) { this.faculty = faculty; }
    public String getProgram() { return program; }
    public void setProgram(String program) { this.program = program; }
    public String getCatalogCode() { return catalogCode; }
    public void setCatalogCode(String catalogCode) { this.catalogCode = catalogCode; }
    public String getCatalogName() { return catalogName; }
    public void setCatalogName(String catalogName) { this.catalogName = catalogName; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
