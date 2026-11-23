package com.teduscheduler.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Müfredat içe aktarma yanıtı (dryRun ve gerçek kayıt aynı biçimde). */
public class CoursePlanImportResult {

    public static class PlanSummary {
        private String programCode;
        private String programName;
        private String kind;
        private String label;
        private int semesters;
        private int courses;
        private int electives;
        private BigDecimal totalCredits;
        private BigDecimal totalEcts;
        private String status;

        public String getProgramCode() { return programCode; }
        public void setProgramCode(String programCode) { this.programCode = programCode; }
        public String getProgramName() { return programName; }
        public void setProgramName(String programName) { this.programName = programName; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public int getSemesters() { return semesters; }
        public void setSemesters(int semesters) { this.semesters = semesters; }
        public int getCourses() { return courses; }
        public void setCourses(int courses) { this.courses = courses; }
        public int getElectives() { return electives; }
        public void setElectives(int electives) { this.electives = electives; }
        public BigDecimal getTotalCredits() { return totalCredits; }
        public void setTotalCredits(BigDecimal totalCredits) { this.totalCredits = totalCredits; }
        public BigDecimal getTotalEcts() { return totalEcts; }
        public void setTotalEcts(BigDecimal totalEcts) { this.totalEcts = totalEcts; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class Warning {
        private final String sheet;
        private final String type;
        private final String message;

        public Warning(String sheet, String type, String message) {
            this.sheet = sheet;
            this.type = type;
            this.message = message;
        }

        public String getSheet() { return sheet; }
        public String getType() { return type; }
        public String getMessage() { return message; }
    }

    public static class FileResult {
        private String fileName;
        private String faculty;
        private final List<PlanSummary> plans = new ArrayList<>();
        private final List<String> skippedSheets = new ArrayList<>();
        private final List<Warning> warnings = new ArrayList<>();

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getFaculty() { return faculty; }
        public void setFaculty(String faculty) { this.faculty = faculty; }
        public List<PlanSummary> getPlans() { return plans; }
        public List<String> getSkippedSheets() { return skippedSheets; }
        public List<Warning> getWarnings() { return warnings; }
    }

    private boolean dryRun;
    private final List<FileResult> files = new ArrayList<>();
    private int plansCreated;
    private int plansUpdated;
    private int itemsSaved;

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    public List<FileResult> getFiles() { return files; }
    public int getPlansCreated() { return plansCreated; }
    public void setPlansCreated(int plansCreated) { this.plansCreated = plansCreated; }
    public int getPlansUpdated() { return plansUpdated; }
    public void setPlansUpdated(int plansUpdated) { this.plansUpdated = plansUpdated; }
    public int getItemsSaved() { return itemsSaved; }
    public void setItemsSaved(int itemsSaved) { this.itemsSaved = itemsSaved; }
}
