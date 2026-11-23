package com.teduscheduler.dto;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {
    public static final int MAX_ERRORS = 50;

    public static class RowError {
        private final int row;
        private final String message;

        public RowError(int row, String message) {
            this.row = row;
            this.message = message;
        }

        public int getRow() { return row; }
        public String getMessage() { return message; }
    }

    private int totalRows;
    private int created;
    private int updated;
    private int skipped;
    private int curriculaCreated;
    private int withoutCurriculum;
    private int programsCreated;
    private int programsUpdated;
    private int programsRemoved;
    private int multiProgramStudents;
    private final List<RowError> errors = new ArrayList<>();

    public void addError(int row, String message) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(new RowError(row, message));
        }
    }

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public int getCreated() { return created; }
    public void setCreated(int created) { this.created = created; }
    public int getUpdated() { return updated; }
    public void setUpdated(int updated) { this.updated = updated; }
    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }
    public int getCurriculaCreated() { return curriculaCreated; }
    public void setCurriculaCreated(int curriculaCreated) { this.curriculaCreated = curriculaCreated; }
    public int getWithoutCurriculum() { return withoutCurriculum; }
    public void setWithoutCurriculum(int withoutCurriculum) { this.withoutCurriculum = withoutCurriculum; }
    public int getProgramsCreated() { return programsCreated; }
    public void setProgramsCreated(int programsCreated) { this.programsCreated = programsCreated; }
    public int getProgramsUpdated() { return programsUpdated; }
    public void setProgramsUpdated(int programsUpdated) { this.programsUpdated = programsUpdated; }
    public int getProgramsRemoved() { return programsRemoved; }
    public void setProgramsRemoved(int programsRemoved) { this.programsRemoved = programsRemoved; }
    public int getMultiProgramStudents() { return multiProgramStudents; }
    public void setMultiProgramStudents(int multiProgramStudents) { this.multiProgramStudents = multiProgramStudents; }
    public List<RowError> getErrors() { return errors; }
}
