package com.teduscheduler.model;

import javax.persistence.*;
import java.math.BigDecimal;

/** Bir planın yarıyıl içindeki satırı (ders ya da seçmeli yuvası). */
@Entity
public class CoursePlanItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private CoursePlan coursePlan;

    private int semester;
    private int orderNo;

    @Enumerated(EnumType.STRING)
    private CoursePlanItemType itemType;

    private String code;
    private String alternativeCode;
    private String name;
    private Integer theory;
    private Integer practice;
    private Integer lab;

    @Column(precision = 10, scale = 2)
    private BigDecimal credits;

    @Column(precision = 10, scale = 2)
    private BigDecimal ects;

    public CoursePlanItem() { }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CoursePlan getCoursePlan() { return coursePlan; }
    public void setCoursePlan(CoursePlan coursePlan) { this.coursePlan = coursePlan; }
    public int getSemester() { return semester; }
    public void setSemester(int semester) { this.semester = semester; }
    public int getOrderNo() { return orderNo; }
    public void setOrderNo(int orderNo) { this.orderNo = orderNo; }
    public CoursePlanItemType getItemType() { return itemType; }
    public void setItemType(CoursePlanItemType itemType) { this.itemType = itemType; }
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
