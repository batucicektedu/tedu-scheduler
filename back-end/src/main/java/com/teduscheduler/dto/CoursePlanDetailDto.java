package com.teduscheduler.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Tek müfredat: plan bilgisi + yarıyıl yarıyıl kalemler. */
public class CoursePlanDetailDto {

    public static class Item {
        private String itemType;
        private String code;
        private String alternativeCode;
        private String name;
        private Integer theory;
        private Integer practice;
        private Integer lab;
        private BigDecimal credits;
        private BigDecimal ects;

        public String getItemType() { return itemType; }
        public void setItemType(String itemType) { this.itemType = itemType; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getAlternativeCode() { return alternativeCode; }
        public void setAlternativeCode(String alternativeCode) { this.alternativeCode = alternativeCode; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getTheory() { return theory; }
        public void setTheory(Integer theory) { this.theory = theory; }
        public Integer getPractice() { return practice; }
        public void setPractice(Integer practice) { this.practice = practice; }
        public Integer getLab() { return lab; }
        public void setLab(Integer lab) { this.lab = lab; }
        public BigDecimal getCredits() { return credits; }
        public void setCredits(BigDecimal credits) { this.credits = credits; }
        public BigDecimal getEcts() { return ects; }
        public void setEcts(BigDecimal ects) { this.ects = ects; }
    }

    public static class Semester {
        private int semester;
        private BigDecimal totalCredits = BigDecimal.ZERO;
        private BigDecimal totalEcts = BigDecimal.ZERO;
        private final List<Item> items = new ArrayList<>();

        public int getSemester() { return semester; }
        public void setSemester(int semester) { this.semester = semester; }
        public BigDecimal getTotalCredits() { return totalCredits; }
        public void setTotalCredits(BigDecimal totalCredits) { this.totalCredits = totalCredits; }
        public BigDecimal getTotalEcts() { return totalEcts; }
        public void setTotalEcts(BigDecimal totalEcts) { this.totalEcts = totalEcts; }
        public List<Item> getItems() { return items; }
    }

    private CoursePlanDto plan;
    private final List<Semester> semesters = new ArrayList<>();

    public CoursePlanDto getPlan() { return plan; }
    public void setPlan(CoursePlanDto plan) { this.plan = plan; }
    public List<Semester> getSemesters() { return semesters; }
}
