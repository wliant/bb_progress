package com.bb.progress.milestone;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "milestone_definition")
public class MilestoneDefinition {

    @Id
    private String id;

    @Column(name = "age_months", nullable = false)
    private int ageMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MilestoneCategory category;

    @Column(name = "title_en", nullable = false)
    private String titleEn;

    @Column(name = "title_zh", nullable = false)
    private String titleZh;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected MilestoneDefinition() {
    }

    public String getId() {
        return id;
    }

    public int getAgeMonths() {
        return ageMonths;
    }

    public MilestoneCategory getCategory() {
        return category;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public String getTitleZh() {
        return titleZh;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
